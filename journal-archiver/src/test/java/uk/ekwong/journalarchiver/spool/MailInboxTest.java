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

package uk.ekwong.journalarchiver.spool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ekwong.journalarchiver.config.AppProperties;

class MailInboxTest {

    @TempDir Path tempDir;

    @Test
    void storeClaimReleaseAndDeleteRoundTrip() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getProcessing().setSpoolDir(tempDir.toString());
        MailInbox inbox = new MailInbox(properties, new ObjectMapper());
        byte[] raw = "Subject: test\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);

        inbox.store(raw, "sender@example.com", List.of("a@example.com", "b@example.com"), "/x");

        List<SpooledMessage> first = inbox.claim(10);
        assertThat(first).hasSize(1);
        SpooledMessage claimed = first.get(0);
        assertThat(claimed.raw()).isEqualTo(raw);
        assertThat(claimed.envelopeSender()).isEqualTo("sender@example.com");
        assertThat(claimed.recipients()).containsExactly("a@example.com", "b@example.com");
        assertThat(claimed.clientAddress()).isEqualTo("/x");
        assertThat(claimed.traceId()).matches("[0-9a-f-]{36}");

        inbox.release(claimed.file());
        List<SpooledMessage> second = inbox.claim(10);
        assertThat(second).hasSize(1);

        inbox.delete(second.get(0).file());
        assertThat(inbox.claim(10)).isEmpty();
    }

    @Test
    void recoversWorkFilesBackToInboxAfterRestart() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getProcessing().setSpoolDir(tempDir.toString());
        MailInbox inbox = new MailInbox(properties, new ObjectMapper());
        inbox.store("raw".getBytes(StandardCharsets.UTF_8), "s@example.com", List.of(), null);

        assertThat(inbox.claim(10)).hasSize(1);
        assertThat(inbox.claim(10)).isEmpty();

        inbox.recoverWork();

        assertThat(inbox.claim(10)).hasSize(1);
    }

    @Test
    void quarantinesUnreadableFilesInsteadOfLosingThem() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getProcessing().setSpoolDir(tempDir.toString());
        MailInbox inbox = new MailInbox(properties, new ObjectMapper());
        Files.writeString(tempDir.resolve("inbox").resolve("corrupt.json"), "not-json");

        assertThat(inbox.claim(10)).isEmpty();

        assertThat(Files.exists(tempDir.resolve("quarantine").resolve("corrupt.json"))).isTrue();
    }

    @Test
    void removesStaleTempFilesAtStartup() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getProcessing().setSpoolDir(tempDir.toString());
        new MailInbox(properties, new ObjectMapper());
        Path stale = tempDir.resolve("inbox").resolve("abandoned.json.tmp");
        Files.writeString(stale, "partial");

        new MailInbox(properties, new ObjectMapper());

        assertThat(Files.exists(stale)).isFalse();
    }
}
