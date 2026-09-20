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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.ekwong.mailcommon.storage.ObjectStorageService;
import uk.ekwong.mailmcpserver.download.OriginalMailNotFoundException;

/**
 * Builds the outgoing mail for the reply and forward endpoints from an archived original email
 * (identified by the archive id returned by the MCP query tools) and the send parameters of the
 * request.
 *
 * <p>The behaviour follows what a mail client does:
 *
 * <ul>
 *   <li><b>reply</b>: the original {@code Reply-To} (or {@code From}) becomes the recipient, the
 *       subject gets a {@code Re:} prefix unless the caller supplied one, the original body is
 *       quoted below the new text and {@code In-Reply-To}/{@code References} keep the reply in the
 *       original thread. <code>replyAll</code> also copies the original To/Cc recipients except the
 *       own address.
 *   <li><b>forward</b>: recipients must be supplied by the caller, the original body is embedded
 *       after a forwarded-message header block, the original attachments are carried over by
 *       default and no threading headers are set, so a new thread starts.
 * </ul>
 */
@Service
public class MailCompositionService {

    private static final Logger log = LoggerFactory.getLogger(MailCompositionService.class);

    private static final Pattern REPLY_PREFIX =
            Pattern.compile("^\\s*re\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORWARD_PREFIX =
            Pattern.compile("^\\s*(fwd?|fw)\\s*:", Pattern.CASE_INSENSITIVE);
    private static final String FORWARD_SEPARATOR = "---------- Forwarded message ---------";

    private final MailSendRequestMapper requestMapper;
    private final ObjectStorageService objectStorageService;
    private final OriginalEmailContentParser contentParser;

    public MailCompositionService(
            MailSendRequestMapper requestMapper,
            ObjectStorageService objectStorageService,
            OriginalEmailContentParser contentParser) {
        this.requestMapper = requestMapper;
        this.objectStorageService = objectStorageService;
        this.contentParser = contentParser;
    }

    /** Composes the reply to an archived mail. */
    public MailSendCommand composeReply(MailReplyRequest request) {
        MailSendCommand base = requestMapper.toCommand(request);
        OriginalEmailContent original = readOriginal(request.id());

        List<String> to = base.to().isEmpty() ? replyRecipients(original) : base.to();
        List<String> cc =
                Boolean.TRUE.equals(request.replyAll())
                        ? replyAllCc(base, original, to)
                        : base.cc();
        String subject = defaultSubject(base.subject(), original.subject(), REPLY_PREFIX, "Re:");
        String body =
                Boolean.FALSE.equals(request.includeOriginalBody())
                        ? base.body()
                        : replyBody(base, original);
        List<MailAttachment> attachments =
                mergeAttachments(base, original, request.includeOriginalAttachments(), false);

        log.info(
                "Composed reply to archived mail id={} to={} cc={} attachments={}",
                request.id(),
                to.size(),
                cc.size(),
                attachments.size());
        return base.withRecipients(to, cc)
                .withSubject(subject)
                .withBody(body)
                .withAttachments(attachments)
                .withThreading(original.messageId(), replyReferences(original));
    }

    /** Composes the forward of an archived mail. */
    public MailSendCommand composeForward(MailForwardRequest request) {
        MailSendCommand base = requestMapper.toCommand(request);
        if (base.to().isEmpty()) {
            throw new IllegalArgumentException("to must not be empty when forwarding");
        }
        OriginalEmailContent original = readOriginal(request.id());

        String subject = defaultSubject(base.subject(), original.subject(), FORWARD_PREFIX, "Fwd:");
        String body =
                Boolean.FALSE.equals(request.includeOriginalBody())
                        ? base.body()
                        : forwardBody(base, original);
        List<MailAttachment> attachments =
                mergeAttachments(base, original, request.includeOriginalAttachments(), true);

        log.info(
                "Composed forward of archived mail id={} to={} cc={} attachments={}",
                request.id(),
                base.to().size(),
                base.cc().size(),
                attachments.size());
        return base.withSubject(subject).withBody(body).withAttachments(attachments);
    }

    /**
     * Reads the archived original email from object storage. The id is the one returned by the MCP
     * tools, i.e. the MongoDB {@code _id} and object storage key of the archived mail.
     */
    private OriginalEmailContent readOriginal(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        String archiveId = id.trim();
        byte[] raw =
                objectStorageService
                        .readIfPresent(archiveId)
                        .orElseThrow(() -> new OriginalMailNotFoundException(List.of(archiveId)));
        return contentParser.parse(raw);
    }

    private List<String> replyRecipients(OriginalEmailContent original) {
        String replyTo =
                StringUtils.hasText(original.replyTo()) ? original.replyTo() : original.from();
        List<String> recipients =
                MailAddressParser.parseList(replyTo == null ? List.of() : List.of(replyTo), "to");
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(
                    "cannot determine the reply recipient from the archived mail; provide to");
        }
        return recipients;
    }

