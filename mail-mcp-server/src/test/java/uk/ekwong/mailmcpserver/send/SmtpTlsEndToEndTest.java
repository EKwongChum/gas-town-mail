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
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.server.SMTPServer;

/**
 * End-to-end test of an encrypted delivery: the embedded SMTP server speaks implicit TLS (the mode
 * used on port 465) with a self-signed certificate for {@code localhost}, and the client verifies
 * the server identity by default.
 */
class SmtpTlsEndToEndTest {

    private static final String KEYSTORE = "/tls/test-smtp.p12";
    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void deliversOverImplicitTlsWhenTheCertificateMatches() throws Exception {
        CapturingServer server = new CapturingServer();
        server.start();
        try {
            MailSendResponse response = send(server.port(), "localhost", true);

            assertThat(response.messageId()).isEqualTo("<tls@example.com>");
            MimeMessage received = server.receivedMessage();
            assertThat(received.getSubject()).isEqualTo("TLS end to end");
            assertThat(received.getHeader("Message-ID")).containsExactly("<tls@example.com>");
            assertThat(received.getAllRecipients()[0].toString()).isEqualTo("bob@example.com");
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsACertificateThatDoesNotMatchTheHostName() throws Exception {
        CapturingServer server = new CapturingServer();
        server.start();
        try {
            // the certificate is only valid for "localhost", not for the IP address
            assertThatThrownBy(() -> send(server.port(), "127.0.0.1", true))
                    .isInstanceOf(MailSendFailedException.class)
                    .hasMessageContaining("SMTP send via 127.0.0.1:" + server.port());
        } finally {
            server.stop();
        }
    }

    @Test
    void acceptsAMismatchedCertificateWhenVerificationIsDisabled() throws Exception {
        CapturingServer server = new CapturingServer();
        server.start();
        try {
            assertThat(send(server.port(), "127.0.0.1", false).messageId())
                    .isEqualTo("<tls@example.com>");
            assertThat(server.receivedMessage().getSubject()).isEqualTo("TLS end to end");
        } finally {
            server.stop();
        }
    }

    private MailSendResponse send(int port, String host, boolean verifyServerIdentity) {
        MailSendProperties properties = new MailSendProperties();
        properties.setVerifyServerIdentity(verifyServerIdentity);
        MailSendService service =
                new MailSendService(new SmtpMailTransport(properties), new SimpleMeterRegistry());
        MailSendCommand command =
                new MailSendCommand(
                        new SmtpSettings(host, port, null, null, SmtpEncryption.SSL),
                        "alice@example.com",
                        List.of("bob@example.com"),
                        List.of(),
                        "TLS end to end",
                        "Body over TLS",
                        false,
                        List.of(),
                        null,
                        List.of(),
                        null,
                        "<tls@example.com>");
        return service.send(command);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static SSLContext serverSslContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = SmtpTlsEndToEndTest.class.getResourceAsStream(KEYSTORE)) {
            keyStore.load(in, PASSWORD);
        }
        KeyManagerFactory keyManagers =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    /** SMTP server that accepts TLS connections directly (implicit TLS, as on port 465). */
    private static final class CapturingServer {

        private int port;
        private SMTPServer server;
        private final List<byte[]> messages = new ArrayList<>();
        private final CountDownLatch received = new CountDownLatch(1);

        void start() throws Exception {
            port = freePort();
            server =
                    SMTPServer.port(port)
                            .hostName("localhost")
                            .messageHandlerFactory(new HandlerFactory())
                            .serverSocketFactory(serverSslContext())
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

        MimeMessage receivedMessage() throws InterruptedException, MessagingException {
            assertThat(received.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .as("SMTP server received a message over TLS")
                    .isTrue();
            return new MimeMessage(
                    Session.getInstance(new Properties()),
                    new ByteArrayInputStream(messages.get(0)));
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
