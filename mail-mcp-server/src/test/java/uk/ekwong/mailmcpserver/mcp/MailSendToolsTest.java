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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ekwong.mailcommon.storage.ObjectStorageService;
import uk.ekwong.mailmcpserver.send.CapturingMailTransport;
import uk.ekwong.mailmcpserver.send.MailCompositionService;
import uk.ekwong.mailmcpserver.send.MailSendProperties;
import uk.ekwong.mailmcpserver.send.MailSendRateLimiter;
import uk.ekwong.mailmcpserver.send.MailSendRequestMapper;
import uk.ekwong.mailmcpserver.send.MailSendService;
import uk.ekwong.mailmcpserver.send.OriginalEmailContentParser;
import uk.ekwong.mailmcpserver.send.TestOriginalEmails;

class MailSendToolsTest {

    private static final String ARCHIVE_ID =
            "YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=";

    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final CapturingMailTransport transport = new CapturingMailTransport();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MailSendProperties properties = new MailSendProperties();

    private MailSendTools tools;

    @BeforeEach
    void setUp() {
        MailSendRequestMapper requestMapper = new MailSendRequestMapper(properties);
        tools =
                new MailSendTools(
                        requestMapper,
                        new MailSendService(transport, new SimpleMeterRegistry()),
                        new MailCompositionService(
                                requestMapper, storage, new OriginalEmailContentParser()),
                        new MailSendRateLimiter(properties),
                        new McpToolObserver(
                                new ObjectMapper()
                                        .registerModule(new JavaTimeModule())
                                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                                meterRegistry));
    }

    @Test
    void limitsToolCallsPerSession() {
        properties.getRateLimit().setRequestsPerMinute(1);
        McpSyncServerExchange session = mock(McpSyncServerExchange.class);
        when(session.sessionId()).thenReturn("session-1");
        Map<String, Object> arguments =
                Map.of(
                        "smtpHost", "smtp.example.com",
                        "smtpPort", 587,
                        "smtpUsername", "alice@example.com",
                        "smtpPassword", "secret",
                        "to", List.of("bob@example.com"),
                        "subject", "Subject");

        assertThat(callTool(0, "send_mail", arguments, session).isError()).isFalse();

        McpSchema.CallToolResult limited = callTool(0, "send_mail", arguments, session);
        assertThat(limited.isError()).isTrue();
        assertThat(text(limited)).contains("Too many mail tool calls");

        McpSyncServerExchange other = mock(McpSyncServerExchange.class);
        when(other.sessionId()).thenReturn("session-2");
        assertThat(callTool(0, "send_mail", arguments, other).isError()).isFalse();
    }

    @Test
    void registersSendReplyAndForwardTools() {
        List<McpServerFeatures.SyncToolSpecification> specs = tools.toolSpecifications();

        assertThat(specs)
                .extracting(specification -> specification.tool().name())
                .containsExactly("send_mail", "reply_mail", "forward_mail");

        McpSchema.Tool sendMail = specs.get(0).tool();
        assertThat(sendMail.title()).isEqualTo("Send an email");
        assertThat(sendMail.description()).contains("real").contains("never stored");
        assertThat(sendMail.inputSchema().type()).isEqualTo("object");
        assertThat(sendMail.inputSchema().required()).containsExactly("smtpHost", "smtpPort", "to");
        assertThat(sendMail.inputSchema().properties())
                .containsKeys(
                        "smtpHost",
                        "smtpPort",
                        "smtpUsername",
                        "smtpPassword",
                        "smtpEncryption",
                        "from",
                        "to",
                        "cc",
                        "subject",
                        "content",
                        "html",
                        "attachments");
        assertThat(sendMail.annotations()).isNotNull();
        assertThat(sendMail.annotations().readOnlyHint()).isFalse();
        assertThat(sendMail.annotations().openWorldHint()).isTrue();

        assertThat(specs.get(1).tool().inputSchema().required())
                .containsExactly("id", "smtpHost", "smtpPort");
        assertThat(specs.get(1).tool().inputSchema().properties()).containsKey("replyAll");
        assertThat(specs.get(2).tool().inputSchema().required())
                .containsExactly("id", "smtpHost", "smtpPort", "to");
        assertThat(specs.get(2).tool().inputSchema().properties())
                .containsKey("includeOriginalAttachments");
    }

