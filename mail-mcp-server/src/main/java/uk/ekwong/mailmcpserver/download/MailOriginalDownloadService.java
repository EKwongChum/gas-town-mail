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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.ekwong.mailcommon.storage.ObjectStorageService;

/**
 * Downloads the requested original emails from object storage into temporary files and packs them
 * into a single zip archive.
 */
@Service
public class MailOriginalDownloadService {

    private static final Logger log = LoggerFactory.getLogger(MailOriginalDownloadService.class);

    public static final int MAX_IDS_PER_REQUEST = 1000;

    private final ObjectStorageService objectStorageService;
    private final TemporaryEmailFileStore fileStore;

    public MailOriginalDownloadService(
            ObjectStorageService objectStorageService, TemporaryEmailFileStore fileStore) {
        this.objectStorageService = objectStorageService;
        this.fileStore = fileStore;
    }

    /**
     * Reads each requested original email from object storage and writes it to its own temporary
     * {@code .eml} file. Blank ids are ignored, duplicates are collapsed, and a request with no ids
     * (or more than {@link #MAX_IDS_PER_REQUEST}) is rejected.
     *
     * @throws OriginalMailNotFoundException when any requested email is missing from storage
     */
    public List<StagedOriginalEmail> stage(List<String> ids) throws IOException {
        List<String> normalized = normalize(ids);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        if (normalized.size() > MAX_IDS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "too many ids, max " + MAX_IDS_PER_REQUEST + " per request");
        }

        List<String> missing = new ArrayList<>();
        List<StagedOriginalEmail> staged = new ArrayList<>();
        Set<String> usedZipNames = new HashSet<>();
        for (String archiveId : normalized) {
            Optional<byte[]> content = objectStorageService.readIfPresent(archiveId);
            if (content.isEmpty()) {
                missing.add(archiveId);
                continue;
            }
            staged.add(
                    new StagedOriginalEmail(
                            archiveId,
                            fileStore.stage(archiveId, content.get()),
                            uniqueZipEntryName(archiveId, usedZipNames)));
        }
        if (!missing.isEmpty()) {
            throw new OriginalMailNotFoundException(missing);
        }
        log.info("Staged {} original emails for zip download", staged.size());
        return List.copyOf(staged);
    }

    /** Streams the staged {@code .eml} files into a zip archive. */
    public void writeZip(List<StagedOriginalEmail> staged, OutputStream output) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (StagedOriginalEmail email : staged) {
                fileStore.acquire(email.file());
                try {
                    zip.putNextEntry(new ZipEntry(email.zipEntryName()));
                    Files.copy(email.file(), zip);
                    zip.closeEntry();
                } finally {
                    fileStore.release(email.file());
                }
            }
        }
    }

    private List<String> normalize(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private String uniqueZipEntryName(String archiveId, Set<String> used) {
        String base = archiveId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty()) {
            base = "mail";
        }
        String name = base + ".eml";
        int suffix = 2;
        while (!used.add(name)) {
            name = base + "-" + suffix++ + ".eml";
        }
        return name;
    }
}
