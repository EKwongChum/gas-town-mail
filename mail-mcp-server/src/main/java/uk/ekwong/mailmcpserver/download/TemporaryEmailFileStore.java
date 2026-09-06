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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes staged {@code .eml} files into a temporary directory and schedules each file for deletion
 * on its own timer, so no single file stays on disk longer than the configured TTL. The directory
 * is treated as dedicated to this store: leftover {@code .eml} files are removed on startup. A file
 * that is currently being read (acquired) is never deleted while the reader is active; deletion is
 * deferred until the last reader releases it.
 */
public class TemporaryEmailFileStore implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(TemporaryEmailFileStore.class);
    private static final int MAX_PREFIX_LENGTH = 80;

    private final Path directory;
    private final Duration fileTtl;
    private final ScheduledExecutorService cleanupExecutor;
    private final ConcurrentMap<Path, FileState> stagedFiles = new ConcurrentHashMap<>();

    public TemporaryEmailFileStore(Path directory, Duration fileTtl) throws IOException {
        if (fileTtl == null || fileTtl.isZero() || fileTtl.isNegative()) {
            throw new IllegalArgumentException("fileTtl must be a positive duration");
        }
        this.directory = directory.toAbsolutePath().normalize();
        this.fileTtl = fileTtl;
        Files.createDirectories(this.directory);
        cleanupLeftoverFiles();
        this.cleanupExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "mail-original-temp-cleanup");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /** The temporary directory holding staged files. */
    public Path directory() {
        return directory;
    }

    /**
     * Writes the given email bytes to a unique {@code .eml} file in the temporary directory and
     * schedules that file for deletion after the configured TTL.
     */
    public Path stage(String archiveId, byte[] content) throws IOException {
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, safePrefix(archiveId) + "-", ".eml");
        try {
            Files.write(file, content);
        } catch (IOException e) {
            deleteQuietly(file);
            throw e;
        }
        FileState state = new FileState();
        stagedFiles.put(file, state);
        scheduleCleanup(file, state);
        log.info(
                "Staged original email {} as {} ({} bytes, cleanup in {})",
                archiveId,
                file.getFileName(),
                content.length,
                fileTtl);
        return file;
    }

    /**
     * Marks the staged file as in use. The cleanup timer will wait for the matching {@link
     * #release(Path)} before deleting the file once its TTL has elapsed.
     */
    public void acquire(Path file) {
        FileState state = stagedFiles.get(file);
        if (state == null) {
            throw new IllegalStateException("Temporary email file is no longer available: " + file);
        }
        synchronized (state) {
            if (state.deleted) {
                throw new IllegalStateException(
                        "Temporary email file was already deleted: " + file);
            }
            state.activeUsers++;
        }
    }

    /** Releases a previously acquired staged file, deleting it if its TTL already elapsed. */
    public void release(Path file) {
        FileState state = stagedFiles.get(file);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.activeUsers > 0) {
                state.activeUsers--;
            }
            if (state.deleteRequested) {
                deleteIfIdle(file, state);
            }
        }
    }

    @Override
    public void close() {
        cleanupExecutor.shutdownNow();
        log.info("Stopped temporary email file cleanup for {}", directory);
    }

    private void scheduleCleanup(Path file, FileState state) {
        try {
            cleanupExecutor.schedule(
                    () -> requestDeletion(file, state), fileTtl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            deleteQuietly(file);
            stagedFiles.remove(file, state);
        }
    }

    private void requestDeletion(Path file, FileState state) {
        synchronized (state) {
            state.deleteRequested = true;
            deleteIfIdle(file, state);
        }
    }

    private void deleteIfIdle(Path file, FileState state) {
        if (state.deleted || state.activeUsers > 0) {
            return;
        }
        state.deleted = true;
        stagedFiles.remove(file, state);
        deleteQuietly(file);
    }

    private void cleanupLeftoverFiles() {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".eml"))
                    .forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("Could not list leftover files in {}: {}", directory, e.getMessage());
        }
    }

    private void deleteQuietly(Path file) {
        try {
            if (Files.deleteIfExists(file)) {
                log.info("Removed temporary file {}", file);
            }
        } catch (IOException e) {
            log.warn("Failed to remove temporary file {}: {}", file, e.getMessage());
        }
    }

    private String safePrefix(String archiveId) {
        String cleaned = archiveId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isEmpty()) {
            cleaned = "mail";
        }
        if (cleaned.length() > MAX_PREFIX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_PREFIX_LENGTH);
        }
        return "mail-" + cleaned;
    }

    private static final class FileState {
        private int activeUsers;
        private boolean deleteRequested;
        private boolean deleted;
    }
}
