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

package uk.ekwong.journalarchiver.controller;

import uk.ekwong.journalarchiver.model.ResendRequest;
import uk.ekwong.journalarchiver.model.ResendResponse;
import uk.ekwong.journalarchiver.service.ResendService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResendController.class)
class ResendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResendService resendService;

    @Test
    void resendsProvidedIds() throws Exception {
        when(resendService.resend(any(ResendRequest.class)))
                .thenReturn(new ResendResponse(1, 1, 0, List.of("id-1"), List.of()));

        mockMvc.perform(post("/api/journal-emails/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"id-1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        ArgumentCaptor<ResendRequest> captor = ArgumentCaptor.forClass(ResendRequest.class);
        verify(resendService).resend(captor.capture());
        assertThat(captor.getValue().ids()).containsExactly("id-1");
        assertThat(captor.getValue().timeGe()).isNull();
        assertThat(captor.getValue().timeLt()).isNull();
    }

    @Test
    void resendsByTimeRange() throws Exception {
        when(resendService.resend(any(ResendRequest.class)))
                .thenReturn(new ResendResponse(2, 2, 0, List.of("id-a", "id-b"), List.of()));

        mockMvc.perform(post("/api/journal-emails/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeGe\":\"2026-08-16T00:00:00Z\",\"timeLt\":\"2026-08-17T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        ArgumentCaptor<ResendRequest> captor = ArgumentCaptor.forClass(ResendRequest.class);
        verify(resendService).resend(captor.capture());
        assertThat(captor.getValue().timeGe()).isEqualTo(Instant.parse("2026-08-16T00:00:00Z"));
        assertThat(captor.getValue().timeLt()).isEqualTo(Instant.parse("2026-08-17T00:00:00Z"));
        assertThat(captor.getValue().ids()).isNull();
    }

    @Test
    void returnsBadRequestWhenServiceRejects() throws Exception {
        when(resendService.resend(any(ResendRequest.class)))
                .thenThrow(new IllegalArgumentException("At least one of 'ids', 'timeGe' or 'timeLt' must be provided"));

        mockMvc.perform(post("/api/journal-emails/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/journal-emails/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }
}