    private List<String> replyAllCc(
            MailSendCommand base, OriginalEmailContent original, List<String> to) {
        Set<String> blocked = new HashSet<>();
        blocked.addAll(MailAddressParser.bareAddresses(List.of(base.from()), "from"));
        blocked.addAll(MailAddressParser.bareAddresses(to, "to"));
        List<String> cc = new ArrayList<>(base.cc());
        blocked.addAll(MailAddressParser.bareAddresses(cc, "cc"));

        List<String> candidates = new ArrayList<>(original.to());
        candidates.addAll(original.cc());
        for (String candidate : MailAddressParser.parseList(candidates, "cc")) {
            if (blocked.add(MailAddressParser.bareAddress(candidate, "cc"))) {
                cc.add(candidate);
            }
        }
        return List.copyOf(cc);
    }

    private List<String> replyReferences(OriginalEmailContent original) {
        List<String> references = new ArrayList<>(original.references());
        if (StringUtils.hasText(original.messageId())
                && !references.contains(original.messageId())) {
            references.add(original.messageId());
        }
        return List.copyOf(references);
    }

    private List<MailAttachment> mergeAttachments(
            MailSendCommand base,
            OriginalEmailContent original,
            Boolean includeOriginal,
            boolean defaultInclude) {
        boolean include = includeOriginal == null ? defaultInclude : includeOriginal;
        if (!include || original.attachments().isEmpty()) {
            return base.attachments();
        }
        List<MailAttachment> merged = new ArrayList<>(base.attachments());
        merged.addAll(original.attachments());
        try {
            requestMapper.validateAttachmentLimits(merged);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    e.getMessage()
                            + "; set includeOriginalAttachments=false to send without the original"
                            + " attachments");
        }
        return List.copyOf(merged);
    }

    private String defaultSubject(
            String requested, String original, Pattern existingPrefix, String prefix) {
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        if (!StringUtils.hasText(original)) {
            return "";
        }
        String subject = original.trim();
        return existingPrefix.matcher(subject).find() ? subject : prefix + " " + subject;
    }

    private String replyBody(MailSendCommand base, OriginalEmailContent original) {
        return base.html() ? htmlReply(base.body(), original) : textReply(base.body(), original);
    }

    private String textReply(String body, OriginalEmailContent original) {
        StringBuilder text = new StringBuilder(body);
        if (!body.isEmpty()) {
            text.append("\n\n");
        }
        text.append(replyAttribution(original)).append('\n');
        String quoted = quote(original.textBody());
        if (!quoted.isEmpty()) {
            text.append(quoted);
        }
        return text.toString();
    }

    private String replyAttribution(OriginalEmailContent original) {
        String from =
                StringUtils.hasText(original.from()) ? original.from() : "the archived sender";
        return original.date() == null
                ? from + " wrote:"
                : "On " + original.date() + ", " + from + " wrote:";
    }

    private String quote(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.lines()
                .map(line -> line.isEmpty() ? ">" : "> " + line)
                .collect(Collectors.joining("\n"));
    }

    private String forwardBody(MailSendCommand base, OriginalEmailContent original) {
        return base.html()
                ? htmlForward(base.body(), original)
                : textForward(base.body(), original);
    }

    private String textForward(String body, OriginalEmailContent original) {
        StringBuilder text = new StringBuilder(body);
        if (!body.isEmpty()) {
            text.append("\n\n");
        }
        text.append(FORWARD_SEPARATOR).append('\n');
        appendHeader(text, "From", original.from());
        appendHeader(text, "Date", original.date() == null ? null : original.date().toString());
        appendHeader(text, "Subject", original.subject());
        appendHeader(text, "To", join(original.to()));
        appendHeader(text, "Cc", join(original.cc()));
        if (!original.textBody().isBlank()) {
            text.append('\n').append(original.textBody());
        }
        return text.toString();
    }

    private String htmlReply(String body, OriginalEmailContent original) {
        StringBuilder html = new StringBuilder(body);
        if (!body.isEmpty()) {
            html.append("<br>");
        }
        html.append("<blockquote>").append(escapeHtml(replyAttribution(original)));
        if (!original.textBody().isBlank()) {
            html.append("<br>").append(escapeHtml(original.textBody()));
        }
        return html.append("</blockquote>").toString();
    }

    private String htmlForward(String body, OriginalEmailContent original) {
        StringBuilder html = new StringBuilder(body);
        if (!body.isEmpty()) {
            html.append("<br><br>");
        }
        html.append("<div>").append(escapeHtml(FORWARD_SEPARATOR)).append("</div>");
        appendHtmlHeader(html, "From", original.from());
        appendHtmlHeader(html, "Date", original.date() == null ? null : original.date().toString());
        appendHtmlHeader(html, "Subject", original.subject());
        appendHtmlHeader(html, "To", join(original.to()));
        appendHtmlHeader(html, "Cc", join(original.cc()));
        if (!original.textBody().isBlank()) {
            html.append("<blockquote>")
                    .append(escapeHtml(original.textBody()))
                    .append("</blockquote>");
        }
        return html.toString();
    }

    private void appendHeader(StringBuilder text, String name, String value) {
        if (StringUtils.hasText(value)) {
            text.append(name).append(": ").append(value.trim()).append('\n');
        }
    }

    private void appendHtmlHeader(StringBuilder html, String name, String value) {
        if (StringUtils.hasText(value)) {
            html.append("<div>").append(escapeHtml(name + ": " + value.trim())).append("</div>");
        }
    }

    private String join(List<String> values) {
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }
}