    @Test
    void sendMailDeliversTheMailAndReturnsTheEffectiveValues() throws Exception {
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("filename", "note.txt");
        attachment.put("contentType", "text/plain");
        attachment.put("contentBase64", base64("attachment-data"));

        McpSchema.CallToolResult result =
                callTool(
                        0,
                        "send_mail",
                        Map.of(
                                "smtpHost", "smtp.example.com",
                                "smtpPort", 587,
                                "smtpUsername", "alice@example.com",
                                "smtpPassword", "secret",
                                "to", List.of("bob@example.com"),
                                "cc", List.of("carol@example.com"),
                                "subject", "季度报告",
                                "content", "Body text",
                                "attachments", List.of(attachment)));

        assertThat(result.isError()).isFalse();
        assertThat(text(result))
                .contains("\"subject\":\"季度报告\"")
                .contains("\"to\":[\"bob@example.com\"]")
                .contains("\"cc\":[\"carol@example.com\"]")
                .contains("\"attachmentCount\":1")
                .contains("\"attachmentBytes\":15")
                .contains("\"messageId\":\"<");

        MimeMessage sent = transport.lastMessage();
        assertThat(sent.getSubject()).isEqualTo("季度报告");
        assertThat(sent.getRecipients(MimeMessage.RecipientType.TO)[0].toString())
                .isEqualTo("bob@example.com");
        Multipart multipart = (Multipart) sent.getContent();
        assertThat(multipart.getBodyPart(1).getFileName()).isEqualTo("note.txt");
    }

    @Test
    void sendMailAcceptsACommaSeparatedRecipientString() throws Exception {
        McpSchema.CallToolResult result =
                callTool(
                        0,
                        "send_mail",
                        Map.of(
                                "smtpHost", "smtp.example.com",
                                "smtpPort", 587,
                                "smtpUsername", "alice@example.com",
                                "smtpPassword", "secret",
                                "to", "bob@example.com, carol@example.com",
                                "subject", "Subject"));

        assertThat(result.isError()).isFalse();
        assertThat(transport.lastMessage().getAllRecipients())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("bob@example.com", "carol@example.com");
    }

