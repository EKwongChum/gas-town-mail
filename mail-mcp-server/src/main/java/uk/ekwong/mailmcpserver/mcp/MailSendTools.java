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

package uk.ekwong.mailmcpserver.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import uk.ekwong.mailmcpserver.download.OriginalMailNotFoundException;
import uk.ekwong.mailmcpserver.send.MailAttachmentRequest;
import uk.ekwong.mailmcpserver.send.MailCompositionService;
import uk.ekwong.mailmcpserver.send.MailForwardRequest;
import uk.ekwong.mailmcpserver.send.MailReplyRequest;
import uk.ekwong.mailmcpserver.send.MailSendFailedException;
import uk.ekwong.mailmcpserver.send.MailSendRateLimiter;
import uk.ekwong.mailmcpserver.send.MailSendRequest;
import uk.ekwong.mailmcpserver.send.MailSendRequestMapper;
import uk.ekwong.mailmcpserver.send.MailSendService;

/**
 * MCP tools that send mail, mirroring the HTTP endpoints {@code POST /api/mails/send|reply|forward}
 * so an MCP client can send a mail, answer or forward an archived mail found with {@code
 * search_mails} / {@code get_mail_by_id}.
 *
 * <p>Like the HTTP endpoints, the SMTP server and the account are taken from the tool arguments for
 * every call: the credentials are used for that single delivery and are never stored. Sending is a
 * real side effect, so the failure modes of the SMTP server are reported back as tool errors
 * instead of exceptions.
 */
@Component
public class MailSendTools {

    private static final String ATTACHMENTS_DESCRIPTION =
            "Attachments, Base64 encoded: each entry is {filename, contentType, contentBase64}. "
                    + "A single attachment may be at most 10 MB and all attachments together at "
                    + "most 20 MB.";

    private final MailSendRequestMapper requestMapper;
    private final MailSendService sendService;
    private final MailCompositionService compositionService;
    private final MailSendRateLimiter rateLimiter;
    private final McpToolObserver observer;

    public MailSendTools(
            MailSendRequestMapper requestMapper,
            MailSendService sendService,
            MailCompositionService compositionService,
            MailSendRateLimiter rateLimiter,
            McpToolObserver observer) {
        this.requestMapper = requestMapper;
        this.sendService = sendService;
        this.compositionService = compositionService;
        this.rateLimiter = rateLimiter;
        this.observer = observer;
    }

