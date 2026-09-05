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

package uk.ekwong.mailcleaner.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.ekwong.mailcommon.storage.ObjectStorageService;

/**
 * Reads the original email bytes from object storage by archive id (MongoDB id / object storage
 * key) so HTTP callers can download them as a {@code .eml} file.
 */
@Service
public class OriginalEmailDownloadService {

    private final ObjectStorageService objectStorageService;

    public OriginalEmailDownloadService(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    /**
     * Downloads the raw original email for the given archive id. Throws {@link
     * IllegalArgumentException} for a blank id and {@link OriginalEmailNotFoundException} when no
     * object exists in storage.
     */
    public byte[] download(String id) {
        String objectKey = normalize(id);
        return objectStorageService
                .readIfPresent(objectKey)
                .orElseThrow(() -> new OriginalEmailNotFoundException(objectKey));
    }

    private String normalize(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return id.trim();
    }
}
