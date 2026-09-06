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

package uk.ekwong.mailmcpserver.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ekwong.mailcommon.storage.ObjectStorageService;

class MailOriginalDownloadServiceTest {

    @TempDir Path tempDir;

    private ObjectStorageService storage;
    private TemporaryEmailFileStore fileStore;
    private MailOriginalDownloadService service;

    @BeforeEach
    void setUp() throws IOException {
        storage = mock(ObjectStorageService.class);
        fileStore = new TemporaryEmailFileStore(tempDir, Duration.ofHours(1));
        service = new MailOriginalDownloadService(storage, fileStore);
    }

    @AfterEach
    void tearDown() {
        fileStore.close();
    }

    @Test
    void stagesEachRequestedEmailWithUniqueZipEntryName() throws Exception {
        byte[] first = "From: a@example.com".getBytes(StandardCharsets.UTF_8);
        byte[] second = "From: b@example.com".getBytes(StandardCharsets.UTF_8);
        when(storage.readIfPresent("id-1")).thenReturn(Optional.of(first));
        when(storage.readIfPresent("id-2")).thenReturn(Optional.of(second));

        List<StagedOriginalEmail> staged = service.stage(List.of(" id-1 ", "id-2", "id-1"));

        assertThat(staged).hasSize(2);
        assertThat(staged.get(0).archiveId()).isEqualTo("id-1");
        assertThat(staged.get(0).zipEntryName()).isEqualTo("id-1.eml");
        assertThat(staged.get(1).archiveId()).isEqualTo("id-2");
        assertThat(staged.get(1).zipEntryName()).isEqualTo("id-2.eml");
        assertThat(staged).allSatisfy(email -> assertThat(email.file()).exists());
    }

    @Test
    void writeZipStreamsStagedFilesIntoArchive() throws Exception {
        byte[] first = "From: a@example.com".getBytes(StandardCharsets.UTF_8);
        byte[] second = "From: b@example.com".getBytes(StandardCharsets.UTF_8);
        when(storage.readIfPresent("id-1")).thenReturn(Optional.of(first));
        when(storage.readIfPresent("id-2")).thenReturn(Optional.of(second));
        List<StagedOriginalEmail> staged = service.stage(List.of("id-1", "id-2"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(staged, output);

        try (ZipInputStream zip =
                new ZipInputStream(new java.io.ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry firstEntry = zip.getNextEntry();
            assertThat(firstEntry.getName()).isEqualTo("id-1.eml");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("From: a@example.com");
            zip.closeEntry();
            ZipEntry secondEntry = zip.getNextEntry();
            assertThat(secondEntry.getName()).isEqualTo("id-2.eml");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("From: b@example.com");
        }
    }

    @Test
    void throwsNotFoundWhenAnyRequestedEmailIsMissing() {
        when(storage.readIfPresent("id-1")).thenReturn(Optional.of(new byte[] {1}));
        when(storage.readIfPresent("id-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stage(List.of("id-1", "id-missing")))
                .isInstanceOf(OriginalMailNotFoundException.class)
                .hasMessageContaining("id-missing");
    }

    @Test
    void rejectsBlankOrEmptyIds() {
        assertThatThrownBy(() -> service.stage(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids must not be empty");
        assertThatThrownBy(() -> service.stage(List.of(" ", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids must not be empty");
        assertThatThrownBy(() -> service.stage(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids must not be empty");
    }

    @Test
    void rejectsMoreThanMaxIdsPerRequest() {
        List<String> tooMany =
                java.util.stream.IntStream.range(
                                0, MailOriginalDownloadService.MAX_IDS_PER_REQUEST + 1)
                        .mapToObj(i -> "id-" + i)
                        .toList();

        assertThatThrownBy(() -> service.stage(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max 1000");
    }
}
