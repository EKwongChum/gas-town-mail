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
import uk.ekwong.mailcommon.mail.EmailDetails;
import uk.ekwong.mailcommon.mail.EmailDetailsExtractor;
import uk.ekwong.mailcommon.mail.EmailIdGenerator;
import uk.ekwong.mailcommon.mail.JournalDetector;
import uk.ekwong.mailcommon.mail.OriginalEmail;
import uk.ekwong.mailcommon.mail.OriginalEmailExtractor;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;
import uk.ekwong.mailcommon.storage.ObjectStorageService;
import uk.ekwong.journalarchiver.notify.MailMetaPublisher;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Core pipeline: detect journal format, extract the original email, persist its
 * basic information in MongoDB and store the raw email in object storage.
 */
@Service
public class JournalProcessingService {

    private static final Logger log = LoggerFactory.getLogger(JournalProcessingService.class);

    private final JournalDetector journalDetector;
    private final OriginalEmailExtractor originalEmailExtractor;
    private final EmailDetailsExtractor emailDetailsExtractor;
    private final MongoTemplate mongoTemplate;
    private final ObjectStorageService objectStorageService;
    private final MailMetaPublisher mailMetaPublisher;
    private final DeadLetterStore deadLetterStore;
    private final AppProperties properties;

    public JournalProcessingService(JournalDetector journalDetector,
                                    OriginalEmailExtractor originalEmailExtractor,
                                    EmailDetailsExtractor emailDetailsExtractor,
                                    MongoTemplate mongoTemplate,
                                    ObjectStorageService objectStorageService,
                                    MailMetaPublisher mailMetaPublisher,
                                    DeadLetterStore deadLetterStore,
                                    AppProperties properties) {
        this.journalDetector = journalDetector;
        this.originalEmailExtractor = originalEmailExtractor;
        this.emailDetailsExtractor = emailDetailsExtractor;
        this.mongoTemplate = mongoTemplate;
        this.objectStorageService = objectStorageService;
        this.mailMetaPublisher = mailMetaPublisher;
        this.deadLetterStore = deadLetterStore;
        this.properties = properties;
    }

    @Async("journalProcessingExecutor")
    public void process(byte[] rawMessage, String envelopeSender, List<String> recipients, SocketAddress clientAddress) {
        AppProperties.Processing config = properties.getProcessing();
        int maxAttempts = Math.max(1, config.getRetryMaxAttempts());
        Throwable lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                doProcess(rawMessage, envelopeSender, recipients, clientAddress);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("Processing attempt {}/{} failed (envelope sender={}): {}",
                        attempt, maxAttempts, envelopeSender, e.toString());
                if (attempt < maxAttempts && config.getRetryBackoffMs() > 0) {
                    try {
                        Thread.sleep(config.getRetryBackoffMs() * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        deadLetterStore.save(rawMessage, envelopeSender, recipients, clientAddress, lastError);
    }

    private void doProcess(byte[] rawMessage, String envelopeSender, List<String> recipients, SocketAddress clientAddress)
            throws MessagingException, IOException {
        MimeMessage received = parse(rawMessage);
        AppProperties.Journal journalConfig = properties.getJournal();

        if (!journalDetector.isJournal(received,
                journalConfig.isDetectByHeader(),
                journalConfig.isDetectByRfc822Attachment())) {
            log.debug("Ignoring non-journal message (envelope sender={})", envelopeSender);
            return;
        }

        OriginalEmail original = originalEmailExtractor.extractOriginal(
                received, rawMessage, journalConfig.isExtractOriginalAttachment());
        EmailDetails details = emailDetailsExtractor.extract(
                original.message(), envelopeSender, journalConfig.getSenderResolution());

        String messageId = details.messageId();
        if (isBlank(messageId) && journalConfig.isGenerateMessageIdIfMissing()) {
            // Note: a message without a Message-Id gets a fresh id per archive
            // attempt; a later re-archive of the same message will therefore
            // produce a different archive id. Within one attempt the
            // S3-compensation guarantees only one artifact survives.
            messageId = "<" + UUID.randomUUID() + "@journal-archiver.local>";
            log.info("Original message has no Message-Id; generated {}", messageId);
        }
        String id = EmailIdGenerator.generate(details.sender(), messageId);

        // Object storage first: it is idempotent (same key overwrites), so
        // retries cannot create duplicates; if the MongoDB write then fails we
        // compensate by deleting the object.
        objectStorageService.store(id, original.raw());
        JournalEmailInfo info;
        try {
            info = saveMetadata(id, details, messageId, envelopeSender, recipients, clientAddress);
        } catch (RuntimeException e) {
            objectStorageService.deleteIfPresent(id);
            throw e;
        }

        if (!mailMetaPublisher.publish(info)) {
            log.warn("Notification was not published for id={}; use the resend endpoint to replay", id);
        }

        log.info("Archived journal email: id={}, sender={}, from={}, subject={}, messageId={}",
                id, details.sender(), details.from(), details.subject(), messageId);
    }

    private MimeMessage parse(byte[] raw) throws MessagingException {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
    }

    private JournalEmailInfo saveMetadata(String id,
                                          EmailDetails details,
                                          String messageId,
                                          String envelopeSender,
                                          List<String> recipients,
                                          SocketAddress clientAddress) {
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update();
        // set fields explicitly (including nulls) so a re-archive clears values
        // that are missing from the newer version of the same email
        update.set("sender", details.sender());
        update.set("from", details.from());
        update.set("to", details.to());
        update.set("cc", details.cc());
        update.set("subject", details.subject());
        update.set("messageId", messageId);
        update.set("envelopeSender", envelopeSender);
        update.set("recipients", recipients);
        update.set("clientAddress", clientAddress == null ? null : clientAddress.toString());
        update.set("receivedAt", now);
        update.set("updatedAt", now);
        // atomic counter: 1 on first archive, +1 on every re-archive of the
        // same id (no read-modify-write race)
        update.inc("modificationCount", 1);
        update.setOnInsert("createdAt", now);

        mongoTemplate.upsert(query, update, JournalEmailInfo.class);
        JournalEmailInfo saved = mongoTemplate.findById(id, JournalEmailInfo.class);
        if (saved == null) {
            throw new IllegalStateException("MongoDB upsert did not produce document " + id);
        }
        log.info("Saved journal email metadata to MongoDB: id={}, modificationCount={}",
                id, saved.getModificationCount());
        return saved;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
