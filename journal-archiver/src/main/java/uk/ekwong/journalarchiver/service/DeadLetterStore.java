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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists emails that could not be archived after all retries, so the raw
 * message bytes are not lost and can be replayed manually.
 */
@Component
public class DeadLetterStore {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterStore.class);

    private final Path directory;
    private final ObjectMapper objectMapper;

    public DeadLetterStore(AppProperties properties, ObjectMapper objectMapper) {
        this.directory = Path.of(properties.getProcessing().getDeadLetterDir());
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the raw email as an {@code .eml} file plus a sidecar JSON file
     * with the receive context and the failure reason.
     */
    public void save(byte[] rawMessage,
                     String envelopeSender,
                     List<String> recipients,
                     SocketAddress clientAddress,
                     Throwable error) {
        try {
            Files.createDirectories(directory);
            String baseName = "email-" + Instant.now().toString()
                    .replace(":", "-").replace(".", "-") + "-" + UUID.randomUUID();
            Path emlFile = directory.resolve(baseName + ".eml");
            Files.write(emlFile, rawMessage);

            Map<String, Object> context = new LinkedHashMap<>();
            context.put("savedAt", Instant.now().toString());
            context.put("envelopeSender", envelopeSender);
            context.put("recipients", recipients);
            context.put("clientAddress", clientAddress == null ? null : clientAddress.toString());
            context.put("error", error == null ? null : error.toString());
            Path contextFile = directory.resolve(baseName + ".json");
            Files.writeString(contextFile, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(context));

            log.error("Email could not be archived after all retries; saved to {} (context: {})",
                    emlFile, contextFile);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to persist dead-letter email", e);
        }
    }
}
