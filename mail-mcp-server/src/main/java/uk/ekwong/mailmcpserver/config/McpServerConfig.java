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

package uk.ekwong.mailmcpserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import uk.ekwong.mailmcpserver.mcp.MailQueryTools;
import uk.ekwong.mailmcpserver.mcp.MailSendTools;

/**
 * Wires the standard Spring MCP server:
 *
 * <ul>
 *   <li>{@link WebMvcStreamableServerTransportProvider} exposes the MCP protocol endpoint
 *       (Streamable HTTP) on {@code /mcp};
 *   <li>{@link McpSyncServer} registers the mail query tools from {@link MailQueryTools} and serves
 *       them to MCP clients, together with the outbound mail tools from {@link MailSendTools}
 *       (send_mail / reply_mail / forward_mail).
 * </ul>
 */
@Configuration
public class McpServerConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider mcpTransportProvider(
            ObjectMapper objectMapper, @Value("${app.mcp.endpoint:/mcp}") String mcpEndpoint) {
        return WebMvcStreamableServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .mcpEndpoint(mcpEndpoint)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebMvcStreamableServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer mcpSyncServer(
            WebMvcStreamableServerTransportProvider transportProvider,
            MailQueryTools mailQueryTools,
            MailSendTools mailSendTools) {
        List<McpServerFeatures.SyncToolSpecification> tools =
                new ArrayList<>(mailQueryTools.toolSpecifications());
        tools.addAll(mailSendTools.toolSpecifications());
        return McpServer.sync(transportProvider)
                .serverInfo("mail-mcp-server", "2.1.0")
                .instructions(
                        "Query the archived journal email metadata in Elasticsearch (mail_info index) with "
                                + "search_mails, get_mail_by_id and count_mails, and send mail with send_mail, "
                                + "reply_mail and forward_mail. The sending tools take the SMTP server and account "
                                + "as arguments for a single delivery; reply_mail and forward_mail take the archive "
                                + "id returned by the query tools.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(tools)
                .build();
    }
}
