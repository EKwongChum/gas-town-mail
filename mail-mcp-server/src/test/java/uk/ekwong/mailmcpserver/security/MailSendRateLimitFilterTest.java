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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uk.ekwong.mailmcpserver.send.MailSendProperties;
import uk.ekwong.mailmcpserver.send.MailSendRateLimiter;

class MailSendRateLimitFilterTest {

    private final MailSendProperties properties = new MailSendProperties();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ProbeController())
                        .addFilters(
                                new MailSendRateLimitFilter(
                                        properties, new MailSendRateLimiter(properties)))
                        .build();
    }

    @Test
    void isDisabledByDefault() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/mails/send")).andExpect(status().isOk());
        }
    }

    @Test
    void answers429WithRetryAfterWhenTheLimitIsExceeded() throws Exception {
        properties.getRateLimit().setRequestsPerMinute(1);

        mockMvc.perform(get("/api/mails/send")).andExpect(status().isOk());
        mockMvc.perform(get("/api/mails/send"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value(containsString("Too many mail requests")));
    }

    @Test
    void doesNotLimitOtherPaths() throws Exception {
        properties.getRateLimit().setRequestsPerMinute(1);

        mockMvc.perform(get("/api/mails/send")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // polling a delivery task must not consume the send quota
        mockMvc.perform(get("/api/mails/tasks/task-1")).andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/mails/send")
        String mailProbe() {
            return "ok";
        }

        @GetMapping("/api/mails/tasks/{id}")
        String taskProbe(@PathVariable String id) {
            return "ok";
        }

        @GetMapping("/actuator/health")
        String healthProbe() {
            return "ok";
        }
    }
}
