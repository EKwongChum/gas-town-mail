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
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import uk.ekwong.mailcommon.storage.ObjectStorageService;
import uk.ekwong.mailmcpserver.send.CapturingMailTransport;
import uk.ekwong.mailmcpserver.send.MailCompositionService;
import uk.ekwong.mailmcpserver.send.MailSendIdempotencyStore;
import uk.ekwong.mailmcpserver.send.MailSendProperties;
import uk.ekwong.mailmcpserver.send.MailSendRequestMapper;
import uk.ekwong.mailmcpserver.send.MailSendService;
import uk.ekwong.mailmcpserver.send.MailSendTaskService;
import uk.ekwong.mailmcpserver.send.MailSendTaskStore;
import uk.ekwong.mailmcpserver.send.OriginalEmailContentParser;
import uk.ekwong.mailmcpserver.send.TestOriginalEmails;

class MailSendControllerTest {

    private static final String ARCHIVE_ID =
            "YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=";

    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CapturingMailTransport transport = new CapturingMailTransport();

    private MailSendProperties properties;
    private MailSendIdempotencyStore idempotencyStore;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new MailSendProperties();
        idempotencyStore = new MailSendIdempotencyStore(properties);
        mockMvc = mockMvc(new InlineTaskExecutor());
    }

    private MockMvc mockMvc(AsyncTaskExecutor executor) {
        MailSendRequestMapper requestMapper = new MailSendRequestMapper(properties);
        MailSendService sendService = new MailSendService(transport, new SimpleMeterRegistry());
        return MockMvcBuilders.standaloneSetup(
                        new MailSendController(
                                sendService,
                                requestMapper,
                                new MailCompositionService(
                                        requestMapper, storage, new OriginalEmailContentParser()),
                                idempotencyStore,
                                new MailSendTaskService(
                                        sendService, new MailSendTaskStore(properties), executor),
                                properties))
                .setControllerAdvice(new MailSendExceptionHandler())
                .build();
    }

    @Test
    void sendsMailWithAttachment() throws Exception {
        Map<String, Object> request = sendRequest();
        request.put(
                "attachments",
                List.of(
                        Map.of(
                                "filename",
                                "note.txt",
                                "contentType",
                                "text/plain",
                                "contentBase64",
                                base64("attachment-data"))));

        mockMvc.perform(json("/api/mails/send", request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Hello"))
                .andExpect(jsonPath("$.from").value("alice@example.com"))
                .andExpect(jsonPath("$.to[0]").value("Bob <bob@example.com>"))
                .andExpect(jsonPath("$.attachmentCount").value(1))
                .andExpect(jsonPath("$.attachmentBytes").value(15))
                .andExpect(jsonPath("$.messageId").value(containsString("@example.com>")));

        MimeMessage sent = transport.lastMessage();
        assertThat(sent.getSubject()).isEqualTo("Hello");
        assertThat(sent.getContent()).isInstanceOf(Multipart.class);
        Multipart multipart = (Multipart) sent.getContent();
        assertThat(multipart.getBodyPart(1).getFileName()).isEqualTo("note.txt");
    }

    @Test
    void returns400ForInvalidSendParameters() throws Exception {
        Map<String, Object> request = sendRequest();
        request.remove("smtpHost");

        mockMvc.perform(json("/api/mails/send", request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("smtpHost must not be blank"));
    }

    @Test
    void returns400ForAttachmentOverTheConfiguredLimit() throws Exception {
        properties.setMaxAttachmentSize(DataSize.ofBytes(4));
        Map<String, Object> request = sendRequest();
        request.put(
                "attachments",
                List.of(Map.of("filename", "big.bin", "contentBase64", base64("0123456789"))));

        mockMvc.perform(json("/api/mails/send", request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("per-attachment limit")));
    }

    @Test
    void returns400ForMalformedOrEmptyBody() throws Exception {
        mockMvc.perform(
                        post("/api/mails/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));

        mockMvc.perform(
                        post("/api/mails/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    @Test
    void repliesToAnArchivedMail() throws Exception {
        givenArchivedMail();
        Map<String, Object> request = sendRequest();
        request.put("id", ARCHIVE_ID);
        request.remove("to");
        request.put("content", "Thanks for the update");
        request.remove("subject");

        mockMvc.perform(json("/api/mails/reply", request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Re: Quarterly report"))
                .andExpect(jsonPath("$.to[0]").value("Alice Replies <alice.reply@example.com>"));

        MimeMessage sent = transport.lastMessage();
        assertThat(sent.getHeader("In-Reply-To")).containsExactly("<original-123@example.com>");
        assertThat((String) sent.getContent()).contains("> please review the numbers.");
    }

    @Test
    void forwardsAnArchivedMailWithItsAttachments() throws Exception {
        givenArchivedMail();
        Map<String, Object> request = sendRequest();
        request.put("id", ARCHIVE_ID);
        request.put("to", List.of("Dave <dave@example.com>"));
        request.put("content", "See below");
        request.remove("subject");

        mockMvc.perform(json("/api/mails/forward", request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Fwd: Quarterly report"))
                .andExpect(jsonPath("$.attachmentCount").value(1))
                .andExpect(jsonPath("$.to[0]").value("Dave <dave@example.com>"));

        MimeMessage sent = transport.lastMessage();
        Multipart multipart = (Multipart) sent.getContent();
        assertThat(multipart.getBodyPart(1).getFileName()).isEqualTo("report.pdf");
    }

    @Test
    void returns404WhenTheArchivedMailIsMissing() throws Exception {
        when(storage.readIfPresent(ARCHIVE_ID)).thenReturn(Optional.empty());
        Map<String, Object> request = sendRequest();
        request.put("id", ARCHIVE_ID);

        mockMvc.perform(json("/api/mails/reply", request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString(ARCHIVE_ID)));
    }

    @Test
    void returns502WhenTheSmtpServerIsUnreachable() throws Exception {
        transport.failWith(new MessagingException("connection refused"));

        mockMvc.perform(json("/api/mails/send", sendRequest()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("connection refused")));
    }

    @Test
    void returns403ForAnSmtpHostOutsideTheAllowList() throws Exception {
        properties.setAllowedSmtpHosts(List.of("smtp.example.com"));
        Map<String, Object> request = sendRequest();
        request.put("smtpHost", "evil.example.com");

        mockMvc.perform(json("/api/mails/send", request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("is not allowed")));
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void sendsMailWithUploadedMultipartAttachments() throws Exception {
        MockMultipartFile attachment =
                new MockMultipartFile(
                        "attachments",
                        "note.txt",
                        "text/plain",
                        "attachment-data".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                        multipart("/api/mails/send")
                                .file(requestPart(sendRequest()))
                                .file(attachment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Hello"))
                .andExpect(jsonPath("$.attachmentCount").value(1))
                .andExpect(jsonPath("$.attachmentBytes").value(15));

        MimeMessage sent = transport.lastMessage();
        assertThat(sent.getSubject()).isEqualTo("Hello");
        Multipart multipart = (Multipart) sent.getContent();
        assertThat(multipart.getBodyPart(1).getFileName()).isEqualTo("note.txt");
        assertThat(
                        new String(
                                multipart.getBodyPart(1).getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8))
                .isEqualTo("attachment-data");
    }

    @Test
    void forwardsAnArchivedMailWithUploadedMultipartAttachments() throws Exception {
        givenArchivedMail();
        Map<String, Object> body = sendRequest();
        body.put("id", ARCHIVE_ID);
        body.put("to", List.of("Dave <dave@example.com>"));
        body.remove("subject");
        MockMultipartFile attachment =
                new MockMultipartFile(
                        "attachments",
                        "extra.txt",
                        "text/plain",
                        "extra".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/mails/forward").file(requestPart(body)).file(attachment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Fwd: Quarterly report"))
                .andExpect(jsonPath("$.attachmentCount").value(2));

        Multipart multipart = (Multipart) transport.lastMessage().getContent();
        assertThat(multipart.getCount()).isEqualTo(3);
        assertThat(multipart.getBodyPart(1).getFileName()).isEqualTo("extra.txt");
        assertThat(multipart.getBodyPart(2).getFileName()).isEqualTo("report.pdf");
    }

    @Test
    void repliesToAnArchivedMailWithAMultipartRequest() throws Exception {
        givenArchivedMail();
        Map<String, Object> body = sendRequest();
        body.put("id", ARCHIVE_ID);
        body.remove("to");
        body.remove("subject");
        body.put("replyAll", true);

        mockMvc.perform(multipart("/api/mails/reply").file(requestPart(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Re: Quarterly report"))
                .andExpect(jsonPath("$.to[0]").value("Alice Replies <alice.reply@example.com>"))
                .andExpect(jsonPath("$.cc[0]").value("Bob <bob@example.com>"));
    }

    @Test
    void returns400WhenTheMultipartRequestPartIsMissing() throws Exception {
        MockMultipartFile attachment =
                new MockMultipartFile(
                        "attachments",
                        "note.txt",
                        "text/plain",
                        "x".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/mails/send").file(attachment))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required part 'request'"));
    }

    @Test
    void returns400ForInvalidMultipartUploads() throws Exception {
        MockMultipartFile empty =
                new MockMultipartFile("attachments", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/mails/send").file(requestPart(sendRequest())).file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("has no content")));

        Map<String, Object> withBase64 = sendRequest();
        withBase64.put(
                "attachments", List.of(Map.of("filename", "a.bin", "contentBase64", "YWxpY2U=")));
        mockMvc.perform(multipart("/api/mails/send").file(requestPart(withBase64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("multipart file parts")));
    }

    @Test
    void returns415ForAnUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/mails/send").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value(containsString("multipart/form-data")));
    }

    @Test
    void replaysAnIdempotentRequestWithoutSendingTwice() throws Exception {
        Map<String, Object> request = sendRequest();

        String first =
                mockMvc.perform(json("/api/mails/send", request).header("Idempotency-Key", "key-1"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String second =
                mockMvc.perform(json("/api/mails/send", request).header("Idempotency-Key", "key-1"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(transport.size()).isEqualTo(1);
    }

    @Test
    void returns409WhileAnIdempotentRequestIsStillRunning() throws Exception {
        idempotencyStore.reserve("key-2");

        mockMvc.perform(json("/api/mails/send", sendRequest()).header("Idempotency-Key", "key-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("still being sent")));
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void rejectsABlankIdempotencyKey() throws Exception {
        mockMvc.perform(json("/api/mails/send", sendRequest()).header("Idempotency-Key", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Idempotency-Key")));
    }

    @Test
    void acceptsABackgroundDeliveryAndExposesItsTask() throws Exception {
        String accepted =
                mockMvc.perform(
                                json("/api/mails/send", sendRequest())
                                        .header("Prefer", "respond-async"))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.taskId").isNotEmpty())
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String taskId = objectMapper.readTree(accepted).get("taskId").asText();
        assertThat(transport.size()).isEqualTo(1);

        mockMvc.perform(get("/api/mails/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.response.messageId").isNotEmpty());
    }

    @Test
    void rejectsBackgroundDeliveryWhenItIsDisabled() throws Exception {
        properties.getAsync().setEnabled(false);

        mockMvc.perform(json("/api/mails/send", sendRequest()).header("Prefer", "respond-async"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("asynchronous delivery")));
    }

    @Test
    void returns429WhenTheBackgroundQueueIsFull() throws Exception {
        mockMvc = mockMvc(new RejectingTaskExecutor());

        mockMvc.perform(json("/api/mails/send", sendRequest()).header("Prefer", "respond-async"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(containsString("background deliveries")));
    }

    @Test
    void returns404ForAnUnknownDeliveryTask() throws Exception {
        mockMvc.perform(get("/api/mails/tasks/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("unknown")));
    }

    private MockMultipartFile requestPart(Map<String, Object> body) throws Exception {
        return new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(body));
    }

    private void givenArchivedMail() {
        when(storage.readIfPresent(ARCHIVE_ID))
                .thenReturn(Optional.of(TestOriginalEmails.withAttachment()));
    }

    private Map<String, Object> sendRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("smtpHost", "smtp.example.com");
        request.put("smtpPort", 587);
        request.put("smtpUsername", "alice@example.com");
        request.put("smtpPassword", "secret");
        request.put("to", List.of("Bob <bob@example.com>"));
        request.put("subject", "Hello");
        request.put("content", "Body text");
        return request;
    }

    private MockHttpServletRequestBuilder json(String path, Map<String, Object> body)
            throws Exception {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Runs background deliveries on the calling thread so the tests stay deterministic. */
    private static final class InlineTaskExecutor implements AsyncTaskExecutor {

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public void execute(Runnable task, long startTimeout) {
            task.run();
        }
    }

    private static final class RejectingTaskExecutor implements AsyncTaskExecutor {

        @Override
        public void execute(Runnable task) {
            throw new TaskRejectedException("queue is full");
        }

        @Override
        public void execute(Runnable task, long startTimeout) {
            throw new TaskRejectedException("queue is full");
        }
    }
}
