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

package uk.ekwong.mailmcpserver.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiKeyFilterTest {

    private static final String KEY = "s3cr3t-key";

    private final SecurityProperties properties = new SecurityProperties();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ProbeController())
                        .addFilters(new ApiKeyFilter(properties))
                        .build();
    }

    @Test
    void isDisabledWhileNoKeyIsConfigured() throws Exception {
        mockMvc.perform(get("/api/mails/probe")).andExpect(status().isOk());
        mockMvc.perform(get("/mcp")).andExpect(status().isOk());
    }

    @Test
    void rejectsProtectedPathsWithoutAValidKey() throws Exception {
        properties.setApiKey(KEY);

        mockMvc.perform(get("/api/mails/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value(containsString("API key")));
        mockMvc.perform(get("/mcp"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(containsString("API key")));

        mockMvc.perform(get("/api/mails/probe").header("X-API-Key", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsTheKeyFromItsHeaderOrAsBearerToken() throws Exception {
        properties.setApiKey(KEY);

        mockMvc.perform(get("/api/mails/probe").header("X-API-Key", KEY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/mcp").header(HttpHeaders.AUTHORIZATION, "Bearer " + KEY))
                .andExpect(status().isOk());
    }

    @Test
    void leavesPathsOutsideTheProtectedListOpen() throws Exception {
        properties.setApiKey(KEY);

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void honoursCustomProtectedPaths() throws Exception {
        properties.setApiKey(KEY);
        properties.setProtectedPaths(List.of("/mcp"));

        mockMvc.perform(get("/api/mails/probe")).andExpect(status().isOk());
        mockMvc.perform(get("/mcp")).andExpect(status().isUnauthorized());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/mails/probe")
        String mailProbe() {
            return "ok";
        }

        @GetMapping("/mcp")
        String mcpProbe() {
            return "ok";
        }

        @GetMapping("/actuator/health")
        String healthProbe() {
            return "ok";
        }
    }
}
