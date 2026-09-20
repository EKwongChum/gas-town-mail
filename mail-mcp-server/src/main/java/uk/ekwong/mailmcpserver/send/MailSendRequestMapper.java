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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns a request into a validated {@link MailSendCommand}: SMTP coordinates, addresses and
 * attachment limits are checked here, so the send, reply and forward endpoints share one set of
 * rules and produce the same error messages.
 */
@Component
public class MailSendRequestMapper {

    static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MailSendProperties properties;

    public MailSendRequestMapper(MailSendProperties properties) {
        this.properties = properties;
    }

    /** Validates the send parameters and decodes the attachments of the request. */
    public MailSendCommand toCommand(MailSendFields request) {
        return toCommand(
                request, decodeAttachments(request == null ? null : request.attachments()));
    }

    /**
     * Validates the send parameters with attachments that are already materialized, e.g. decoded
     * from Base64 or read from multipart file parts.
     */
    public MailSendCommand toCommand(MailSendFields request, List<MailAttachment> attachments) {
        if (request == null) {
            throw new IllegalArgumentException("request body must not be null");
        }
        SmtpSettings smtp = smtpSettings(request);
        validateAttachmentLimits(attachments);
        return new MailSendCommand(
                smtp,
                from(request.from(), smtp),
                MailAddressParser.parseList(request.to(), "to"),
                MailAddressParser.parseList(request.cc(), "cc"),
                trimToEmpty(request.subject()),
                request.content() == null ? "" : request.content(),
                Boolean.TRUE.equals(request.html()),
                attachments,
                null,
                List.of());
    }

    /**
     * Builds a command for a multipart request: attachments are uploaded as file parts, so the
     * Base64 {@code attachments} field of the JSON part must stay empty.
     */
    public MailSendCommand toMultipartCommand(MailSendFields request, List<MultipartFile> files) {
        if (request != null && request.attachments() != null && !request.attachments().isEmpty()) {
            throw new IllegalArgumentException(
                    "attachments must be uploaded as multipart file parts, not as Base64 content");
        }
        return toCommand(request, toAttachments(files));
    }

    /**
     * Reads the attachments uploaded as multipart file parts. Oversized uploads are rejected from
     * their reported size before they are read into memory.
     */
    public List<MailAttachment> toAttachments(List<MultipartFile> files) {
        List<MailAttachment> attachments = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return attachments;
        }
        for (MultipartFile file : files) {
            if (file == null) {
                continue;
            }
            String filename = sanitizeFilename(file.getOriginalFilename());
            if (file.isEmpty()) {
                throw new IllegalArgumentException("attachment '" + filename + "' has no content");
            }
            if (file.getSize() > properties.getMaxAttachmentSize().toBytes()) {
                throw attachmentTooLarge(filename, file.getSize());
            }
            attachments.add(new MailAttachment(filename, contentTypeOf(file), read(file)));
        }
        validateAttachmentLimits(attachments);
        return attachments;
    }

    /** Checks the per-attachment and total attachment limits, e.g. after forwarding attachments. */
    public void validateAttachmentLimits(List<MailAttachment> attachments) {
        List<MailAttachment> all = attachments == null ? List.of() : attachments;
        for (MailAttachment attachment : all) {
            if (attachment.size() > properties.getMaxAttachmentSize().toBytes()) {
                throw attachmentTooLarge(attachment.filename(), attachment.size());
            }
        }
        long total = all.stream().mapToLong(MailAttachment::size).sum();
        if (total > properties.getMaxTotalAttachmentSize().toBytes()) {
            throw totalTooLarge(total);
        }
    }

    private IllegalArgumentException attachmentTooLarge(String filename, long bytes) {
        return new IllegalArgumentException(
                "attachment '"
                        + filename
                        + "' is "
                        + describe(DataSize.ofBytes(bytes))
                        + ", exceeding the per-attachment limit of "
                        + describe(properties.getMaxAttachmentSize()));
    }

    private IllegalArgumentException totalTooLarge(long bytes) {
        return new IllegalArgumentException(
                "total attachment size "
                        + describe(DataSize.ofBytes(bytes))
                        + " exceeds the limit of "
                        + describe(properties.getMaxTotalAttachmentSize()));
    }

    private SmtpSettings smtpSettings(MailSendFields request) {
        if (!StringUtils.hasText(request.smtpHost())) {
            throw new IllegalArgumentException("smtpHost must not be blank");
        }
        Integer port = request.smtpPort();
        if (port == null) {
            throw new IllegalArgumentException("smtpPort must not be null");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("smtpPort must be between 1 and 65535");
        }
        String username = trimToNull(request.smtpUsername());
        String password = request.smtpPassword();
        if (username != null && !StringUtils.hasText(password)) {
            throw new IllegalArgumentException(
                    "smtpPassword must not be blank when smtpUsername is set");
        }
        return new SmtpSettings(
                request.smtpHost().trim(), port, username, password, encryption(request));
    }

    private SmtpEncryption encryption(MailSendFields request) {
        String value = trimToNull(request.smtpEncryption());
        if (value == null) {
            return SmtpEncryption.AUTO;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none" -> SmtpEncryption.NONE;
            case "starttls" -> SmtpEncryption.STARTTLS;
            case "ssl" -> SmtpEncryption.SSL;
            default ->
                    throw new IllegalArgumentException(
                            "smtpEncryption must be one of none, starttls, ssl");
        };
    }

    private String from(String from, SmtpSettings smtp) {
        String value = trimToNull(from);
        if (value == null) {
            value = smtp.username();
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "from must not be blank when smtpUsername is not set");
        }
        return MailAddressParser.parseSingle(value, "from");
    }

    private List<MailAttachment> decodeAttachments(List<MailAttachmentRequest> requests) {
        List<MailAttachment> attachments = new ArrayList<>();
        if (requests == null) {
            return attachments;
        }
        for (MailAttachmentRequest request : requests) {
            if (request == null) {
                continue;
            }
            String filename = sanitizeFilename(request.filename());
            String encoded = trimToNull(request.contentBase64());
            if (encoded == null) {
                throw new IllegalArgumentException("attachment '" + filename + "' has no content");
            }
            byte[] content;
            try {
                content = Base64.getMimeDecoder().decode(encoded);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "attachment '" + filename + "' content is not valid Base64");
            }
            String contentType =
                    StringUtils.hasText(request.contentType())
                            ? request.contentType().trim()
                            : DEFAULT_CONTENT_TYPE;
            attachments.add(new MailAttachment(filename, contentType, content));
        }
        return attachments;
    }

    private String contentTypeOf(MultipartFile file) {
        return StringUtils.hasText(file.getContentType())
                ? file.getContentType().trim()
                : DEFAULT_CONTENT_TYPE;
    }

    private byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "could not read the uploaded attachment '" + file.getOriginalFilename() + "'",
                    e);
        }
    }

    /**
     * Normalizes an attachment file name: any directory part a client sent is dropped and control
     * characters are removed, so the name cannot break out of the MIME header it is written to.
     */
    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("attachment filename must not be blank");
        }
        String name = StringUtils.getFilename(filename.trim().replace('\\', '/'));
        name = name == null ? "" : name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("attachment filename must not be blank");
        }
        return name;
    }

    private String describe(DataSize size) {
        long bytes = size.toBytes();
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
