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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemporaryEmailFileStoreTest {

    @TempDir Path tempDir;

    @Test
    void stagesFileInsideTemporaryDirectory() throws Exception {
        try (TemporaryEmailFileStore store =
                new TemporaryEmailFileStore(tempDir, Duration.ofMinutes(30))) {
            Path file = store.stage("id-1", "raw email".getBytes(StandardCharsets.UTF_8));

            assertThat(file).exists();
            assertThat(file.getParent()).isEqualTo(tempDir.toAbsolutePath());
            assertThat(Files.readAllBytes(file))
                    .isEqualTo("raw email".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void removesEachFileAfterItsOwnTtl() throws Exception {
        try (TemporaryEmailFileStore store =
                new TemporaryEmailFileStore(tempDir, Duration.ofMillis(20))) {
            Path first = store.stage("id-1", "first".getBytes(StandardCharsets.UTF_8));
            Thread.sleep(30);
            Path second = store.stage("id-2", "second".getBytes(StandardCharsets.UTF_8));

            awaitDeleted(first);
            assertThat(second).exists();

            awaitDeleted(second);
        }
    }

    @Test
    void defersDeletionWhileFileIsAcquired() throws Exception {
        try (TemporaryEmailFileStore store =
                new TemporaryEmailFileStore(tempDir, Duration.ofMillis(20))) {
            Path file = store.stage("id-1", "in use".getBytes(StandardCharsets.UTF_8));
            store.acquire(file);

            Thread.sleep(100);
            assertThat(file).exists();

            store.release(file);
            awaitDeleted(file);
        }
    }

    @Test
    void removesLeftoverEmlFilesOnStartup() throws Exception {
        Path leftover = tempDir.resolve("mail-id-1-123.eml");
        Files.writeString(leftover, "leftover");

        try (TemporaryEmailFileStore store =
                new TemporaryEmailFileStore(tempDir, Duration.ofMinutes(30))) {
            assertThat(leftover).doesNotExist();
            assertThat(store.directory()).exists();
        }
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new TemporaryEmailFileStore(tempDir, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileTtl");
    }

    private void awaitDeleted(Path file) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (Files.exists(file) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(file).doesNotExist();
    }
}
