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

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts the content a reply or forward needs from the raw {@code .eml} bytes of an archived
 * mail: header values, the plain text body (HTML is reduced to text) and all attachments.
 */
@Component
public class OriginalEmailContentParser {

    private static final Logger log = LoggerFactory.getLogger(OriginalEmailContentParser.class);

    private static final Pattern HTML_BREAK = Pattern.compile("(?i)<\\s*br\\s*/?\\s*>");
    private static final Pattern HTML_PARAGRAPH =
            Pattern.compile("(?i)</\\s*(p|div|tr|li|h[1-6])\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern HTML_WHITESPACE = Pattern.compile("[ \t]+\n");

    /**
     * Parses the raw original email: headers and body, plus the attachment names. The attachment
     * bytes are only read by {@link #readAttachments(byte[])}, so a reply that does not carry the
     * original attachments never loads them.
     *
     * @throws IllegalArgumentException when the bytes are not a readable MIME message
     */
    public OriginalEmailContent parse(byte[] raw) {
        MimeMessage message = read(raw);
        BodyCollector collector = new BodyCollector();
        try {
            collect(message, collector);
        } catch (MessagingException | IOException e) {
            throw new IllegalArgumentException(
                    "could not read the archived original email: " + e.getMessage());
        }

        return new OriginalEmailContent(
                firstAddress(message),
                header(message, "Reply-To"),
                addressList(message, Message.RecipientType.TO),
                addressList(message, Message.RecipientType.CC),
                subject(message),
                header(message, "Message-Id"),
                headerValues(message, "References"),
                sentDate(message),
                collector.body(),
                collector.attachmentNames());
    }

    /**
     * Reads the attachment bytes of the archived original email, e.g. to carry them over on a
     * forward.
     *
     * @throws IllegalArgumentException when the bytes are not a readable MIME message
     */
    public List<MailAttachment> readAttachments(byte[] raw) {
        MimeMessage message = read(raw);
        AttachmentCollector collector = new AttachmentCollector();
        try {
            collectAttachments(message, collector);
        } catch (MessagingException | IOException e) {
            throw new IllegalArgumentException(
                    "could not read the attachments of the archived original email: "
                            + e.getMessage());
        }
        return collector.attachments();
    }

    private MimeMessage read(byte[] raw) {
        try {
            return new MimeMessage(
                    Session.getInstance(new Properties()),
                    new ByteArrayInputStream(raw == null ? new byte[0] : raw));
        } catch (MessagingException e) {
            throw new IllegalArgumentException(
                    "archived original email is not a readable MIME message: " + e.getMessage());
        }
    }

    private void collect(Part part, BodyCollector collector)
            throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), collector);
            }
            return;
        }
        String filename = filename(part);
        if (filename != null) {
            collector.attachment(filename);
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            // an embedded mail without a file name: not something we can quote or attach
            log.debug("Skipping embedded message/rfc822 part without a file name");
            return;
        }
        if (part.isMimeType("text/plain")) {
            collector.plain(text(part));
        } else if (part.isMimeType("text/html")) {
            collector.html(text(part));
        }
    }

    private void collectAttachments(Part part, AttachmentCollector collector)
            throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collectAttachments(multipart.getBodyPart(i), collector);
            }
            return;
        }
        String filename = filename(part);
        if (filename != null) {
            collector.attachment(filename, part);
        }
    }

    private String filename(Part part) throws MessagingException {
        String filename = part.getFileName();
        if (filename == null || filename.isBlank()) {
            return null;
        }
        return decodeText(filename.trim());
    }

    private String text(Part part) throws MessagingException, IOException {
        Object content = part.getContent();
        return content instanceof String string ? string : "";
    }

    private String firstAddress(MimeMessage message) {
        try {
            return InternetAddress.toString(message.getFrom());
        } catch (MessagingException e) {
            log.warn("Could not read the From header: {}", e.getMessage());
            return null;
        }
    }

    private List<String> addressList(MimeMessage message, Message.RecipientType type) {
        try {
            Address[] addresses = message.getRecipients(type);
            if (addresses == null || addresses.length == 0) {
                return List.of();
            }
            return Arrays.stream(addresses).map(Address::toString).toList();
        } catch (MessagingException e) {
            log.warn("Could not read the {} recipients: {}", type, e.getMessage());
            return List.of();
        }
    }

    private String subject(MimeMessage message) {
        try {
            String subject = message.getSubject();
            return subject == null ? null : decodeText(subject);
        } catch (MessagingException e) {
            log.warn("Could not read the subject: {}", e.getMessage());
            return null;
        }
    }

    private Instant sentDate(MimeMessage message) {
        try {
            Date date = message.getSentDate();
            return date == null ? null : date.toInstant();
        } catch (MessagingException e) {
            log.warn("Could not read the Date header: {}", e.getMessage());
            return null;
        }
    }

    private String header(MimeMessage message, String name) {
        try {
            String[] values = message.getHeader(name);
            return values == null || values.length == 0 ? null : values[0].trim();
        } catch (MessagingException e) {
            log.warn("Could not read the {} header: {}", name, e.getMessage());
            return null;
        }
    }

    private List<String> headerValues(MimeMessage message, String name) {
        try {
            String joined = message.getHeader(name, " ");
            if (joined == null || joined.isBlank()) {
                return List.of();
            }
            return Arrays.stream(joined.trim().split("\\s+")).filter(v -> !v.isEmpty()).toList();
        } catch (MessagingException e) {
            log.warn("Could not read the {} header: {}", name, e.getMessage());
            return List.of();
        }
    }

    private String decodeText(String value) {
        try {
            return MimeUtility.decodeText(value);
        } catch (UnsupportedEncodingException e) {
            log.debug("Could not decode a MIME encoded header: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Reduces an HTML body to readable text, used when the original mail has no plain text part.
     */
    static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text =
                HTML_BREAK.matcher(html).replaceAll("\n").replaceAll("(?i)</\\s*p\\s*>", "\n\n");
        text = HTML_PARAGRAPH.matcher(text).replaceAll("\n");
        text = HTML_TAG.matcher(text).replaceAll("");
        text =
                text.replace("&nbsp;", " ")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&amp;", "&");
        return HTML_WHITESPACE.matcher(text).replaceAll("\n").trim();
    }

    /**
     * Collects the first plain text body (HTML is only used as a fallback) and the attachment
     * names.
     */
    private static final class BodyCollector {

        private String plain;
        private String html;
        private final List<String> attachmentNames = new ArrayList<>();

        void plain(String value) {
            if (plain == null) {
                plain = value;
            }
        }

        void html(String value) {
            if (html == null) {
                html = value;
            }
        }

        void attachment(String filename) {
            attachmentNames.add(filename);
        }

        String body() {
            if (plain != null) {
                return plain;
            }
            return html == null ? "" : htmlToText(html);
        }

        List<String> attachmentNames() {
            return List.copyOf(attachmentNames);
        }
    }

    /** Reads the attachment bytes of an archived mail; used when they are carried over. */
    private static final class AttachmentCollector {

        private final List<MailAttachment> attachments = new ArrayList<>();

        void attachment(String filename, Part part) throws MessagingException, IOException {
            try (InputStream in = part.getInputStream()) {
                attachments.add(MailAttachment.of(filename, contentType(part), in.readAllBytes()));
            }
        }

        List<MailAttachment> attachments() {
            return List.copyOf(attachments);
        }

        private String contentType(Part part) {
            try {
                String contentType = part.getContentType();
                if (contentType == null) {
                    return MailSendRequestMapper.DEFAULT_CONTENT_TYPE;
                }
                int separator = contentType.indexOf(';');
                return (separator < 0 ? contentType : contentType.substring(0, separator)).trim();
            } catch (MessagingException e) {
                return MailSendRequestMapper.DEFAULT_CONTENT_TYPE;
            }
        }
    }
}
