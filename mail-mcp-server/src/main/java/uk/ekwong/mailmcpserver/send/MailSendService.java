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

import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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

    public MailSendService(MailTransport transport) {
        this.transport = transport;
    }

    /**
     * Delivers the mail described by the command.
     *
     * @throws IllegalArgumentException when the command has no recipient
     * @throws MailSendFailedException when the SMTP server rejected the mail or could not be
     *     reached
     */
    public MailSendResponse send(MailSendCommand command) {
        if (command.to().isEmpty()) {
            throw new IllegalArgumentException("to must not be empty");
        }
        String messageId = generateMessageId(command);
        MimeMessage message = build(command, messageId);
        try {
            transport.send(command.smtp(), message);
        } catch (MessagingException e) {
            throw new MailSendFailedException(
                    "SMTP send via " + command.smtp() + " failed: " + e.getMessage(), e);
        }
        log.info(
                "Sent mail via {} to={} cc={} attachments={} attachmentBytes={}",
                command.smtp(),
                command.to().size(),
                command.cc().size(),
                command.attachments().size(),
                command.attachmentBytes());
        return new MailSendResponse(
                messageId,
                command.from(),
                command.to(),
                command.cc(),
                command.subject(),
                command.attachments().size(),
                command.attachmentBytes(),
                Instant.now());
    }

    private MimeMessage build(MailSendCommand command, String messageId) {
        MimeMessage message = transport.newMessage(command.smtp());
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
            // Save once before fixing the Message-ID: the mail client generates (and would
            // otherwise overwrite) the header on the first save, and later saves are no-ops.
            message.saveChanges();
            message.setHeader("Message-ID", messageId);
        } catch (MessagingException e) {
            throw new MailSendFailedException(
                    "Could not build the MIME message: " + e.getMessage(), e);
        }
        return message;
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
        part.setDataHandler(
                new DataHandler(
                        new ByteArrayDataSource(attachment.content(), attachment.contentType())));
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

    private InternetAddress[] toAddresses(List<String> addresses) throws MessagingException {
        InternetAddress[] result = new InternetAddress[addresses.size()];
        for (int i = 0; i < addresses.size(); i++) {
            result[i] = new InternetAddress(addresses.get(i), true);
        }
        return result;
    }

    /**
     * Generates the Message-ID from a random token and the sender domain, so replies can reference
     * this mail even when the SMTP server does not add the header itself.
     */
    private String generateMessageId(MailSendCommand command) {
        String domain = domainOf(command.from());
        if (!StringUtils.hasText(domain)) {
            domain = sanitizeDomain(command.smtp().host());
        }
        return "<" + UUID.randomUUID() + "@" + domain + ">";
    }

    private String domainOf(String address) {
        int at = address == null ? -1 : address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return null;
        }
        String domain = address.substring(at + 1).replace(">", "").trim();
        return sanitizeDomain(domain);
    }

    private String sanitizeDomain(String value) {
        String sanitized =
                value == null
                        ? ""
                        : value.replaceAll("[^A-Za-z0-9.-]", "").toLowerCase(Locale.ROOT);
        return sanitized.isEmpty() ? "mail.local" : sanitized;
    }
}
