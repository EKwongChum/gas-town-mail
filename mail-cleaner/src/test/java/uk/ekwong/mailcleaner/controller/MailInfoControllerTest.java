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

package uk.ekwong.mailcleaner.controller;

import uk.ekwong.mailcleaner.service.MailDeleteResponse;
import uk.ekwong.mailcleaner.service.MailDeletionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MailInfoControllerTest {

    private final MailDeletionService deletionService = mock(MailDeletionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MailInfoController(deletionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deleteReturnsDeletedAndNotFoundIds() throws Exception {
        when(deletionService.deleteByIds(anyList()))
                .thenReturn(new MailDeleteResponse(3, 2, List.of("id-missing")));

        mockMvc.perform(post("/api/mail-info/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("ids", List.of("id-1", "id-2", "id-missing")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(3))
                .andExpect(jsonPath("$.deleted").value(2))
                .andExpect(jsonPath("$.notFoundIds[0]").value("id-missing"));

        verify(deletionService).deleteByIds(List.of("id-1", "id-2", "id-missing"));
    }

    @Test
    void deleteReturns400ForEmptyIds() throws Exception {
        doThrow(new IllegalArgumentException("ids must not be empty"))
                .when(deletionService).deleteByIds(List.of());

        mockMvc.perform(post("/api/mail-info/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns400ForMalformedBody() throws Exception {
        mockMvc.perform(post("/api/mail-info/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }
}
