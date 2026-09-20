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

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailSendServiceTest {

    private final CapturingMailTransport transport = new CapturingMailTransport();
    private final MailSendService service = new MailSendService(transport);

    private SmtpSettings smtp;

    @BeforeEach
    void setUp() {
        smtp = new SmtpSettings("smtp.example.com", 587, "alice@example.com", "secret", null);
    }

    @Test
    void sendsPlainTextMailWithGeneratedMessageId() throws Exception {
        MailSendResponse response =
                service.send(
                        command(
                                "Quarterly report",
                                "Hello Bob",
                                false,
                                List.of(),
                                null,
                                List.of()));

        MimeMessage received = transport.lastMessage();
        assertThat(received.getSubject()).isEqualTo("Quarterly report");
        assertThat(received.getFrom()[0].toString()).isEqualTo("alice@example.com");
        assertThat(received.getRecipients(MimeMessage.RecipientType.TO)[0].toString())
                .isEqualTo("Bob <bob@example.com>");
        assertThat(received.getRecipients(MimeMessage.RecipientType.CC)[0].toString())
                .isEqualTo("carol@example.com");
        assertThat(received.getContentType()).startsWith("text/plain");
        assertThat((String) received.getContent()).isEqualTo("Hello Bob");

        assertThat(response.messageId()).startsWith("<").endsWith("@example.com>");
        assertThat(received.getHeader("Message-ID")).containsExactly(response.messageId());
        assertThat(response.subject()).isEqualTo("Quarterly report");
        assertThat(response.from()).isEqualTo("alice@example.com");
        assertThat(response.to()).containsExactly("Bob <bob@example.com>");
        assertThat(response.cc()).containsExactly("carol@example.com");
        assertThat(response.attachmentCount()).isZero();
        assertThat(response.attachmentBytes()).isZero();
        assertThat(response.sentAt()).isNotNull();
    }

    @Test
    void sendsHtmlBodyAsHtmlPart() throws Exception {
        service.send(command("Subject", "<p>Hello</p>", true, List.of(), null, List.of()));

        MimeMessage received = transport.lastMessage();
        assertThat(received.getContentType()).startsWith("text/html");
        assertThat((String) received.getContent()).isEqualTo("<p>Hello</p>");
    }

    @Test
    void sendsAttachmentsAsSeparatePartsWithDecodedFileNames() throws Exception {
        MailAttachment attachment =
                new MailAttachment(
                        "报表.pdf", "application/pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8));

        MailSendResponse response =
                service.send(
                        command("Subject", "Body", false, List.of(attachment), null, List.of()));

        MimeMessage received = transport.lastMessage();
        assertThat(received.getContent()).isInstanceOf(Multipart.class);
        Multipart multipart = (Multipart) received.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);

        Part attachmentPart = multipart.getBodyPart(1);
        assertThat(MimeUtility.decodeText(attachmentPart.getFileName())).isEqualTo("报表.pdf");
        assertThat(attachmentPart.getDisposition()).isEqualTo(Part.ATTACHMENT);
        assertThat(attachmentPart.getContentType()).startsWith("application/pdf");
        assertThat(attachmentPart.getInputStream().readAllBytes())
                .isEqualTo("pdf-bytes".getBytes(StandardCharsets.UTF_8));

        assertThat(response.attachmentCount()).isEqualTo(1);
        assertThat(response.attachmentBytes()).isEqualTo(9);
    }

    @Test
    void keepsTheThreadWithInReplyToAndReferences() throws Exception {
        service.send(
                command(
                        "Re: Quarterly report",
                        "Body",
                        false,
                        List.of(),
                        "<original-123@example.com>",
                        List.of("<thread-1@example.com>", "<original-123@example.com>")));

        MimeMessage received = transport.lastMessage();
        assertThat(received.getHeader("In-Reply-To")).containsExactly("<original-123@example.com>");
        assertThat(received.getHeader("References"))
                .containsExactly("<thread-1@example.com> <original-123@example.com>");
    }

    @Test
    void encodesNonAsciiSubjects() throws Exception {
        service.send(command("季度报告", "正文", false, List.of(), null, List.of()));

        MimeMessage received = transport.lastMessage();
        assertThat(received.getSubject()).isEqualTo("季度报告");
    }

    @Test
    void rejectsMailWithoutRecipients() {
        MailSendCommand withoutRecipients =
                new MailSendCommand(
                        smtp,
                        "alice@example.com",
                        List.of(),
                        List.of(),
                        "Subject",
                        "Body",
                        false,
                        List.of(),
                        null,
                        List.of(),
                        "alice@example.com",
                        "<generated@example.com>");

        assertThatThrownBy(() -> service.send(withoutRecipients))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("to must not be empty");
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void wrapsTransportFailuresAsBadGatewayErrors() {
        transport.failWith(new MessagingException("connection refused"));

        assertThatThrownBy(
                        () ->
                                service.send(
                                        command(
                                                "Subject", "Body", false, List.of(), null,
                                                List.of())))
                .isInstanceOf(MailSendFailedException.class)
                .hasMessageContaining("smtp.example.com:587")
                .hasMessageContaining("connection refused");
    }

    private MailSendCommand command(
            String subject,
            String body,
            boolean html,
            List<MailAttachment> attachments,
            String inReplyTo,
            List<String> references) {
        return new MailSendCommand(
                smtp,
                "alice@example.com",
                List.of("Bob <bob@example.com>"),
                List.of("carol@example.com"),
                subject,
                body,
                html,
                attachments,
                inReplyTo,
                references,
                smtp.authenticated() ? smtp.username() : null,
                "<generated@example.com>");
    }
}
