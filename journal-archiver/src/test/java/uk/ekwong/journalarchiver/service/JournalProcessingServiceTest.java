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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.ekwong.journalarchiver.TestEmails;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;
import uk.ekwong.journalarchiver.notify.MailMetaPublisher;
import uk.ekwong.mailcommon.mail.EmailDetailsExtractor;
import uk.ekwong.mailcommon.mail.EmailIdGenerator;
import uk.ekwong.mailcommon.mail.JournalDetector;
import uk.ekwong.mailcommon.mail.OriginalEmailExtractor;
import uk.ekwong.mailcommon.storage.ObjectStorageService;

class JournalProcessingServiceTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final MailMetaPublisher publisher = mock(MailMetaPublisher.class);
    private final DeadLetterStore deadLetterStore = mock(DeadLetterStore.class);

    @Test
    void archivesJournalEmailAndPublishes() {
        byte[] raw = TestEmails.journalReport().getBytes(StandardCharsets.UTF_8);
        String expectedId =
                EmailIdGenerator.generate(
                        "Alice <alice@example.com>", "<original-123@example.com>");
        JournalEmailInfo saved = info(expectedId);
        when(mongoTemplate.findById(expectedId, JournalEmailInfo.class)).thenReturn(saved);
        when(publisher.publish(saved)).thenReturn(true);

        service(properties(1, 0))
                .process(raw, "postmaster@corp.local", List.of("journal@archive.local"), null);

        verify(storage).store(eq(expectedId), any(byte[].class));
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate)
                .upsert(any(Query.class), updateCaptor.capture(), eq(JournalEmailInfo.class));
        Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertThat(set).containsEntry("objectKey", expectedId);
        verify(publisher).publish(saved);
        verifyNoInteractions(deadLetterStore);
    }

    @Test
    void retriesAndWritesDeadLetterWhenStorageKeepsFailing() {
        byte[] raw = TestEmails.journalReport().getBytes(StandardCharsets.UTF_8);
        doThrow(new RuntimeException("object storage unavailable"))
                .when(storage)
                .store(anyString(), any(byte[].class));

        service(properties(3, 0)).process(raw, "postmaster@corp.local", List.of(), null);

        verify(storage, times(3)).store(anyString(), any(byte[].class));
        verify(deadLetterStore)
                .save(eq(raw), eq("postmaster@corp.local"), any(), any(), any(Throwable.class));
        verifyNoInteractions(mongoTemplate, publisher);
    }

    @Test
    void compensatesMongoFailureByDeletingTheObject() {
        byte[] raw = TestEmails.journalReport().getBytes(StandardCharsets.UTF_8);
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(JournalEmailInfo.class)))
                .thenThrow(new RuntimeException("mongodb unavailable"));

        service(properties(2, 0)).process(raw, "postmaster@corp.local", List.of(), null);

        verify(storage, times(2)).store(anyString(), any(byte[].class));
        verify(storage, times(2)).deleteIfPresent(anyString());
        verify(deadLetterStore).save(any(), any(), any(), any(), any(Throwable.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void ignoresNonJournalEmails() {
        byte[] raw = TestEmails.plainEmail().getBytes(StandardCharsets.UTF_8);

        service(properties(1, 0)).process(raw, "alice@example.com", List.of(), null);

        verifyNoInteractions(mongoTemplate, storage, publisher, deadLetterStore);
    }

    @Test
    void skipsJournalEmailThatDoesNotPassCollectionFilter() {
        byte[] raw = TestEmails.journalReport().getBytes(StandardCharsets.UTF_8);
        AppProperties props = properties(1, 0);
        props.getJournal().getFilter().setSenderEmails(List.of("someone@example.com"));

        service(props).process(raw, "postmaster@corp.local", List.of(), null);

        verifyNoInteractions(mongoTemplate, storage, publisher, deadLetterStore);
    }

    @Test
    void archivesJournalEmailWhenAllCollectionFiltersPass() {
        byte[] raw = TestEmails.journalReport().getBytes(StandardCharsets.UTF_8);
        AppProperties props = properties(1, 0);
        props.getJournal().getFilter().setSenderEmails(List.of("alice@example.com"));
        props.getJournal().getFilter().setFromEmails(List.of("alice@example.com"));
        props.getJournal().getFilter().setToEmails(List.of("bob@example.com"));
        props.getJournal().getFilter().setCcEmails(List.of("carol@example.com"));
        String expectedId =
                EmailIdGenerator.generate(
                        "Alice <alice@example.com>", "<original-123@example.com>");
        JournalEmailInfo saved = info(expectedId);
        when(mongoTemplate.findById(expectedId, JournalEmailInfo.class)).thenReturn(saved);
        when(publisher.publish(saved)).thenReturn(true);

        service(props).process(raw, "postmaster@corp.local", List.of(), null);

        verify(storage).store(eq(expectedId), any(byte[].class));
        verify(mongoTemplate)
                .upsert(any(Query.class), any(Update.class), eq(JournalEmailInfo.class));
        verify(publisher).publish(saved);
        verifyNoInteractions(deadLetterStore);
    }

    @Test
    void generatesMessageIdWhenOriginalHasNone() {
        // remove the Message-Id from the embedded original email
        String report =
                TestEmails.journalReport()
                        .replace("Message-ID: <original-123@example.com>\r\n", "");
        byte[] raw = report.getBytes(StandardCharsets.UTF_8);
        when(mongoTemplate.findById(anyString(), eq(JournalEmailInfo.class)))
                .thenAnswer(
                        invocation -> {
                            String id = invocation.getArgument(0);
                            JournalEmailInfo info = new JournalEmailInfo();
                            info.setId(id);
                            info.setObjectKey(id);
                            info.setMessageId("<generated@journal-archiver.local>");
                            info.setCreatedAt(Instant.parse("2026-08-16T05:00:00Z"));
                            info.setUpdatedAt(Instant.parse("2026-08-16T05:00:00Z"));
                            info.setModificationCount(1);
                            return info;
                        });
        when(publisher.publish(any(JournalEmailInfo.class))).thenReturn(true);

        service(properties(1, 0)).process(raw, "postmaster@corp.local", List.of(), null);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate)
                .upsert(queryCaptor.capture(), any(Update.class), eq(JournalEmailInfo.class));
        String archivedId = (String) queryCaptor.getValue().getQueryObject().get("_id");
        String[] parts = archivedId.split("_", 2);
        assertThat(parts[0])
                .isEqualTo(
                        Base64.getEncoder()
                                .encodeToString(
                                        "Alice <alice@example.com>"
                                                .getBytes(StandardCharsets.UTF_8)));
        String decodedMessageId =
                new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(decodedMessageId).startsWith("<").endsWith("@journal-archiver.local>");

        ArgumentCaptor<JournalEmailInfo> infoCaptor =
                ArgumentCaptor.forClass(JournalEmailInfo.class);
        verify(publisher).publish(infoCaptor.capture());
        assertThat(infoCaptor.getValue().getMessageId())
                .startsWith("<")
                .endsWith("@journal-archiver.local>");
    }

    @Test
    void avoidsCollisionsWhenGenerationDisabledAndMessageIdMissing() {
        when(mongoTemplate.findById(anyString(), eq(JournalEmailInfo.class)))
                .thenAnswer(
                        invocation -> {
                            String id = invocation.getArgument(0);
                            JournalEmailInfo info = new JournalEmailInfo();
                            info.setId(id);
                            info.setObjectKey(id);
                            return info;
                        });
        when(publisher.publish(any(JournalEmailInfo.class))).thenReturn(true);
        AppProperties props = properties(1, 0);
        props.getJournal().setGenerateMessageIdIfMissing(false);

        service(props)
                .process(
                        journalReportWithoutMessageId("first unique body"),
                        "postmaster@corp.local",
                        List.of(),
                        null);
        service(props)
                .process(
                        journalReportWithoutMessageId("second unique body"),
                        "postmaster@corp.local",
                        List.of(),
                        null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).store(keyCaptor.capture(), any(byte[].class));
        assertThat(keyCaptor.getAllValues()).doesNotHaveDuplicates();
        verifyNoInteractions(deadLetterStore);
    }

    private byte[] journalReportWithoutMessageId(String body) {
        String report =
                TestEmails.journalReport()
                        .replace("Message-ID: <original-123@example.com>\r\n", "")
                        .replace("Hello Bob, please review the quarterly numbers.", body);
        return report.getBytes(StandardCharsets.UTF_8);
    }

    private JournalProcessingService service(AppProperties properties) {
        return new JournalProcessingService(
                new JournalDetector(),
                new OriginalEmailExtractor(),
                new EmailDetailsExtractor(),
                mongoTemplate,
                storage,
                publisher,
                deadLetterStore,
                new JournalMailFilter(),
                properties);
    }

    private AppProperties properties(int retryMaxAttempts, long backoffMs) {
        AppProperties properties = new AppProperties();
        properties.getProcessing().setRetryMaxAttempts(retryMaxAttempts);
        properties.getProcessing().setRetryBackoffMs(backoffMs);
        properties.getProcessing().setDeadLetterDir(System.getProperty("java.io.tmpdir"));
        return properties;
    }

    private JournalEmailInfo info(String id) {
        JournalEmailInfo info = new JournalEmailInfo();
        info.setId(id);
        info.setObjectKey(id);
        info.setSender("Alice <alice@example.com>");
        info.setMessageId("<original-123@example.com>");
        info.setCreatedAt(Instant.parse("2026-08-16T05:00:00Z"));
        info.setUpdatedAt(Instant.parse("2026-08-16T05:00:00Z"));
        info.setModificationCount(1);
        return info;
    }
}
