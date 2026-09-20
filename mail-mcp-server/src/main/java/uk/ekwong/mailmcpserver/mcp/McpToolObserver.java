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
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wraps every MCP tool call with timing, metrics and a single summary log line, and builds the
 * success/error results of a tool. Request content is never logged.
 */
@Component
public class McpToolObserver {

    private static final Logger log = LoggerFactory.getLogger(McpToolObserver.class);

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public McpToolObserver(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs one tool call with timing/metrics and a summary log line, so every MCP invocation
     * (including error results and exceptions) is observable.
     */
    public McpSchema.CallToolResult observed(
            String toolName, Supplier<McpSchema.CallToolResult> invocation) {
        long startedNanos = System.nanoTime();
        try {
            McpSchema.CallToolResult result = invocation.get();
            record(toolName, startedNanos, result.isError() ? "error" : "success", null);
            return result;
        } catch (RuntimeException e) {
            record(toolName, startedNanos, "error", e);
            throw e;
        }
    }

    /** Serializes a tool result value as JSON text content. */
    public McpSchema.CallToolResult ok(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .build();
        } catch (JsonProcessingException e) {
            return error("Failed to serialize result: " + e.getMessage());
        }
    }

    /** Returns an error result: MCP clients see the message and {@code isError = true}. */
    public McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }

    private void record(
            String toolName, long startedNanos, String outcome, RuntimeException failure) {
        Duration duration = Duration.ofNanos(System.nanoTime() - startedNanos);
        meterRegistry.timer("mail.mcp.tool.duration", "tool", toolName).record(duration);
        meterRegistry
                .counter("mail.mcp.tool.calls", "tool", toolName, "outcome", outcome)
                .increment();
        if (failure != null) {
            log.error(
                    "MCP tool call failed tool={} durationMs={}",
                    toolName,
                    duration.toMillis(),
                    failure);
        } else if ("error".equals(outcome)) {
            log.warn(
                    "MCP tool call returned an error tool={} durationMs={}",
                    toolName,
                    duration.toMillis());
        } else {
            log.info(
                    "MCP tool call succeeded tool={} durationMs={}", toolName, duration.toMillis());
        }
    }
}
