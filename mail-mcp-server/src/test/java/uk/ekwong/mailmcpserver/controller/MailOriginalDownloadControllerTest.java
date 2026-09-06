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

package uk.ekwong.mailmcpserver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.ekwong.mailcommon.storage.ObjectStorageService;
import uk.ekwong.mailmcpserver.download.MailOriginalDownloadService;
import uk.ekwong.mailmcpserver.download.TemporaryEmailFileStore;

class MailOriginalDownloadControllerTest {

    @TempDir Path tempDir;

    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TemporaryEmailFileStore fileStore;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        fileStore = new TemporaryEmailFileStore(tempDir, Duration.ofMinutes(30));
        MailOriginalDownloadService service = new MailOriginalDownloadService(storage, fileStore);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new MailOriginalDownloadController(service))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @AfterEach
    void tearDown() {
        fileStore.close();
    }

    @Test
    void downloadsRequestedEmailsAsZipAttachment() throws Exception {
        byte[] first = "From: a@example.com".getBytes(StandardCharsets.UTF_8);
        byte[] second = "From: b@example.com".getBytes(StandardCharsets.UTF_8);
        when(storage.readIfPresent("id-1")).thenReturn(Optional.of(first));
        when(storage.readIfPresent("id-2")).thenReturn(Optional.of(second));

        byte[] body =
                mockMvc.perform(
                                post("/api/mail-originals/download")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of("ids", List.of("id-1", "id-2")))))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType("application/zip"))
                        .andExpect(
                                header().string(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                containsString("mail-originals.zip")))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("id-1.eml");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("From: a@example.com");
            zip.closeEntry();
            assertThat(zip.getNextEntry().getName()).isEqualTo("id-2.eml");
        }
    }

    @Test
    void returns404WhenEmailIsMissing() throws Exception {
        when(storage.readIfPresent("id-missing")).thenReturn(Optional.empty());

        mockMvc.perform(
                        post("/api/mail-originals/download")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ids\":[\"id-missing\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("id-missing")));
    }

    @Test
    void returns400ForEmptyIds() throws Exception {
        mockMvc.perform(
                        post("/api/mail-originals/download")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ids must not be empty"));
    }

    @Test
    void returns400ForMalformedBody() throws Exception {
        mockMvc.perform(
                        post("/api/mail-originals/download")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }
}
