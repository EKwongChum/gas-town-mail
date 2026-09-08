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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.ekwong.mailcommon.es.MailInfoDocument;
import uk.ekwong.mailmcpserver.service.EmailQueryService;
import uk.ekwong.mailmcpserver.service.MailSearchRequest;
import uk.ekwong.mailmcpserver.service.SearchResult;

class MailQueryToolsTest {

    private final EmailQueryService queryService = mock(EmailQueryService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MailQueryTools tools =
            new MailQueryTools(
                    queryService,
                    new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                    meterRegistry);

    private MailInfoDocument sampleDocument;

    @BeforeEach
    void setUp() {
        sampleDocument =
                MailInfoDocument.from(
                        "id-1",
                        "Alice <alice@example.com>",
                        "Alice <alice@example.com>",
                        "Bob <bob@example.com>",
                        null,
                        "<original-123@example.com>",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        "Quarterly report",
                        "text/plain",
                        List.of("report.pdf"));
    }

    @Test
    void registersSearchGetAndCountTools() {
        List<McpServerFeatures.SyncToolSpecification> specs = tools.toolSpecifications();

        assertThat(specs).hasSize(3);
        assertThat(specs)
                .extracting(spec -> spec.tool().name())
                .containsExactly("search_mails", "get_mail_by_id", "count_mails");
        assertThat(specs.get(0).tool().description()).contains("mail_info");
        assertThat(specs.get(1).tool().inputSchema().required()).containsExactly("id");
    }

    @Test
    void searchMailsParsesArgumentsAndReturnsJson() {
        when(queryService.search(any(MailSearchRequest.class)))
                .thenReturn(new SearchResult(1, List.of(sampleDocument)));

        McpSchema.CallToolResult result =
                callTool(
                        0,
                        "search_mails",
                        Map.of(
                                "keyword", "Quarterly",
                                "page", 2,
                                "size", 50));

        assertThat(result.isError()).isFalse();
        assertThat(text(result))
                .contains("\"total\":1")
                .contains("Quarterly report")
                .contains("Alice <alice@example.com>");

        ArgumentCaptor<MailSearchRequest> captor = ArgumentCaptor.forClass(MailSearchRequest.class);
        verify(queryService).search(captor.capture());
        assertThat(captor.getValue().keyword()).isEqualTo("Quarterly");
        assertThat(captor.getValue().page()).isEqualTo(2);
        assertThat(captor.getValue().size()).isEqualTo(50);
    }

    @Test
    void searchMailsAppliesDefaultPagination() {
        when(queryService.search(any(MailSearchRequest.class)))
                .thenReturn(new SearchResult(0, List.of()));

        callTool(0, "search_mails", Map.of());

        ArgumentCaptor<MailSearchRequest> captor = ArgumentCaptor.forClass(MailSearchRequest.class);
        verify(queryService).search(captor.capture());
        assertThat(captor.getValue().page()).isZero();
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    void getMailByIdReturnsDocumentJsonWhenFound() {
        when(queryService.findById("id-1")).thenReturn(Optional.of(sampleDocument));

        McpSchema.CallToolResult result = callTool(1, "get_mail_by_id", Map.of("id", "id-1"));

        assertThat(result.isError()).isFalse();
        assertThat(text(result))
                .contains("\"messageId\":\"<original-123@example.com>\"")
                .contains("Quarterly report");
    }

    @Test
    void getMailByIdReturnsErrorWhenNotFound() {
        when(queryService.findById("missing")).thenReturn(Optional.empty());

        McpSchema.CallToolResult result = callTool(1, "get_mail_by_id", Map.of("id", "missing"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("No mail document found with id 'missing'");
    }

    @Test
    void countMailsReturnsCountJson() {
        when(queryService.count(any(MailSearchRequest.class))).thenReturn(7L);

        McpSchema.CallToolResult result =
                callTool(2, "count_mails", Map.of("sender", "alice@example.com"));

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("\"count\":7");
    }

    @Test
    void recordsToolCallOutcomeMetrics() {
        when(queryService.search(any(MailSearchRequest.class)))
                .thenReturn(new SearchResult(1, List.of(sampleDocument)));
        when(queryService.findById("missing")).thenReturn(Optional.empty());

        callTool(0, "search_mails", Map.of());
        callTool(1, "get_mail_by_id", Map.of("id", "missing"));

        assertThat(
                        meterRegistry
                                .get("mail.mcp.tool.calls")
                                .tag("tool", "search_mails")
                                .tag("outcome", "success")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        meterRegistry
                                .get("mail.mcp.tool.calls")
                                .tag("tool", "get_mail_by_id")
                                .tag("outcome", "error")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        meterRegistry
                                .get("mail.mcp.tool.duration")
                                .tag("tool", "search_mails")
                                .timer()
                                .count())
                .isEqualTo(1L);
    }

    private McpSchema.CallToolResult callTool(
            int index, String name, Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tools.toolSpecifications().get(index);
        return spec.callHandler().apply(null, new McpSchema.CallToolRequest(name, arguments));
    }

    private String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }
}
