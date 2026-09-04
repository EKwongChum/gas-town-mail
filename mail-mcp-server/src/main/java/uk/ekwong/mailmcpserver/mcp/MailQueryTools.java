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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import java.util.LinkedHashMap;
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

    private static final String TYPE_STRING = "string";

    private final EmailQueryService queryService;
    private final ObjectMapper objectMapper;

    public MailQueryTools(EmailQueryService queryService, ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
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
                                        + "Returns a JSON object with the total count and the documents on the requested page.")
                        .inputSchema(
                                jsonSchema(
                                        List.of(
                                                arg(
                                                        "keyword",
                                                        "Free-text keyword searched in subject, sender, from, to and message id",
                                                        false),
                                                arg("sender", "Exact sender address", false),
                                                arg("from", "Exact from address", false),
                                                arg("to", "Exact to address", false),
                                                arg("cc", "Exact cc address", false),
                                                arg("messageId", "Exact Message-Id", false),
                                                arg(
                                                        "receivedTimeGe",
                                                        "Received time greater than or equal to, ISO-8601",
                                                        false),
                                                arg(
                                                        "receivedTimeLt",
                                                        "Received time less than, ISO-8601",
                                                        false),
                                                arg(
                                                        "page",
                                                        "integer",
                                                        "Zero-based page number, default 0",
                                                        false),
                                                arg(
                                                        "size",
                                                        "integer",
                                                        "Page size, default 20, max 100",
                                                        false)),
                                        List.of()))
                        .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) ->
                                ok(queryService.search(toSearchRequest(request.arguments()))))
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
                                        + "Returns the document as JSON, or an error when it does not exist.")
                        .inputSchema(
                                jsonSchema(
                                        List.of(
                                                arg(
                                                        "id",
                                                        "Archive id of the email document",
                                                        true)),
                                        List.of("id")))
                        .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) -> {
                            String id = stringArg(request.arguments(), "id");
                            Optional<MailInfoDocument> document = queryService.findById(id);
                            if (document.isPresent()) {
                                return ok(document.get());
                            }
                            return error("No mail document found with id '" + id + "'");
                        })
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
                                jsonSchema(
                                        List.of(
                                                arg(
                                                        "keyword",
                                                        "Free-text keyword searched in subject, sender, from, to and message id",
                                                        false),
                                                arg("sender", "Exact sender address", false),
                                                arg("from", "Exact from address", false),
                                                arg("to", "Exact to address", false),
                                                arg("cc", "Exact cc address", false),
                                                arg("messageId", "Exact Message-Id", false),
                                                arg(
                                                        "receivedTimeGe",
                                                        "Received time greater than or equal to, ISO-8601",
                                                        false),
                                                arg(
                                                        "receivedTimeLt",
                                                        "Received time less than, ISO-8601",
                                                        false)),
                                        List.of()))
                        .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) ->
                                ok(
                                        Map.of(
                                                "count",
                                                queryService.count(
                                                        toSearchRequest(request.arguments())))))
                .build();
    }

    private MailSearchRequest toSearchRequest(Map<String, Object> arguments) {
        return new MailSearchRequest(
                stringArg(arguments, "keyword"),
                stringArg(arguments, "sender"),
                stringArg(arguments, "from"),
                stringArg(arguments, "to"),
                stringArg(arguments, "cc"),
                stringArg(arguments, "messageId"),
                instantArg(arguments, "receivedTimeGe"),
                instantArg(arguments, "receivedTimeLt"),
                intArg(arguments, "page", 0),
                intArg(arguments, "size", 20));
    }

    private McpSchema.CallToolResult ok(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .build();
        } catch (JsonProcessingException e) {
            return error("Failed to serialize result: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }

    private McpSchema.JsonSchema jsonSchema(
            List<Map<String, Object>> properties, List<String> required) {
        Map<String, Object> propertyMap = new LinkedHashMap<>();
        properties.forEach(
                property -> {
                    String name = String.valueOf(property.get("name"));
                    Map<String, Object> schema = new LinkedHashMap<>(property);
                    schema.remove("name");
                    propertyMap.put(name, schema);
                });
        return new McpSchema.JsonSchema("object", propertyMap, required, false, null, null);
    }

    private Map<String, Object> arg(String name, String description, boolean required) {
        return arg(name, TYPE_STRING, description, required);
    }

    private Map<String, Object> arg(
            String name, String type, String description, boolean required) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", name);
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int intArg(Map<String, Object> arguments, String key, int defaultValue) {
        if (arguments == null) {
            return defaultValue;
        }
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Instant instantArg(Map<String, Object> arguments, String key) {
        String value = stringArg(arguments, key);
        return value == null ? null : Instant.parse(value);
    }
}
