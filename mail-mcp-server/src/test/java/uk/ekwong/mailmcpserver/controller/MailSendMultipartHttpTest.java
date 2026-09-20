/*
 * Copyright 2026 ekwongchum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ekwong.mailmcpserver.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.server.SMTPServer;
import uk.ekwong.mailmcpserver.send.MailSendResponse;
import uk.ekwong.mailmcpserver.send.MailSendTask;

/**
 * End-to-end test over a real servlet container: the multipart request is parsed by the servlet
 * multipart support (not by MockMvc), the uploaded file is delivered through a real SMTP
 * conversation, an oversized attachment is rejected with the application error response and an
 * upload beyond the servlet limits is rejected by the container.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.servlet.multipart.max-file-size=3KB",
            "spring.servlet.multipart.max-request-size=6KB",
            "app.send.max-attachment-size=2KB",
            "app.send.max-total-attachment-size=4KB"
        })
class MailSendMultipartHttpTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final CapturingSmtpServer SMTP = new CapturingSmtpServer();

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired private TestRestTemplate restTemplate;

    @BeforeAll
    static void startSmtpServer() throws IOException {
        SMTP.start();
    }

    @AfterAll
    static void stopSmtpServer() {
        SMTP.stop();
    }

    @Test
    void deliversAnUploadedAttachment() throws Exception {
        byte[] uploaded = "attachment-from-http".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/api/mails/send",
                        multipartBody(
                                requestBody(),
                                new ByteArrayResource(uploaded) {
                                    @Override
                                    public String getFilename() {
                                        return "note.txt";
                                    }
                                }),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MailSendResponse sent = objectMapper.readValue(response.getBody(), MailSendResponse.class);
        assertThat(sent.attachmentCount()).isEqualTo(1);
        assertThat(sent.attachmentBytes()).isEqualTo(uploaded.length);

        MimeMessage received = new MimeMessage(null, new ByteArrayInputStream(SMTP.received()));
        assertThat(received.getSubject()).isEqualTo("Multipart over HTTP");
        assertThat(received.getHeader("Message-ID")).containsExactly(sent.messageId());

        Multipart multipart = (Multipart) received.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        BodyPart attachment = multipart.getBodyPart(1);
        assertThat(attachment.getFileName()).isEqualTo("note.txt");
        assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(uploaded);
    }

    @Test
    void rejectsAnAttachmentOverTheApplicationLimit() throws Exception {
        byte[] tooLarge = new byte[2600];

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/api/mails/send",
                        multipartBody(
                                requestBody(),
                                new ByteArrayResource(tooLarge) {
                                    @Override
                                    public String getFilename() {
                                        return "big.bin";
                                    }
                                }),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("per-attachment limit").contains("big.bin");
    }

    @Test
    void rejectsAnUploadBeyondTheServletMultipartLimit() throws Exception {
        byte[] farTooLarge = new byte[8192];

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/api/mails/send",
                        multipartBody(
                                requestBody(),
                                new ByteArrayResource(farTooLarge) {
                                    @Override
                                    public String getFilename() {
                                        return "huge.bin";
                                    }
                                }),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void listsTheQueryAndSendingToolsOverTheMcpEndpoint() {
        HttpHeaders initializeHeaders = mcpHeaders();
        ResponseEntity<String> initialized =
                restTemplate.postForEntity(
                        "/mcp",
                        new HttpEntity<>(
                                """
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":
                                {"protocolVersion":"2025-03-26","capabilities":{},
                                "clientInfo":{"name":"test","version":"1.0"}}}
                                """,
                                initializeHeaders),
                        String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders listHeaders = mcpHeaders();
        String sessionId = initialized.getHeaders().getFirst("Mcp-Session-Id");
        if (sessionId != null) {
            listHeaders.set("Mcp-Session-Id", sessionId);
        }
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/mcp",
                        new HttpEntity<>(
                                """
                                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                                """,
                                listHeaders),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("search_mails")
                .contains("get_mail_by_id")
                .contains("count_mails")
                .contains("send_mail")
                .contains("reply_mail")
                .contains("forward_mail");
    }

    @Test
    void deliversAnUploadedAttachmentInTheBackground() throws Exception {
        byte[] uploaded = "async-attachment".getBytes(StandardCharsets.UTF_8);
        MultiValueMap<String, Object> body =
                multipartBody(
                        requestBody(),
                        new ByteArrayResource(uploaded) {
                            @Override
                            public String getFilename() {
                                return "async.txt";
                            }
                        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Prefer", "respond-async");

        ResponseEntity<String> accepted =
                restTemplate.postForEntity(
                        "/api/mails/send", new HttpEntity<>(body, headers), String.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String taskId = objectMapper.readTree(accepted.getBody()).get("taskId").asText();

        MailSendTask task = awaitTask(taskId);
        assertThat(task.status()).isEqualTo(MailSendTask.Status.SUCCEEDED);
        assertThat(task.response().attachmentCount()).isEqualTo(1);
        assertThat(task.response().attachmentBytes()).isEqualTo(uploaded.length);
    }

    private MailSendTask awaitTask(String taskId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            MailSendTask task =
                    restTemplate.getForObject("/api/mails/tasks/" + taskId, MailSendTask.class);
            if (task.status() != MailSendTask.Status.PENDING) {
                return task;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("delivery task " + taskId + " did not finish in time");
    }

    private HttpHeaders mcpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        return headers;
    }

    private MultiValueMap<String, Object> multipartBody(
            Map<String, Object> request, ByteArrayResource file) throws Exception {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_PLAIN);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(
                "request", new HttpEntity<>(objectMapper.writeValueAsString(request), jsonHeaders));
        body.add("attachments", new HttpEntity<>(file, fileHeaders));
        return body;
    }

    private Map<String, Object> requestBody() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("smtpHost", "127.0.0.1");
        request.put("smtpPort", SMTP.port());
        request.put("from", "alice@example.com");
        request.put("to", List.of("bob@example.com"));
        request.put("subject", "Multipart over HTTP");
        request.put("content", "Body over HTTP");
        return request;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Embedded SMTP server that captures the delivered message. */
    private static final class CapturingSmtpServer {

        private int port;
        private SMTPServer server;
        private final CountDownLatch received = new CountDownLatch(1);
        private final List<byte[]> messages = new ArrayList<>();

        void start() throws IOException {
            port = freePort();
            server =
                    SMTPServer.port(port)
                            .hostName("test.local")
                            .messageHandlerFactory(new HandlerFactory())
                            .build();
            server.start();
        }

        void stop() {
            if (server != null) {
                server.stop();
            }
        }

        int port() {
            return port;
        }

        byte[] received() throws InterruptedException {
            assertThat(received.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .as("SMTP server received a message")
                    .isTrue();
            // the server is shared by the tests of this class: the newest message is the one the
            // current test just asked for
            return messages.get(messages.size() - 1);
        }

        private final class HandlerFactory implements MessageHandlerFactory {

            @Override
            public MessageHandler create(MessageContext context) {
                return new MessageHandler() {
                    @Override
                    public void from(String from) {
                        // envelope sender, not needed here
                    }

                    @Override
                    public void recipient(String recipient) {
                        // envelope recipients, not needed here
                    }

                    @Override
                    public String data(InputStream data) throws IOException {
                        messages.add(data.readAllBytes());
                        received.countDown();
                        return null;
                    }

                    @Override
                    public void done() {
                        // nothing to clean up
                    }
                };
            }
        }
    }
}
