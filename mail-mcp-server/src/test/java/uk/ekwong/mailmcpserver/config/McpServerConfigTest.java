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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;
import uk.ekwong.mailmcpserver.mcp.MailQueryTools;
import uk.ekwong.mailmcpserver.mcp.MailSendTools;
import uk.ekwong.mailmcpserver.mcp.McpToolObserver;
import uk.ekwong.mailmcpserver.send.MailCompositionService;
import uk.ekwong.mailmcpserver.send.MailSendProperties;
import uk.ekwong.mailmcpserver.send.MailSendRequestMapper;
import uk.ekwong.mailmcpserver.send.MailSendService;
import uk.ekwong.mailmcpserver.service.EmailQueryService;

class McpServerConfigTest {

    @Test
    void buildsTransportRouterAndSyncServer() {
        McpServerConfig config = new McpServerConfig();
        ObjectMapper objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        WebMvcStreamableServerTransportProvider transport =
                config.mcpTransportProvider(objectMapper, "/mcp");

        assertThat(transport.getRouterFunction()).isNotNull();
        assertThat(config.mcpRouterFunction(transport)).isNotNull();

        McpToolObserver observer = new McpToolObserver(objectMapper, new SimpleMeterRegistry());
        MailQueryTools queryTools = new MailQueryTools(mock(EmailQueryService.class), observer);
        MailSendTools sendTools =
                new MailSendTools(
                        new MailSendRequestMapper(new MailSendProperties()),
                        mock(MailSendService.class),
                        mock(MailCompositionService.class),
                        observer);
        McpSyncServer server = config.mcpSyncServer(transport, queryTools, sendTools);
        try {
            assertThat(server).isNotNull();
            assertThat(queryTools.toolSpecifications())
                    .extracting(specification -> specification.tool().name())
                    .containsExactly("search_mails", "get_mail_by_id", "count_mails");
            assertThat(sendTools.toolSpecifications())
                    .extracting(specification -> specification.tool().name())
                    .containsExactly("send_mail", "reply_mail", "forward_mail");
        } finally {
            server.close();
        }
    }
}
