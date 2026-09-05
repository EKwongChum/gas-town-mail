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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ekwong.mailcommon.storage.ObjectStorageService;

class OriginalEmailDownloadServiceTest {

    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final OriginalEmailDownloadService service = new OriginalEmailDownloadService(storage);

    @Test
    void downloadsRawEmailByArchiveId() {
        byte[] raw = "From: a@example.com".getBytes(StandardCharsets.UTF_8);
        when(storage.readIfPresent("id-1")).thenReturn(Optional.of(raw));

        byte[] content = service.download(" id-1 ");

        assertThat(content).isEqualTo(raw);
        verify(storage).readIfPresent("id-1");
    }

    @Test
    void rejectsBlankId() {
        assertThatThrownBy(() -> service.download("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be blank");
        assertThatThrownBy(() -> service.download(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsNotFoundWhenObjectIsMissing() {
        when(storage.readIfPresent("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download("missing"))
                .isInstanceOf(OriginalEmailNotFoundException.class)
                .hasMessageContaining("missing");
    }
}
