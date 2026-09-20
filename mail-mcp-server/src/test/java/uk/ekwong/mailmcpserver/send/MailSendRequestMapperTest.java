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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class MailSendRequestMapperTest {

    private MailSendProperties properties;
    private MailSendRequestMapper mapper;

    @BeforeEach
    void setUp() {
        properties = new MailSendProperties();
        mapper = new MailSendRequestMapper(properties);
    }

    @Test
    void attachmentLimitsDefaultToTenAndTwentyMegabytes() {
        assertThat(new MailSendProperties().getMaxAttachmentSize().toBytes())
                .isEqualTo(10L * 1024 * 1024);
        assertThat(new MailSendProperties().getMaxTotalAttachmentSize().toBytes())
                .isEqualTo(20L * 1024 * 1024);
    }

    @Test
    void mapsSendParametersAndDefaultsFromToTheSmtpAccount() {
        MailSendCommand command = mapper.toCommand(sendRequest());

        assertThat(command.smtp().host()).isEqualTo("smtp.example.com");
        assertThat(command.smtp().port()).isEqualTo(587);
        assertThat(command.smtp().authenticated()).isTrue();
        // credentials force an encrypted connection unless plaintext is explicitly allowed
        assertThat(command.smtp().encryption()).isEqualTo(SmtpEncryption.STARTTLS);
        assertThat(command.from()).isEqualTo("alice@example.com");
        assertThat(command.envelopeFrom()).isEqualTo("alice@example.com");
        assertThat(command.messageId()).startsWith("<").endsWith("@example.com>");
        assertThat(command.to()).containsExactly("Bob <bob@example.com>");
        assertThat(command.cc()).containsExactly("carol@example.com");
        assertThat(command.subject()).isEqualTo("Hello");
        assertThat(command.body()).isEqualTo("Body text");
        assertThat(command.html()).isFalse();
        assertThat(command.attachments()).isEmpty();
        assertThat(command.inReplyTo()).isNull();
        assertThat(command.references()).isEmpty();
    }

    @Test
    void acceptsAnExplicitFromWithDisplayNameAndHtmlBody() {
        MailSendCommand command =
                mapper.toCommand(
                        new MailSendRequest(
                                "smtp.example.com",
                                465,
                                "alice@example.com",
                                "secret",
                                "ssl",
                                "Alice <alice@example.com>",
                                List.of("bob@example.com"),
                                List.of(),
                                "Subject",
                                "<p>html</p>",
                                true,
                                List.of()));

        assertThat(command.from()).isEqualTo("Alice <alice@example.com>");
        assertThat(command.smtp().encryption()).isEqualTo(SmtpEncryption.SSL);
        assertThat(command.html()).isTrue();
    }

    @Test
    void parsesEncryptionCaseInsensitively() {
        assertThat(mapper.toCommand(requestWithEncryption("STARTTLS")).smtp().encryption())
                .isEqualTo(SmtpEncryption.STARTTLS);
        assertThat(mapper.toCommand(requestWithEncryption(" ")).smtp().encryption())
                .isEqualTo(SmtpEncryption.STARTTLS);
        assertThat(mapper.toCommand(requestWithEncryption("ssl")).smtp().encryption())
                .isEqualTo(SmtpEncryption.SSL);
    }

    @Test
    void allowsServersWithoutAuthentication() {
        MailSendCommand command =
                mapper.toCommand(
                        new MailSendRequest(
                                "smtp.example.com",
                                25,
                                null,
                                null,
                                null,
                                "Alice <alice@example.com>",
                                List.of("bob@example.com"),
                                List.of(),
                                "Subject",
                                "Body",
                                null,
                                List.of()));

        assertThat(command.smtp().authenticated()).isFalse();
        assertThat(command.from()).isEqualTo("Alice <alice@example.com>");
    }

    @Test
    void decodesAttachmentsAndDefaultsTheirContentType() {
        MailSendCommand command =
                mapper.toCommand(
                        sendRequest(
                                new MailAttachmentRequest(
                                        "report.pdf", "application/pdf", base64("pdf-bytes")),
                                new MailAttachmentRequest("notes.txt", null, base64("notes"))));

        assertThat(command.attachments()).hasSize(2);
        assertThat(command.attachments().get(0).filename()).isEqualTo("report.pdf");
        assertThat(command.attachments().get(0).contentType()).isEqualTo("application/pdf");
        assertThat(new String(command.attachments().get(0).content(), StandardCharsets.UTF_8))
                .isEqualTo("pdf-bytes");
        assertThat(command.attachments().get(1).contentType())
                .isEqualTo("application/octet-stream");
        assertThat(command.attachmentBytes()).isEqualTo(14);
    }

    @Test
    void collapsesDuplicateAndBlankRecipients() {
        MailSendCommand command =
                mapper.toCommand(
                        new MailSendRequest(
                                "smtp.example.com",
                                587,
                                "alice@example.com",
                                "secret",
                                null,
                                null,
                                List.of(
                                        " Bob <bob@example.com> ",
                                        "",
                                        "bob@example.com, carol@example.com"),
                                null,
                                "Subject",
                                "Body",
                                null,
                                null));

        assertThat(command.to()).containsExactly("Bob <bob@example.com>", "carol@example.com");
        assertThat(command.cc()).isEmpty();
    }

    @Test
    void rejectsMissingSmtpCoordinates() {
        assertThatThrownBy(() -> mapper.toCommand(withHost(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smtpHost");
        assertThatThrownBy(() -> mapper.toCommand(withPort(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smtpPort must not be null");
        assertThatThrownBy(() -> mapper.toCommand(withPort(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smtpPort must be between 1 and 65535");
        assertThatThrownBy(() -> mapper.toCommand(withPort(70000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smtpPort must be between 1 and 65535");
    }

    @Test
    void rejectsPasswordWithoutUsernameAndBadEncryption() {
        assertThatThrownBy(
                        () ->
                                mapper.toCommand(
                                        new MailSendRequest(
                                                "smtp.example.com",
                                                587,
                                                "alice@example.com",
                                                "  ",
                                                null,
                                                null,
                                                List.of("bob@example.com"),
                                                List.of(),
                                                "Subject",
                                                "Body",
                                                null,
                                                List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smtpPassword");
        assertThatThrownBy(() -> mapper.toCommand(requestWithEncryption("tls")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smtpEncryption");
    }

    @Test
    void rejectsInvalidAddresses() {
        assertThatThrownBy(
                        () ->
                                mapper.toCommand(
                                        new MailSendRequest(
                                                "smtp.example.com",
                                                587,
                                                null,
                                                null,
                                                null,
                                                "not-an-address",
                                                List.of("bob@example.com"),
                                                List.of(),
                                                "Subject",
                                                "Body",
                                                null,
                                                List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
        assertThatThrownBy(
                        () ->
                                mapper.toCommand(
                                        new MailSendRequest(
                                                "smtp.example.com",
                                                587,
                                                "alice@example.com",
                                                "secret",
                                                null,
                                                null,
                                                List.of("bob@@example.com"),
                                                List.of(),
                                                "Subject",
                                                "Body",
                                                null,
                                                List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to");
    }

    @Test
    void rejectsAttachmentsOverTheConfiguredLimits() {
        properties.setMaxAttachmentSize(DataSize.ofBytes(4));
        properties.setMaxTotalAttachmentSize(DataSize.ofBytes(6));

        assertThatThrownBy(
                        () ->
                                mapper.toCommand(
                                        sendRequest(
                                                new MailAttachmentRequest(
                                                        "big.bin", null, base64("12345")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-attachment limit");

        assertThatThrownBy(
                        () ->
                                mapper.toCommand(
                                        sendRequest(
                                                new MailAttachmentRequest(
                                                        "a.bin", null, base64("1234")),
                                                new MailAttachmentRequest(
                                                        "b.bin", null, base64("1234")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total attachment size");
    }

    @Test
    void rejectsMalformedAttachments() {
        assertThatThrownBy(
                        () ->
                                mapper.toCommand(
                                        sendRequest(
                                                new MailAttachmentRequest(
                                                        null, null, base64("x")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename");
        assertThatThrownBy(
                        () ->
                                mapper.toCommand(
                                        sendRequest(new MailAttachmentRequest("a.bin", null, "A"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid Base64");
        assertThatThrownBy(
                        () ->
                                mapper.toCommand(
                                        sendRequest(new MailAttachmentRequest("a.bin", null, " "))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no content");
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> mapper.toCommand(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request body must not be null");
    }

    @Test
    void rejectsSmtpHostsOutsideTheAllowList() {
        properties.setAllowedSmtpHosts(List.of("smtp.example.com", "*.corp.example"));

        assertThat(mapper.toCommand(sendRequest()).smtp().host()).isEqualTo("smtp.example.com");
        assertThat(mapper.toCommand(withHost("mail.corp.example")).smtp().host())
                .isEqualTo("mail.corp.example");

        assertThatThrownBy(() -> mapper.toCommand(withHost("evil.example.com")))
                .isInstanceOf(SmtpHostNotAllowedException.class)
                .hasMessageContaining("evil.example.com")
                .hasMessageContaining("permitted hosts");
        // the wildcard only covers sub-domains
        assertThatThrownBy(() -> mapper.toCommand(withHost("corp.example")))
                .isInstanceOf(SmtpHostNotAllowedException.class);
    }

    @Test
    void requiresEncryptionWhenCredentialsAreSent() {
        assertThat(mapper.toCommand(sendRequest()).smtp().encryption())
                .isEqualTo(SmtpEncryption.STARTTLS);
        assertThat(mapper.toCommand(withPort(465)).smtp().encryption())
                .isEqualTo(SmtpEncryption.SSL);

        assertThatThrownBy(() -> mapper.toCommand(requestWithEncryption("none")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allow-plaintext-credentials");

        properties.setAllowPlaintextCredentials(true);
        assertThat(mapper.toCommand(requestWithEncryption("none")).smtp().encryption())
                .isEqualTo(SmtpEncryption.NONE);
    }

    @Test
    void keepsUnencryptedServersUsableWithoutCredentials() {
        MailSendCommand command = mapper.toCommand(anonymousRequest());

        assertThat(command.smtp().authenticated()).isFalse();
        assertThat(command.smtp().encryption()).isEqualTo(SmtpEncryption.AUTO);
        assertThat(command.envelopeFrom()).isNull();
        assertThat(command.from()).isEqualTo("Alice <alice@example.com>");
        assertThat(command.messageId()).startsWith("<").endsWith("@example.com>");
    }

    @Test
    void readsAttachmentsFromMultipartFiles() {
        MockMultipartFile report =
                new MockMultipartFile(
                        "attachments",
                        "report.pdf",
                        "application/pdf",
                        "pdf-bytes".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile notes =
                new MockMultipartFile(
                        "attachments", "notes.txt", null, "notes".getBytes(StandardCharsets.UTF_8));

        MailSendCommand command = mapper.toMultipartCommand(sendRequest(), List.of(report, notes));

        assertThat(command.from()).isEqualTo("alice@example.com");
        assertThat(command.subject()).isEqualTo("Hello");
        assertThat(command.attachments()).hasSize(2);
        assertThat(command.attachments().get(0).filename()).isEqualTo("report.pdf");
        assertThat(command.attachments().get(0).contentType()).isEqualTo("application/pdf");
        assertThat(new String(command.attachments().get(0).content(), StandardCharsets.UTF_8))
                .isEqualTo("pdf-bytes");
        assertThat(command.attachments().get(1).contentType())
                .isEqualTo("application/octet-stream");
        assertThat(command.attachmentBytes()).isEqualTo(14);
    }

    @Test
    void acceptsMultipartRequestsWithoutFiles() {
        MailSendCommand command = mapper.toMultipartCommand(sendRequest(), null);

        assertThat(command.attachments()).isEmpty();
        assertThat(command.smtp().host()).isEqualTo("smtp.example.com");
    }

    @Test
    void stripsDirectoryPartsAndControlCharactersFromUploadedFileNames() {
        MockMultipartFile windowsPath =
                new MockMultipartFile(
                        "attachments",
                        "C:\\Users\\alice\\re\r\nport.pdf",
                        "application/pdf",
                        "x".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile unixPath =
                new MockMultipartFile(
                        "attachments",
                        "/tmp/uploads/notes.txt",
                        "text/plain",
                        "y".getBytes(StandardCharsets.UTF_8));

        MailSendCommand command =
                mapper.toMultipartCommand(sendRequest(), List.of(windowsPath, unixPath));

        assertThat(command.attachments())
                .extracting(MailAttachment::filename)
                .containsExactly("report.pdf", "notes.txt");
    }

    @Test
    void rejectsEmptyOrNamelessUploads() {
        MockMultipartFile empty =
                new MockMultipartFile("attachments", "empty.txt", "text/plain", new byte[0]);
        MockMultipartFile nameless =
                new MockMultipartFile(
                        "attachments", null, "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> mapper.toMultipartCommand(sendRequest(), List.of(empty)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attachment 'empty.txt' has no content");
        assertThatThrownBy(() -> mapper.toMultipartCommand(sendRequest(), List.of(nameless)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attachment filename must not be blank");
    }

    @Test
    void rejectsUploadedAttachmentsOverTheConfiguredLimits() {
        properties.setMaxAttachmentSize(DataSize.ofBytes(4));
        properties.setMaxTotalAttachmentSize(DataSize.ofBytes(6));
        MockMultipartFile big =
                new MockMultipartFile(
                        "attachments", "big.bin", null, "12345".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile small =
                new MockMultipartFile(
                        "attachments", "small.bin", null, "1234".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> mapper.toMultipartCommand(sendRequest(), List.of(big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-attachment limit");
        assertThatThrownBy(() -> mapper.toMultipartCommand(sendRequest(), List.of(small, small)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total attachment size");
    }

    @Test
    void rejectsBase64AttachmentsInMultipartRequests() {
        MailSendRequest request =
                sendRequest(new MailAttachmentRequest("report.pdf", null, base64("pdf")));

        assertThatThrownBy(() -> mapper.toMultipartCommand(request, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multipart file parts");
    }

    private MailSendRequest requestWithEncryption(String encryption) {
        return new MailSendRequest(
                "smtp.example.com",
                587,
                "alice@example.com",
                "secret",
                encryption,
                null,
                List.of("bob@example.com"),
                List.of(),
                "Subject",
                "Body",
                null,
                List.of());
    }

    private MailSendRequest anonymousRequest() {
        return new MailSendRequest(
                "smtp.example.com",
                25,
                null,
                null,
                null,
                "Alice <alice@example.com>",
                List.of("bob@example.com"),
                List.of(),
                "Subject",
                "Body",
                null,
                List.of());
    }

    private MailSendRequest withHost(String host) {
        MailSendRequest request = sendRequest();
        return new MailSendRequest(
                host,
                request.smtpPort(),
                request.smtpUsername(),
                request.smtpPassword(),
                request.smtpEncryption(),
                request.from(),
                request.to(),
                request.cc(),
                request.subject(),
                request.content(),
                request.html(),
                request.attachments());
    }

    private MailSendRequest withPort(Integer port) {
        MailSendRequest request = sendRequest();
        return new MailSendRequest(
                request.smtpHost(),
                port,
                request.smtpUsername(),
                request.smtpPassword(),
                request.smtpEncryption(),
                request.from(),
                request.to(),
                request.cc(),
                request.subject(),
                request.content(),
                request.html(),
                request.attachments());
    }

    private MailSendRequest sendRequest(MailAttachmentRequest... attachments) {
        return new MailSendRequest(
                "smtp.example.com",
                587,
                "alice@example.com",
                "secret",
                null,
                null,
                List.of("Bob <bob@example.com>"),
                List.of("carol@example.com"),
                "Hello",
                "Body text",
                null,
                List.of(attachments));
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
