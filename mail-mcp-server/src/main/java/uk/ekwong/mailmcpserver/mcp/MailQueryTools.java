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
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import uk.ekwong.mailcommon.es.MailInfoDocument;
import uk.ekwong.mailmcpserver.service.EmailQueryService;
import uk.ekwong.mailmcpserver.service.MailSearchRequest;

/**
 * MCP tool definitions for querying the archived email metadata (Elasticsearch {@code mail_info}
 * index).
 */
@Component
public class MailQueryTools {

    private final EmailQueryService queryService;
    private final McpToolObserver observer;

    public MailQueryTools(EmailQueryService queryService, McpToolObserver observer) {
        this.queryService = queryService;
        this.observer = observer;
    }

    /**
     * All tools exposed by this MCP server. Tool names are stable identifiers used by MCP clients
     * (LLM tool calls).
     */
    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
        return List.of(searchMailsTool(), getMailByIdTool(), countMailsTool());
    }

    private McpServerFeatures.SyncToolSpecification searchMailsTool() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder()
                        .name("search_mails")
                        .title("Search archived emails")
                        .description(
                                "Searches the Elasticsearch mail_info index for archived journal emails. "
                                        + "All filters are optional and combined with AND. When keyword is provided it is "
                                        + "matched against subject, sender, from, to and message id. "
                                        + "Times are ISO-8601 instants (e.g. 2026-08-23T08:00:00Z). "
                                        + "Returns a JSON object with the total count and the documents on the requested page; "
                                        + "each document includes sha256, the digest of the archived original .eml file.")
                        .inputSchema(
                                McpToolSchemas.jsonSchema(
                                        List.of(
                                                McpToolSchemas.arg(
                                                        "keyword",
                                                        "Free-text keyword searched in subject, sender, from, to and message id",
                                                        false),
                                                McpToolSchemas.arg(
                                                        "sender", "Exact sender address", false),
                                                McpToolSchemas.arg(
                                                        "from", "Exact from address", false),
                                                McpToolSchemas.arg("to", "Exact to address", false),
                                                McpToolSchemas.arg("cc", "Exact cc address", false),
                                                McpToolSchemas.arg(
                                                        "messageId", "Exact Message-Id", false),
                                                McpToolSchemas.arg(
                                                        "receivedTimeGe",
                                                        "Received time greater than or equal to, ISO-8601",
                                                        false),
                                                McpToolSchemas.arg(
                                                        "receivedTimeLt",
                                                        "Received time less than, ISO-8601",
                                                        false),
                                                McpToolSchemas.arg(
                                                        "page",
                                                        "integer",
                                                        "Zero-based page number, default 0",
                                                        false),
                                                McpToolSchemas.arg(
                                                        "size",
                                                        "integer",
                                                        "Page size, default 20, max 100",
                                                        false))))
                        .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) ->
                                observer.observed(
                                        "search_mails",
                                        () ->
                                                observer.ok(
                                                        queryService.search(
                                                                toSearchRequest(
                                                                        request.arguments())))))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification getMailByIdTool() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder()
                        .name("get_mail_by_id")
                        .title("Get an archived email by id")
                        .description(
                                "Returns the archived email document with the given archive id "
                                        + "(MongoDB document id / object storage key) from the Elasticsearch mail_info index. "
                                        + "The document includes sender, from, to, cc, messageId, receivedTime, subject, "
                                        + "contentType, attachmentNames and sha256 (the digest of the original .eml file). "
                                        + "Returns the document as JSON, or an error when it does not exist. "
                                        + "The id can be passed to reply_mail or forward_mail to answer or forward the email.")
                        .inputSchema(
                                McpToolSchemas.jsonSchema(
                                        List.of(
                                                McpToolSchemas.arg(
                                                        "id",
                                                        "Archive id of the email document",
                                                        true))))
                        .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) ->
                                observer.observed(
                                        "get_mail_by_id",
                                        () -> {
                                            String id =
                                                    McpToolSchemas.string(
                                                            request.arguments(), "id");
                                            Optional<MailInfoDocument> document =
                                                    queryService.findById(id);
                                            if (document.isPresent()) {
                                                return observer.ok(document.get());
                                            }
                                            return observer.error(
                                                    "No mail document found with id '" + id + "'");
                                        }))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification countMailsTool() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder()
                        .name("count_mails")
                        .title("Count archived emails")
                        .description(
                                "Counts the archived journal emails in the Elasticsearch mail_info index matching "
                                        + "the same optional filters as search_mails (pagination is ignored). "
                                        + "Times are ISO-8601 instants. Returns a JSON object with the count.")
                        .inputSchema(
                                McpToolSchemas.jsonSchema(
                                        List.of(
                                                McpToolSchemas.arg(
                                                        "keyword",
                                                        "Free-text keyword searched in subject, sender, from, to and message id",
                                                        false),
                                                McpToolSchemas.arg(
                                                        "sender", "Exact sender address", false),
                                                McpToolSchemas.arg(
                                                        "from", "Exact from address", false),
                                                McpToolSchemas.arg("to", "Exact to address", false),
                                                McpToolSchemas.arg("cc", "Exact cc address", false),
                                                McpToolSchemas.arg(
                                                        "messageId", "Exact Message-Id", false),
                                                McpToolSchemas.arg(
                                                        "receivedTimeGe",
                                                        "Received time greater than or equal to, ISO-8601",
                                                        false),
                                                McpToolSchemas.arg(
                                                        "receivedTimeLt",
                                                        "Received time less than, ISO-8601",
                                                        false))))
                        .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) ->
                                observer.observed(
                                        "count_mails",
                                        () ->
                                                observer.ok(
                                                        Map.of(
                                                                "count",
                                                                queryService.count(
                                                                        toSearchRequest(
                                                                                request
                                                                                        .arguments()))))))
                .build();
    }

    private MailSearchRequest toSearchRequest(Map<String, Object> arguments) {
        return new MailSearchRequest(
                McpToolSchemas.string(arguments, "keyword"),
                McpToolSchemas.string(arguments, "sender"),
                McpToolSchemas.string(arguments, "from"),
                McpToolSchemas.string(arguments, "to"),
                McpToolSchemas.string(arguments, "cc"),
                McpToolSchemas.string(arguments, "messageId"),
                McpToolSchemas.instant(arguments, "receivedTimeGe"),
                McpToolSchemas.instant(arguments, "receivedTimeLt"),
                McpToolSchemas.integer(arguments, "page", 0),
                McpToolSchemas.integer(arguments, "size", 20));
    }
}
