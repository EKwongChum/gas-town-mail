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

package uk.ekwong.journalarchiver.service;

import uk.ekwong.journalarchiver.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void writesEmlAndContextSidecar() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getProcessing().setDeadLetterDir(tempDir.toString());
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        DeadLetterStore store = new DeadLetterStore(properties, mapper);
        byte[] raw = "Subject: test\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);

        store.save(raw, "sender@example.com", List.of("rcpt@example.com"), null, new RuntimeException("boom"));

        List<Path> files;
        try (var stream = Files.list(tempDir)) {
            files = stream.toList();
        }
        assertThat(files).hasSize(2);
        Path eml = files.stream().filter(p -> p.toString().endsWith(".eml")).findFirst().orElseThrow();
        Path context = files.stream().filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
        assertThat(Files.readAllBytes(eml)).isEqualTo(raw);
        String contextJson = Files.readString(context);
        assertThat(contextJson).contains("envelopeSender").contains("sender@example.com").contains("boom");
    }
}
