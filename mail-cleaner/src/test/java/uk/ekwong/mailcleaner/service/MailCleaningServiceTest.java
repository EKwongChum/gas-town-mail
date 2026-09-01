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

import uk.ekwong.mailcleaner.CleanerTestEmails;
import uk.ekwong.mailcommon.es.MailInfoDocument;
import uk.ekwong.mailcommon.mail.EmailDetailsExtractor;
import uk.ekwong.mailcommon.mail.MailMetaMessage;
import uk.ekwong.mailcommon.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MailCleaningServiceTest {

    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final EmailDetailsExtractor extractor = new EmailDetailsExtractor();
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
    private final MailCleaningService service =
            new MailCleaningService(storage, extractor, mapper, elasticsearchOperations);

    @Test
    void cleansEmailAndSavesToElasticsearch() throws Exception {
        String objectKey = "QWxpY2UgPGFsaWNlQGV4YW1wbGUuY29tPg==_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=";
        byte[] raw = CleanerTestEmails.emailWithAttachments().getBytes(StandardCharsets.UTF_8);
        when(storage.read(objectKey)).thenReturn(raw);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(elasticsearchOperations.indexOps(MailInfoDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);

        String payload = mapper.writeValueAsString(meta(objectKey));

        service.clean(payload, objectKey);

        ArgumentCaptor<MailInfoDocument> captor = ArgumentCaptor.forClass(MailInfoDocument.class);
        verify(elasticsearchOperations).save(captor.capture());
        MailInfoDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo(objectKey);
        assertThat(doc.getSender()).isEqualTo("Alice <alice@example.com>");
        assertThat(doc.getFrom()).isEqualTo("Alice <alice@example.com>");
        assertThat(doc.getTo()).isEqualTo("Bob <bob@example.com>");
        assertThat(doc.getCc()).isEqualTo("Carol <carol@example.com>");
        assertThat(doc.getMessageId()).isEqualTo("<attach-001@example.com>");
        assertThat(doc.getReceivedTime()).isEqualTo(Instant.parse("2026-08-14T00:30:00Z"));
        assertThat(doc.getSubject()).isEqualTo("Project files");
        assertThat(doc.getContentType()).contains("multipart/mixed");
        assertThat(doc.getAttachmentNames()).containsExactly("report.pdf", "notes.txt");
    }

    @Test
    void failsWhenObjectStorageReadFails() throws Exception {
        String objectKey = "missing";
        when(storage.read(objectKey)).thenThrow(new IllegalStateException("object not found"));
        String payload = mapper.writeValueAsString(meta(objectKey));

        assertThatThrownBy(() -> service.clean(payload, objectKey))
                .isInstanceOf(MailCleaningService.MailCleanException.class);
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    void failsWhenPayloadIsNotValidJson() {
        assertThatThrownBy(() -> service.clean("not-json", "key-1"))
                .isInstanceOf(MailCleaningService.MailCleanException.class);

        verifyNoInteractions(storage, elasticsearchOperations);
    }

    @Test
    void readsObjectKeyFromMetaWhenProvided() throws Exception {
        String id = "id-custom";
        String customObjectKey = "custom-object-key";
        byte[] raw = CleanerTestEmails.emailWithAttachments().getBytes(StandardCharsets.UTF_8);
        when(storage.read(customObjectKey)).thenReturn(raw);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(elasticsearchOperations.indexOps(MailInfoDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);

        String payload = mapper.writeValueAsString(metaWithObjectKey(id, customObjectKey));

        service.clean(payload, id);

        verify(storage).read(customObjectKey);
    }

    @Test
    void reusesExistingIndexAndSkipsIndexCheckOnSubsequentMessages() throws Exception {
        String objectKey = "id-1";
        byte[] raw = CleanerTestEmails.emailWithAttachments().getBytes(StandardCharsets.UTF_8);
        when(storage.read(objectKey)).thenReturn(raw);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(elasticsearchOperations.indexOps(MailInfoDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        String payload = mapper.writeValueAsString(meta(objectKey));

        service.clean(payload, objectKey);
        service.clean(payload, objectKey);

        verify(indexOperations).exists();
        verify(indexOperations, org.mockito.Mockito.never()).create();
        verify(elasticsearchOperations, org.mockito.Mockito.times(2)).save(any(MailInfoDocument.class));
    }

    private MailMetaMessage meta(String id) {
        return new MailMetaMessage(
                id,
                "Alice <alice@example.com>",
                "Alice <alice@example.com>",
                "Bob <bob@example.com>",
                "Carol <carol@example.com>",
                "Project files",
                "<attach-001@example.com>",
                "postmaster@corp.local",
                id,
                Instant.parse("2026-08-16T05:00:00Z"),
                Instant.parse("2026-08-16T05:00:00Z"),
                Instant.parse("2026-08-16T05:00:00Z"),
                1);
    }

    private MailMetaMessage metaWithObjectKey(String id, String objectKey) {
        return new MailMetaMessage(
                id,
                "Alice <alice@example.com>",
                "Alice <alice@example.com>",
                "Bob <bob@example.com>",
                "Carol <carol@example.com>",
                "Project files",
                "<attach-001@example.com>",
                "postmaster@corp.local",
                objectKey,
                Instant.parse("2026-08-16T05:00:00Z"),
                Instant.parse("2026-08-16T05:00:00Z"),
                Instant.parse("2026-08-16T05:00:00Z"),
                1);
    }
}
