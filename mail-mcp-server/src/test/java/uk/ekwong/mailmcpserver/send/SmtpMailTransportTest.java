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

package uk.ekwong.mailmcpserver.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.auth.EasyAuthenticationHandlerFactory;
import org.subethamail.smtp.auth.LoginFailedException;
import org.subethamail.smtp.server.SMTPServer;

/** End-to-end test: a real SMTP conversation against an embedded server. */
class SmtpMailTransportTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void deliversMailWithBodyAndAttachment() throws Exception {
        CapturingServer server = new CapturingServer();
        server.start();
        try {
            MailSendService service =
                    new MailSendService(
                            new SmtpMailTransport(new MailSendProperties()),
                            new SimpleMeterRegistry());
            MailSendCommand command =
                    new MailSendCommand(
                            new SmtpSettings("127.0.0.1", server.port(), null, null, null),
                            "alice@example.com",
                            List.of("Bob <bob@example.com>"),
                            List.of("carol@example.com"),
                            "SMTP end to end",
                            "Hello Bob, see the attachment.",
                            false,
                            List.of(
                                    MailAttachment.of(
                                            "note.txt",
                                            "text/plain",
                                            "attachment-data".getBytes(StandardCharsets.UTF_8))),
                            null,
                            List.of(),
                            null,
                            "<end-to-end@example.com>");

            MailSendResponse response = service.send(command);
            byte[] raw = server.received();
            MimeMessage received =
                    new MimeMessage(
                            Session.getInstance(new Properties()), new ByteArrayInputStream(raw));

            assertThat(received.getSubject()).isEqualTo("SMTP end to end");
            assertThat(received.getFrom()[0].toString()).isEqualTo("alice@example.com");
            assertThat(received.getAllRecipients())
                    .extracting(Address::toString)
                    .containsExactlyInAnyOrder("Bob <bob@example.com>", "carol@example.com");
            assertThat(received.getHeader("Message-ID")).containsExactly(response.messageId());
            assertThat(received.getContent()).isInstanceOf(Multipart.class);

            Multipart multipart = (Multipart) received.getContent();
            assertThat(multipart.getCount()).isEqualTo(2);
            BodyPart body = multipart.getBodyPart(0);
            assertThat((String) body.getContent()).isEqualTo("Hello Bob, see the attachment.");
            BodyPart attachment = multipart.getBodyPart(1);
            assertThat(MimeUtility.decodeText(attachment.getFileName())).isEqualTo("note.txt");
            assertThat(
                            new String(
                                    attachment.getInputStream().readAllBytes(),
                                    StandardCharsets.UTF_8))
                    .isEqualTo("attachment-data");

            assertThat(server.envelopeSender()).isEqualTo("alice@example.com");
            assertThat(server.envelopeRecipients())
                    .containsExactlyInAnyOrder("bob@example.com", "carol@example.com");
        } finally {
            server.stop();
        }
    }

    @Test
    void failsWhenTheSmtpServerIsUnreachable() throws Exception {
        int port = freePort();
        MailSendService service =
                new MailSendService(
                        new SmtpMailTransport(new MailSendProperties()), new SimpleMeterRegistry());
        MailSendCommand command =
                new MailSendCommand(
                        new SmtpSettings("127.0.0.1", port, null, null, null),
                        "alice@example.com",
                        List.of("bob@example.com"),
                        List.of(),
                        "Subject",
                        "Body",
                        false,
                        List.of(),
                        null,
                        List.of(),
                        null,
                        "<unreachable@example.com>");

        assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(MailSendFailedException.class)
                .hasMessageContaining("SMTP send via 127.0.0.1:" + port + " (auto) failed");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void authenticatesAndSendsWithTheEnvelopeSenderOfTheCommand() throws Exception {
        CapturingServer server = new CapturingServer();
        server.requireAuthentication("alice@example.com", "secret");
        server.start();
        try {
            MailSendService service =
                    new MailSendService(
                            new SmtpMailTransport(new MailSendProperties()),
                            new SimpleMeterRegistry());
            MailSendCommand command =
                    new MailSendCommand(
                            new SmtpSettings(
                                    "127.0.0.1",
                                    server.port(),
                                    "alice@example.com",
                                    "secret",
                                    SmtpEncryption.NONE),
                            "Alice <alice@example.com>",
                            List.of("bob@example.com"),
                            List.of(),
                            "Authenticated",
                            "Body",
                            false,
                            List.of(),
                            null,
                            List.of(),
                            "noreply@example.com",
                            "<authenticated@example.com>");

            service.send(command);
            server.received();

            assertThat(server.authenticatedUsers()).containsExactly("alice@example.com");
            // the envelope sender comes from the command, not from the From header
            assertThat(server.envelopeSender()).isEqualTo("noreply@example.com");
        } finally {
            server.stop();
        }
    }

    @Test
    void configuresTheSessionForTheRequestedDelivery() {
        MailSendProperties properties = new MailSendProperties();
        SmtpMailTransport transport = new SmtpMailTransport(properties);

        Session starttls =
                transport.session(
                        command(SmtpEncryption.STARTTLS, "alice@example.com", "alice@example.com"));
        assertThat(starttls.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(starttls.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(starttls.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(starttls.getProperty("mail.smtp.auth")).isEqualTo("true");
        assertThat(starttls.getProperty("mail.smtp.from")).isEqualTo("alice@example.com");
        assertThat(starttls.getProperty("mail.smtp.connectiontimeout")).isEqualTo("10000");
        assertThat(starttls.getProperty("mail.smtp.timeout")).isEqualTo("30000");
        assertThat(starttls.getProperty("mail.smtp.writetimeout")).isEqualTo("60000");

        Session ssl = transport.session(command(SmtpEncryption.SSL, "alice@example.com", null));
        assertThat(ssl.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(ssl.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");

        Session plain = transport.session(command(SmtpEncryption.NONE, null, null));
        assertThat(plain.getProperty("mail.smtp.auth")).isEqualTo("false");
        assertThat(plain.getProperty("mail.smtp.from")).isNull();

        properties.setVerifyServerIdentity(false);
        assertThat(
                        transport
                                .session(command(SmtpEncryption.SSL, "alice@example.com", null))
                                .getProperty("mail.smtp.ssl.checkserveridentity"))
                .isEqualTo("false");
    }

    private MailSendCommand command(
            SmtpEncryption encryption, String envelopeFrom, String username) {
        return new MailSendCommand(
                new SmtpSettings("127.0.0.1", 2525, username, "secret", encryption),
                "Alice <alice@example.com>",
                List.of("bob@example.com"),
                List.of(),
                "Subject",
                "Body",
                false,
                List.of(),
                null,
                List.of(),
                envelopeFrom,
                "<session@example.com>");
    }

    /** Embedded SMTP server that captures the envelope and the message data. */
    private static final class CapturingServer {

        private int port;
        private final CountDownLatch received = new CountDownLatch(1);
        private final List<byte[]> messages = new ArrayList<>();
        private String envelopeSender;
        private final List<String> envelopeRecipients = new ArrayList<>();
        private final List<String> authenticatedUsers = new ArrayList<>();
        private String requiredUsername;
        private String requiredPassword;
        private SMTPServer server;

        void requireAuthentication(String username, String password) {
            this.requiredUsername = username;
            this.requiredPassword = password;
        }

        void start() throws IOException {
            port = freePort();
            SMTPServer.Builder builder =
                    SMTPServer.port(port)
                            .hostName("test.local")
                            .messageHandlerFactory(new HandlerFactory());
            if (requiredUsername != null) {
                builder =
                        builder.authenticationHandlerFactory(
                                        new EasyAuthenticationHandlerFactory(
                                                (username, password, context) -> {
                                                    if (!requiredUsername.equals(username)
                                                            || !requiredPassword.equals(password)) {
                                                        throw new LoginFailedException(
                                                                "invalid credentials");
                                                    }
                                                    authenticatedUsers.add(username);
                                                }))
                                .requireAuth();
            }
            server = builder.build();
            server.start();
        }

        List<String> authenticatedUsers() {
            return authenticatedUsers;
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
            return messages.get(0);
        }

        String envelopeSender() {
            return envelopeSender;
        }

        List<String> envelopeRecipients() {
            return envelopeRecipients;
        }

        private final class HandlerFactory implements MessageHandlerFactory {

            @Override
            public MessageHandler create(MessageContext context) {
                return new MessageHandler() {
                    @Override
                    public void from(String from) {
                        envelopeSender = from;
                    }

                    @Override
                    public void recipient(String recipient) {
                        envelopeRecipients.add(recipient);
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