    @Test
    void sendMailReportsInvalidArgumentsAsToolErrors() {
        McpSchema.CallToolResult result =
                callTool(0, "send_mail", Map.of("smtpPort", 587, "to", List.of("bob@example.com")));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("smtpHost must not be blank");
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void sendMailReportsSmtpFailuresAsToolErrors() {
        transport.failWith(new MessagingException("connection refused"));

        McpSchema.CallToolResult result =
                callTool(
                        0,
                        "send_mail",
                        Map.of(
                                "smtpHost", "smtp.example.com",
                                "smtpPort", 587,
                                "smtpUsername", "alice@example.com",
                                "smtpPassword", "secret",
                                "to", List.of("bob@example.com"),
                                "subject", "Subject"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .contains("SMTP send via smtp.example.com:587")
                .contains("connection refused");
    }

    @Test
    void replyMailAnswersTheArchivedMail() throws Exception {
        when(storage.readIfPresent(ARCHIVE_ID))
                .thenReturn(Optional.of(TestOriginalEmails.withAttachment()));

        McpSchema.CallToolResult result =
                callTool(
                        1,
                        "reply_mail",
                        Map.of(
                                "id", ARCHIVE_ID,
                                "smtpHost", "smtp.example.com",
                                "smtpPort", 587,
                                "smtpUsername", "alice@example.com",
                                "smtpPassword", "secret",
                                "content", "Thanks for the update",
                                "replyAll", true));

        assertThat(result.isError()).isFalse();
        assertThat(text(result))
                .contains("\"subject\":\"Re: Quarterly report\"")
                .contains("\"to\":[\"Alice Replies <alice.reply@example.com>\"]")
                .contains("\"cc\":[\"Bob <bob@example.com>\",\"Carol <carol@example.com>\"]");

        MimeMessage sent = transport.lastMessage();
        assertThat(sent.getHeader("In-Reply-To")).containsExactly("<original-123@example.com>");
        assertThat((String) sent.getContent()).contains("> please review the numbers.");
    }

    @Test
    void replyMailReportsAMissingArchivedMail() {
        when(storage.readIfPresent(ARCHIVE_ID)).thenReturn(Optional.empty());

        McpSchema.CallToolResult result =
                callTool(
                        1,
                        "reply_mail",
                        Map.of(
                                "id", ARCHIVE_ID,
                                "smtpHost", "smtp.example.com",
                                "smtpPort", 587,
                                "smtpUsername", "alice@example.com",
                                "smtpPassword", "secret",
                                "content", "Thanks"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .contains("No original email found in object storage")
                .contains(ARCHIVE_ID);
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void forwardMailCarriesTheOriginalAttachments() throws Exception {
        when(storage.readIfPresent(ARCHIVE_ID))
                .thenReturn(Optional.of(TestOriginalEmails.withAttachment()));

        McpSchema.CallToolResult result =
                callTool(
                        2,
                        "forward_mail",
                        Map.of(
                                "id", ARCHIVE_ID,
                                "smtpHost", "smtp.example.com",
                                "smtpPort", 587,
                                "smtpUsername", "alice@example.com",
                                "smtpPassword", "secret",
                                "to", List.of("dave@example.com"),
                                "content", "See below"));

        assertThat(result.isError()).isFalse();
        assertThat(text(result))
                .contains("\"subject\":\"Fwd: Quarterly report\"")
                .contains("\"attachmentCount\":1");

        Multipart multipart = (Multipart) transport.lastMessage().getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        assertThat(multipart.getBodyPart(1).getFileName()).isEqualTo("report.pdf");
    }

    @Test
    void forwardMailWithoutRecipientsReturnsAnError() {
        McpSchema.CallToolResult result =
                callTool(
                        2,
                        "forward_mail",
                        Map.of(
                                "id", ARCHIVE_ID,
                                "smtpHost", "smtp.example.com",
                                "smtpPort", 587,
                                "smtpUsername", "alice@example.com",
                                "smtpPassword", "secret",
                                "content", "See below"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("to must not be empty when forwarding");
    }

    @Test
    void recordsToolCallOutcomeMetrics() {
        when(storage.readIfPresent(ARCHIVE_ID)).thenReturn(Optional.empty());

        callTool(
                0,
                "send_mail",
                Map.of(
                        "smtpHost", "smtp.example.com",
                        "smtpPort", 587,
                        "smtpUsername", "alice@example.com",
                        "smtpPassword", "secret",
                        "to", List.of("bob@example.com"),
                        "subject", "Subject"));
        callTool(
                1,
                "reply_mail",
                Map.of(
                        "id", ARCHIVE_ID,
                        "smtpHost", "smtp.example.com",
                        "smtpPort", 587,
                        "smtpUsername", "alice@example.com",
                        "smtpPassword", "secret",
                        "content", "Thanks"));

        assertThat(counter("send_mail", "success")).isEqualTo(1.0);
        assertThat(counter("reply_mail", "error")).isEqualTo(1.0);
        assertThat(
                        meterRegistry
                                .get("mail.mcp.tool.duration")
                                .tag("tool", "send_mail")
                                .timer()
                                .count())
                .isEqualTo(1L);
    }

    private double counter(String tool, String outcome) {
        return meterRegistry
                .get("mail.mcp.tool.calls")
                .tag("tool", tool)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private McpSchema.CallToolResult callTool(
            int index, String name, Map<String, Object> arguments) {
        return callTool(index, name, arguments, null);
    }

    private McpSchema.CallToolResult callTool(
            int index, String name, Map<String, Object> arguments, McpSyncServerExchange exchange) {
        McpServerFeatures.SyncToolSpecification spec = tools.toolSpecifications().get(index);
        return spec.callHandler().apply(exchange, new McpSchema.CallToolRequest(name, arguments));
    }

    private String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