    /** The outbound mail tools exposed by this MCP server. */
    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
        return List.of(sendMailTool(), replyMailTool(), forwardMailTool());
    }

    private McpServerFeatures.SyncToolSpecification sendMailTool() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder()
                        .name("send_mail")
                        .title("Send an email")
                        .description(
                                "Sends a new email through the SMTP server given in the arguments "
                                        + "(smtpHost, smtpPort, smtpUsername, smtpPassword). This delivers a real "
                                        + "message to the recipients, so only call it when the user asked for the mail "
                                        + "to be sent. The SMTP credentials are used for this call only and are never "
                                        + "stored; do not repeat the password back to the user. "
                                        + "Returns a JSON object with the generated messageId and the effective "
                                        + "from/to/cc/subject and attachment counters, or an error describing why the "
                                        + "mail could not be sent.")
                        .inputSchema(McpToolSchemas.jsonSchema(arguments(true, false)))
                        .annotations(sendingAnnotations("Send an email"))
                        .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) ->
                                observer.observed(
                                        "send_mail", () -> send(exchange, request.arguments())))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification replyMailTool() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder()
                        .name("reply_mail")
                        .title("Reply to an archived email")
                        .description(
                                "Replies to an archived email found with search_mails or get_mail_by_id (pass its "
                                        + "id) and delivers the reply through the SMTP server given in the arguments. "
                                        + "Like a mail client, the reply goes to the original Reply-To/From unless to is "
                                        + "given, the subject gets a Re: prefix unless subject is given, the original body "
                                        + "is quoted below content, and the reply keeps the original thread through "
                                        + "In-Reply-To/References. Set replyAll to also copy the original To/Cc "
                                        + "recipients, and includeOriginalAttachments to carry the original "
                                        + "attachments. The SMTP credentials are used for this call only and are never "
                                        + "stored. Returns the messageId plus the effective recipients and subject, or an "
                                        + "error.")
                        .inputSchema(McpToolSchemas.jsonSchema(arguments(false, true)))
                        .annotations(sendingAnnotations("Reply to an archived email"))
                        .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) ->
                                observer.observed(
                                        "reply_mail", () -> reply(exchange, request.arguments())))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification forwardMailTool() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder()
                        .name("forward_mail")
                        .title("Forward an archived email")
                        .description(
                                "Forwards an archived email found with search_mails or get_mail_by_id (pass its id) "
                                        + "to the recipients in to. The subject gets a Fwd: prefix unless subject is "
                                        + "given, the original body is embedded in a forwarded-message block below "
                                        + "content, and the original attachments are carried over unless "
                                        + "includeOriginalAttachments is false. The SMTP credentials are used for this "
                                        + "call only and are never stored. Returns the messageId plus the effective "
                                        + "recipients and subject, or an error.")
                        .inputSchema(McpToolSchemas.jsonSchema(arguments(true, true)))
                        .annotations(sendingAnnotations("Forward an archived email"))
                        .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) ->
                                observer.observed(
                                        "forward_mail",
                                        () -> forward(exchange, request.arguments())))
                .build();
    }

    private McpSchema.CallToolResult send(
            McpSyncServerExchange exchange, Map<String, Object> arguments) {
        McpSchema.CallToolResult limited = rateLimited(exchange);
        if (limited != null) {
            return limited;
        }
        try {
            return observer.ok(sendService.send(requestMapper.toCommand(toSendRequest(arguments))));
        } catch (IllegalArgumentException | MailSendFailedException | IllegalStateException e) {
            return observer.error(e.getMessage());
        }
    }

    private McpSchema.CallToolResult reply(
            McpSyncServerExchange exchange, Map<String, Object> arguments) {
        McpSchema.CallToolResult limited = rateLimited(exchange);
        if (limited != null) {
            return limited;
        }
        try {
            return observer.ok(
                    sendService.send(compositionService.composeReply(toReplyRequest(arguments))));
        } catch (IllegalArgumentException
                | MailSendFailedException
                | OriginalMailNotFoundException
                | IllegalStateException e) {
            return observer.error(e.getMessage());
        }
    }

    private McpSchema.CallToolResult forward(
            McpSyncServerExchange exchange, Map<String, Object> arguments) {
        McpSchema.CallToolResult limited = rateLimited(exchange);
        if (limited != null) {
            return limited;
        }
        try {
            return observer.ok(
                    sendService.send(
                            compositionService.composeForward(toForwardRequest(arguments))));
        } catch (IllegalArgumentException
                | MailSendFailedException
                | OriginalMailNotFoundException
                | IllegalStateException e) {
            return observer.error(e.getMessage());
        }
    }

    /**
     * The properties shared by the three tools. {@code id} is added for reply/forward, and {@code
     * to} is required everywhere except for a reply, which can derive it from the archived mail.
     */
    private List<Map<String, Object>> arguments(boolean toRequired, boolean includeId) {
        List<Map<String, Object>> arguments = new ArrayList<>();
        if (includeId) {
            arguments.add(
                    McpToolSchemas.arg(
                            "id",
                            "Archive id of the mail to answer or forward (from search_mails / get_mail_by_id)",
                            true));
        }
        arguments.addAll(
                List.of(
                        McpToolSchemas.arg("smtpHost", "SMTP server host name or IP address", true),
                        McpToolSchemas.arg(
                                "smtpPort",
                                "integer",
                                "SMTP server port, e.g. 25, 465 or 587",
                                true),
                        McpToolSchemas.arg(
                                "smtpUsername",
                                "SMTP account; omit for a server that does not authenticate",
                                false),
                        McpToolSchemas.arg(
                                "smtpPassword", "SMTP password, required with smtpUsername", false),
                        McpToolSchemas.arg(
                                "smtpEncryption",
                                "none, starttls or ssl; by default port 465 uses implicit TLS and "
                                        + "other ports offer STARTTLS",
                                false),
                        McpToolSchemas.arg(
                                "from",
                                "From address, display name allowed; defaults to smtpUsername",
                                false),
                        McpToolSchemas.arrayArg(
                                "to",
                                "string",
                                "To recipients; omit on a reply to answer the original sender",
                                toRequired),
                        McpToolSchemas.arrayArg("cc", "string", "Cc recipients", false),
                        McpToolSchemas.arg("subject", "Mail subject; UTF-8", false),
                        McpToolSchemas.arg("content", "Mail body; UTF-8", false),
                        McpToolSchemas.arg("html", "boolean", "Whether content is HTML", false),
                        McpToolSchemas.arrayArg(
                                "attachments",
                                McpToolSchemas.objectSchema(
                                        attachmentProperties(),
                                        List.of("filename", "contentBase64")),
                                ATTACHMENTS_DESCRIPTION,
                                false)));
        if (includeId) {
            arguments.add(
                    McpToolSchemas.arg(
                            "includeOriginalBody",
                            "boolean",
                            "Whether to quote (reply) or embed (forward) the original body; "
                                    + "defaults to true",
                            false));
            arguments.add(
                    McpToolSchemas.arg(
                            "includeOriginalAttachments",
                            "boolean",
                            "Whether to carry the original attachments: default false for a reply "
                                    + "(use true to attach them) and true for a forward",
                            false));
        }
        if (!toRequired) {
            arguments.add(
                    McpToolSchemas.arg(
                            "replyAll",
                            "boolean",
                            "Also copy the original To/Cc recipients except the own address",
                            false));
        }
        return arguments;
    }

    private Map<String, Object> attachmentProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("filename", Map.of("type", "string"));
        properties.put("contentType", Map.of("type", "string"));
        properties.put("contentBase64", Map.of("type", "string"));
        return properties;
    }

    private McpSchema.ToolAnnotations sendingAnnotations(String title) {
        return new McpSchema.ToolAnnotations(title, false, false, false, true, null);
    }

    private MailSendRequest toSendRequest(Map<String, Object> arguments) {
        return new MailSendRequest(
                McpToolSchemas.string(arguments, "smtpHost"),
                McpToolSchemas.integer(arguments, "smtpPort"),
                McpToolSchemas.string(arguments, "smtpUsername"),
                McpToolSchemas.string(arguments, "smtpPassword"),
                McpToolSchemas.string(arguments, "smtpEncryption"),
                McpToolSchemas.string(arguments, "from"),
                McpToolSchemas.stringList(arguments, "to"),
                McpToolSchemas.stringList(arguments, "cc"),
                McpToolSchemas.string(arguments, "subject"),
                McpToolSchemas.text(arguments, "content"),
                McpToolSchemas.bool(arguments, "html"),
                toAttachments(arguments));
    }

    private MailReplyRequest toReplyRequest(Map<String, Object> arguments) {
        return new MailReplyRequest(
                McpToolSchemas.string(arguments, "id"),
                McpToolSchemas.string(arguments, "smtpHost"),
                McpToolSchemas.integer(arguments, "smtpPort"),
                McpToolSchemas.string(arguments, "smtpUsername"),
                McpToolSchemas.string(arguments, "smtpPassword"),
                McpToolSchemas.string(arguments, "smtpEncryption"),
                McpToolSchemas.string(arguments, "from"),
                McpToolSchemas.stringList(arguments, "to"),
                McpToolSchemas.stringList(arguments, "cc"),
                McpToolSchemas.string(arguments, "subject"),
                McpToolSchemas.text(arguments, "content"),
                McpToolSchemas.bool(arguments, "html"),
                toAttachments(arguments),
                McpToolSchemas.bool(arguments, "replyAll"),
                McpToolSchemas.bool(arguments, "includeOriginalBody"),
                McpToolSchemas.bool(arguments, "includeOriginalAttachments"));
    }

    private MailForwardRequest toForwardRequest(Map<String, Object> arguments) {
        return new MailForwardRequest(
                McpToolSchemas.string(arguments, "id"),
                McpToolSchemas.string(arguments, "smtpHost"),
                McpToolSchemas.integer(arguments, "smtpPort"),
                McpToolSchemas.string(arguments, "smtpUsername"),
                McpToolSchemas.string(arguments, "smtpPassword"),
                McpToolSchemas.string(arguments, "smtpEncryption"),
                McpToolSchemas.string(arguments, "from"),
                McpToolSchemas.stringList(arguments, "to"),
                McpToolSchemas.stringList(arguments, "cc"),
                McpToolSchemas.string(arguments, "subject"),
                McpToolSchemas.text(arguments, "content"),
                McpToolSchemas.bool(arguments, "html"),
                toAttachments(arguments),
                McpToolSchemas.bool(arguments, "includeOriginalBody"),
                McpToolSchemas.bool(arguments, "includeOriginalAttachments"));
    }

    private List<MailAttachmentRequest> toAttachments(Map<String, Object> arguments) {
        List<Map<String, Object>> entries = McpToolSchemas.objectList(arguments, "attachments");
        if (entries == null) {
            return null;
        }
        List<MailAttachmentRequest> attachments = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            attachments.add(
                    new MailAttachmentRequest(
                            McpToolSchemas.string(entry, "filename"),
                            McpToolSchemas.string(entry, "contentType"),
                            McpToolSchemas.string(entry, "contentBase64")));
        }
        return attachments;
    }

    /**
     * Applies {@code app.send.rate-limit.requests-per-minute} to the sending tools, keyed by the
     * MCP session because tool calls do not expose a client address.
     *
     * @return an error result when the limit is reached, {@code null} when the call may proceed
     */
    private McpSchema.CallToolResult rateLimited(McpSyncServerExchange exchange) {
        String session = exchange == null ? null : exchange.sessionId();
        MailSendRateLimiter.Decision decision =
                rateLimiter.acquire("mcp:" + (session == null ? "anonymous" : session));
        if (decision.allowed()) {
            return null;
        }
        return observer.error(
                "Too many mail tool calls, retry in " + decision.retryAfterSeconds() + " seconds");
    }
}
