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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.mailcommon.trace.TraceIds;

/**
 * Durable SMTP inbox. Every accepted message is written to {@code inbox/} as one self-contained
 * JSON file (raw bytes base64-encoded plus envelope context), fsynced and atomically moved into
 * place before the SMTP handler acknowledges it with {@code 250}. The inbox relay atomically moves
 * files to {@code work/} while processing them and deletes them only after success, so a crash can
 * never lose an acknowledged message.
 */
@Component
public class MailInbox {

    private static final Logger log = LoggerFactory.getLogger(MailInbox.class);

    private final Path inboxDir;
    private final Path workDir;
    private final Path quarantineDir;
    private final ObjectMapper objectMapper;

    public MailInbox(AppProperties properties, ObjectMapper objectMapper) throws IOException {
        Path root = Path.of(properties.getProcessing().getSpoolDir()).toAbsolutePath().normalize();
        this.inboxDir = root.resolve("inbox");
        this.workDir = root.resolve("work");
        this.quarantineDir = root.resolve("quarantine");
        this.objectMapper = objectMapper;
        Files.createDirectories(inboxDir);
        Files.createDirectories(workDir);
        Files.createDirectories(quarantineDir);
        cleanupTempFiles();
    }

    /** Durably stores one accepted message and returns the spool id. */
    public String store(
            byte[] raw, String envelopeSender, List<String> recipients, String clientAddress)
            throws IOException {
        String spoolId =
                Instant.now().toString().replace(":", "-").replace(".", "-")
                        + "-"
                        + UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("spoolId", spoolId);
        payload.put("raw", Base64.getEncoder().encodeToString(raw));
        payload.put("envelopeSender", envelopeSender);
        ArrayNode recipientsNode = payload.putArray("recipients");
        if (recipients != null) {
            recipients.forEach(recipientsNode::add);
        }
        payload.put("clientAddress", clientAddress);
        payload.put("traceId", TraceIds.currentOrGenerate());
        payload.put("savedAt", Instant.now().toString());

        Path temp = inboxDir.resolve(spoolId + ".json.tmp");
        writeDurably(objectMapper.writeValueAsBytes(payload), temp);
        Path target = inboxDir.resolve(spoolId + ".json");
        move(temp, target);
        return spoolId;
    }

    /**
     * Atomically claims up to {@code max} inbox files by moving them to {@code work/}. Files that
     * cannot be read are moved to {@code quarantine/} and logged instead of being lost.
     */
    public List<SpooledMessage> claim(int max) throws IOException {
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> files = Files.list(inboxDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(max)
                    .forEach(candidates::add);
        }

        List<SpooledMessage> claimed = new ArrayList<>();
        for (Path inboxFile : candidates) {
            if (!Files.exists(inboxFile)) {
                continue; // another relay instance claimed it first
            }
            Path workFile = workDir.resolve(inboxFile.getFileName());
            move(inboxFile, workFile);
            try {
                claimed.add(read(workFile));
            } catch (IOException | RuntimeException e) {
                moveToQuarantine(workFile);
                log.error("Quarantined unreadable spool file {}", workFile, e);
            }
        }
        return claimed;
    }

    /** Moves {@code work/} files back to {@code inbox/} after a restart/crash. */
    public void recoverWork() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(workDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(files::add);
        }
        for (Path file : files) {
            Path target = inboxDir.resolve(file.getFileName());
            if (Files.exists(target)) {
                moveToQuarantine(file);
                log.warn("Duplicate spool target {}; quarantined {}", target, file);
            } else {
                move(file, target);
            }
        }
        if (!files.isEmpty()) {
            log.info("Recovered {} spooled messages after startup", files.size());
        }
    }

    /** Deletes a successfully handled spool file. */
    public void delete(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    /** Moves a claimed spool file back to the inbox for a later retry. */
    public void release(Path file) throws IOException {
        Path target = inboxDir.resolve(file.getFileName());
        if (!Files.exists(file)) {
            return;
        }
        if (Files.exists(target)) {
            moveToQuarantine(file);
        } else {
            move(file, target);
        }
    }

    private SpooledMessage read(Path file) throws IOException {
        JsonNode payload = objectMapper.readTree(Files.readAllBytes(file));
        byte[] raw = Base64.getDecoder().decode(payload.get("raw").asText());
        List<String> recipients = new ArrayList<>();
        JsonNode recipientsNode = payload.get("recipients");
        if (recipientsNode != null && recipientsNode.isArray()) {
            recipientsNode.forEach(node -> recipients.add(node.asText()));
        }
        return new SpooledMessage(
                file,
                raw,
                text(payload, "envelopeSender"),
                recipients,
                text(payload, "clientAddress"),
                text(payload, "traceId"));
    }

    private void writeDurably(byte[] content, Path temp) throws IOException {
        try (FileChannel channel =
                FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void moveToQuarantine(Path file) {
        Path target = quarantineDir.resolve(file.getFileName());
        try {
            move(file, target);
        } catch (IOException e) {
            log.error("Could not move {} to quarantine {}", file, target, e);
        }
    }

    private void cleanupTempFiles() throws IOException {
        try (Stream<Path> files = Files.list(inboxDir)) {
            List<Path> stale =
                    files.filter(path -> path.getFileName().toString().endsWith(".json.tmp"))
                            .toList();
            for (Path file : stale) {
                Files.deleteIfExists(file);
                log.info("Removed stale spool temp file {}", file);
            }
        }
    }

    private String text(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
