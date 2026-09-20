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

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Builds the MIME message of an outgoing mail and hands it to the SMTP transport. The body is sent
 * as UTF-8 text (optionally HTML), attachments are sent as {@code attachment} parts with their
 * encoded file names, and the generated {@code Message-ID} is returned so the mail can be
 * correlated with later replies.
 */
@Service
public class MailSendService {

    private static final Logger log = LoggerFactory.getLogger(MailSendService.class);

    private final MailTransport transport;
    private final MeterRegistry meterRegistry;

    public MailSendService(MailTransport transport, MeterRegistry meterRegistry) {
        this.transport = transport;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Delivers the mail described by the command.
     *
     * @throws IllegalArgumentException when the command has no recipient
     * @throws MailSendFailedException when the SMTP server rejected the mail or could not be
     *     reached
     * @throws IllegalStateException when the MIME message itself cannot be built
     */
    public MailSendResponse send(MailSendCommand command) {
        if (command.to().isEmpty()) {
            throw new IllegalArgumentException("to must not be empty");
        }
        long startedNanos = System.nanoTime();
        MimeMessage message = build(command);
        try {
            transport.send(command, message);
        } catch (MessagingException e) {
            record("failure", startedNanos, command);
            throw new MailSendFailedException(
                    "SMTP send via " + command.smtp() + " failed: " + e.getMessage(), e);
        }
        record("success", startedNanos, command);
        log.info(
                "Sent mail via {} to={} cc={} attachments={} attachmentBytes={}",
                command.smtp(),
                command.to().size(),
                command.cc().size(),
                command.attachments().size(),
                command.attachmentBytes());
        return new MailSendResponse(
                command.messageId(),
                command.from(),
                command.to(),
                command.cc(),
                command.subject(),
                command.attachments().size(),
                command.attachmentBytes(),
                command.envelopeFrom(),
                Instant.now());
    }

    private MimeMessage build(MailSendCommand command) {
        MimeMessage message = transport.newMessage(command);
        try {
            message.setFrom(new InternetAddress(command.from(), true));
            message.setRecipients(Message.RecipientType.TO, toAddresses(command.to()));
            if (!command.cc().isEmpty()) {
                message.setRecipients(Message.RecipientType.CC, toAddresses(command.cc()));
            }
            message.setSubject(command.subject(), StandardCharsets.UTF_8.name());
            if (StringUtils.hasText(command.inReplyTo())) {
                message.setHeader("In-Reply-To", command.inReplyTo());
            }
            if (!command.references().isEmpty()) {
                message.setHeader("References", String.join(" ", command.references()));
            }
            message.setSentDate(new Date());
            setContent(message, command);
            // The transport creates an OutgoingMimeMessage, which keeps the Message-ID of the
            // command instead of generating its own.
            message.saveChanges();
        } catch (MessagingException e) {
            // building the message is our own failure, not an error of the SMTP server
            throw new IllegalStateException(
                    "Could not build the MIME message: " + e.getMessage(), e);
        }
        return message;
    }

    private void record(String outcome, long startedNanos, MailSendCommand command) {
        meterRegistry.counter("mail.send.attempts", "outcome", outcome).increment();
        meterRegistry
                .timer("mail.send.duration", "outcome", outcome)
                .record(Duration.ofNanos(System.nanoTime() - startedNanos));
        if ("success".equals(outcome)) {
            meterRegistry.counter("mail.send.attachments").increment(command.attachments().size());
            meterRegistry
                    .counter("mail.send.attachment.bytes")
                    .increment(command.attachmentBytes());
        }
    }

    private void setContent(MimeMessage message, MailSendCommand command)
            throws MessagingException {
        String subtype = command.html() ? "html" : "plain";
        if (command.attachments().isEmpty()) {
            message.setText(command.body(), StandardCharsets.UTF_8.name(), subtype);
            return;
        }
        MimeMultipart multipart = new MimeMultipart("mixed");
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(command.body(), StandardCharsets.UTF_8.name(), subtype);
        multipart.addBodyPart(bodyPart);
        for (MailAttachment attachment : command.attachments()) {
            multipart.addBodyPart(attachmentPart(attachment));
        }
        message.setContent(multipart);
    }

    private MimeBodyPart attachmentPart(MailAttachment attachment) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new AttachmentDataSource(attachment)));
        try {
            part.setFileName(
                    MimeUtility.encodeText(
                            attachment.filename(), StandardCharsets.UTF_8.name(), null));
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Unsupported attachment file name encoding", e);
        }
        part.setDisposition(Part.ATTACHMENT);
        return part;
    }

    /**
     * Reads the attachment from its source while the message is written, so a large uploaded file
     * is not buffered in memory a second time.
     */
    private record AttachmentDataSource(MailAttachment attachment) implements DataSource {

        @Override
        public InputStream getInputStream() throws IOException {
            return attachment.content().open();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("attachments are read-only");
        }

        @Override
        public String getContentType() {
            return attachment.contentType();
        }

        @Override
        public String getName() {
            return attachment.filename();
        }
    }

    private InternetAddress[] toAddresses(List<String> addresses) throws MessagingException {
        InternetAddress[] result = new InternetAddress[addresses.size()];
        for (int i = 0; i < addresses.size(); i++) {
            result[i] = new InternetAddress(addresses.get(i), true);
        }
        return result;
    }
}
