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

package uk.ekwong.mailcommon.mail;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MailDateFormat;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts email details (sender, from, to, cc, Message-Id, received time,
 * subject, content type and attachment names) from a MIME message.
 */
@Component
public class EmailDetailsExtractor {

    private static final Logger log = LoggerFactory.getLogger(EmailDetailsExtractor.class);

    public EmailDetails extract(MimeMessage message, String envelopeSender) throws MessagingException, IOException {
        return extract(message, envelopeSender, "header");
    }

    /**
     * @param senderResolution how the "sender" field is resolved:
     *                         {@code header} (Sender header, fallback From),
     *                         {@code envelope} (SMTP MAIL FROM) or {@code from}
     */
    public EmailDetails extract(MimeMessage message, String envelopeSender, String senderResolution)
            throws MessagingException, IOException {
        String from = addressToString(first(message.getFrom()));
        String sender = resolveSender(message, envelopeSender, from, senderResolution);
        String to = join(message.getRecipients(Message.RecipientType.TO));
        String cc = join(message.getRecipients(Message.RecipientType.CC));
        String messageId = message.getMessageID();
        Instant receivedTime = parseReceivedTime(message);
        String subject = decodeSubject(message);
        String contentType = contentType(message);
        List<String> attachmentNames = collectAttachmentNames(message);
        return new EmailDetails(sender, from, to, cc, messageId, receivedTime, subject, contentType, attachmentNames);
    }

    private String resolveSender(MimeMessage message, String envelopeSender, String from, String senderResolution)
            throws MessagingException {
        String senderHeader = firstHeader(message, "Sender");
        return switch (senderResolution == null ? "header" : senderResolution.toLowerCase()) {
            case "envelope" -> firstNonBlank(senderHeader, envelopeSender, from);
            case "from" -> from;
            default -> firstNonBlank(senderHeader, from);
        };
    }

    private String firstHeader(MimeMessage message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return values != null && values.length > 0 ? values[0].trim() : null;
    }

    private Address first(Address[] addresses) {
        return addresses != null && addresses.length > 0 ? addresses[0] : null;
    }

    private String join(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        return Arrays.stream(addresses)
                .map(Address::toString)
                .collect(Collectors.joining(", "));
    }

    private String addressToString(Address address) {
        return address == null ? null : address.toString();
    }

    private String decodeSubject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            log.warn("Failed to decode subject", e);
            return null;
        }
    }

    private String contentType(MimeMessage message) {
        try {
            return message.getContentType();
        } catch (MessagingException e) {
            log.warn("Failed to read content type", e);
            return null;
        }
    }

    private Instant parseReceivedTime(MimeMessage message) {
        try {
            String[] dates = message.getHeader("Date");
            if (dates != null && dates.length > 0 && !dates[0].isBlank()) {
                Date date = new MailDateFormat().parse(dates[0].trim());
                return date.toInstant();
            }
        } catch (Exception e) {
            log.debug("Could not parse Date header: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Collects the file names of all attachments. Parts without a file name are
     * ignored; nested multipart parts are walked recursively.
     */
    private List<String> collectAttachmentNames(Part part) throws MessagingException, IOException {
        List<String> names = new ArrayList<>();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String fileName = bodyPart.getFileName();
                if (fileName != null && !fileName.isBlank()) {
                    names.add(MimeUtility.decodeText(fileName.trim()));
                }
                if (bodyPart.isMimeType("multipart/*")) {
                    names.addAll(collectAttachmentNames(bodyPart));
                }
            }
        }
        return names;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
