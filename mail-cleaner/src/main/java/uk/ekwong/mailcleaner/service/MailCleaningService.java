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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;
import uk.ekwong.mailcommon.es.MailInfoDocument;
import uk.ekwong.mailcommon.mail.EmailDetails;
import uk.ekwong.mailcommon.mail.EmailDetailsExtractor;
import uk.ekwong.mailcommon.mail.MailMetaMessage;
import uk.ekwong.mailcommon.storage.ObjectStorageService;

/**
 * Reads the archived email from object storage, parses its details and indexes them into
 * Elasticsearch ({@code mail_info}).
 */
@Service
public class MailCleaningService {

    private static final Logger log = LoggerFactory.getLogger(MailCleaningService.class);

    private final ObjectStorageService objectStorageService;
    private final EmailDetailsExtractor emailDetailsExtractor;
    private final ObjectMapper objectMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private volatile boolean indexEnsured;

    public MailCleaningService(
            ObjectStorageService objectStorageService,
            EmailDetailsExtractor emailDetailsExtractor,
            ObjectMapper objectMapper,
            ElasticsearchOperations elasticsearchOperations) {
        this.objectStorageService = objectStorageService;
        this.emailDetailsExtractor = emailDetailsExtractor;
        this.objectMapper = objectMapper;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * Cleans one archived email. Throws a runtime exception when the message cannot be processed so
     * the consumer can trigger RocketMQ redelivery.
     */
    public void clean(String payloadJson, String messageKey) {
        try {
            MailMetaMessage meta = objectMapper.readValue(payloadJson, MailMetaMessage.class);
            String objectKey =
                    meta.objectKey() != null && !meta.objectKey().isBlank()
                            ? meta.objectKey()
                            : meta.id();

            byte[] raw = objectStorageService.read(objectKey);
            MimeMessage message =
                    new MimeMessage(
                            Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
            EmailDetails details = emailDetailsExtractor.extract(message, meta.envelopeSender());

            ExtractedMail extracted =
                    new ExtractedMail(
                            details.sender(),
                            details.from(),
                            details.to(),
                            details.cc(),
                            details.messageId(),
                            details.receivedTime(),
                            details.subject(),
                            details.contentType(),
                            details.attachmentNames());

            MailInfoDocument doc =
                    MailInfoDocument.from(
                            objectKey,
                            extracted.sender(),
                            extracted.from(),
                            extracted.to(),
                            extracted.cc(),
                            extracted.messageId(),
                            extracted.receivedTime(),
                            extracted.subject(),
                            extracted.contentType(),
                            extracted.attachmentNames());
            ensureIndex();
            elasticsearchOperations.save(doc);
            log.info(
                    "Saved cleaned email to Elasticsearch mail_info: id={}, messageId={}",
                    doc.getId(),
                    doc.getMessageId());
        } catch (Exception e) {
            log.error("Failed to clean email (message key={})", messageKey, e);
            throw new MailCleanException("Failed to clean email, message key=" + messageKey, e);
        }
    }

    /**
     * Ensures the {@code mail_info} index exists. Best-effort: when Elasticsearch is unreachable
     * the index is checked again on the next message instead of failing the application startup.
     */
    private synchronized void ensureIndex() {
        if (indexEnsured) {
            return;
        }
        try {
            IndexOperations indexOperations =
                    elasticsearchOperations.indexOps(MailInfoDocument.class);
            if (!indexOperations.exists()) {
                indexOperations.create();
            }
            indexEnsured = true;
        } catch (Exception e) {
            log.warn(
                    "Could not verify/create Elasticsearch index 'mail_info'; will retry on next message",
                    e);
        }
    }

    /** Immutable extracted fields that are written to the Elasticsearch document. */
    public record ExtractedMail(
            String sender,
            String from,
            String to,
            String cc,
            String messageId,
            Instant receivedTime,
            String subject,
            String contentType,
            List<String> attachmentNames) {}

    public static class MailCleanException extends RuntimeException {
        public MailCleanException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
