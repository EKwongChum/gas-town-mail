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

package uk.ekwong.journalarchiver.notify;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;

/**
 * Mongo-backed notification outbox. Every archived email is first written with {@code PENDING}
 * status; the in-pipeline publish normally marks it {@code SENT}. This relay scans PENDING records
 * that were never confirmed and retries them, so a RocketMQ outage or process crash self-heals
 * without manual resend.
 */
@Component
public class NotificationOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxRelay.class);

    private final MongoTemplate mongoTemplate;
    private final MailMetaPublisher mailMetaPublisher;
    private final AppProperties properties;
    private ScheduledExecutorService scheduler;

    public NotificationOutboxRelay(
            MongoTemplate mongoTemplate,
            MailMetaPublisher mailMetaPublisher,
            AppProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.mailMetaPublisher = mailMetaPublisher;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        long interval = Math.max(1000, properties.getNotify().getOutbox().getScanIntervalMs());
        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "notification-outbox");
                            thread.setDaemon(true);
                            return thread;
                        });
        scheduler.scheduleWithFixedDelay(this::scan, interval, interval, TimeUnit.MILLISECONDS);
        log.info("Notification outbox relay started (scan interval {} ms)", interval);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /** Scans once; exposed for tests and the scheduled loop. */
    public int scanOnce() {
        if (!properties.getNotify().getRocketMq().isEnabled()) {
            return 0;
        }
        AppProperties.Notify.Outbox config = properties.getNotify().getOutbox();
        int maxAttempts = Math.max(1, config.getMaxAttempts());
        int scanned = 0;
        JournalEmailInfo claimed;
        while ((claimed = claimNext(config)) != null) {
            scanned++;
            processClaimed(claimed, maxAttempts);
        }
        return scanned;
    }

    private void scan() {
        try {
            scanOnce();
        } catch (RuntimeException e) {
            log.error("Notification outbox scan failed", e);
        }
    }

    private JournalEmailInfo claimNext(AppProperties.Notify.Outbox config) {
        Instant now = Instant.now();
        Instant newRecordCutoff = now.minusMillis(config.getGraceMs());
        Instant retryCutoff = now.minusMillis(config.getRetryBackoffMs());
        Criteria status = Criteria.where("notificationStatus").is(NotificationStatus.PENDING);
        Criteria newRecords =
                Criteria.where("notificationAttemptCount")
                        .is(0)
                        .and("notificationAttemptAt")
                        .lt(newRecordCutoff);
        Criteria retries =
                Criteria.where("notificationAttemptCount")
                        .gt(0)
                        .and("notificationAttemptAt")
                        .lt(retryCutoff);
        Query query =
                Query.query(
                        new Criteria()
                                .andOperator(
                                        status, new Criteria().orOperator(newRecords, retries)));
        Update update =
                new Update().inc("notificationAttemptCount", 1).set("notificationAttemptAt", now);
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                JournalEmailInfo.class);
    }

    private void processClaimed(JournalEmailInfo claimed, int maxAttempts) {
        String id = claimed.getId();
        if (mailMetaPublisher.publish(claimed)) {
            try {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(id)),
                        new Update()
                                .set("notificationStatus", NotificationStatus.SENT)
                                .set("notifiedAt", Instant.now()),
                        JournalEmailInfo.class);
                log.info("Outbox notification sent and marked SENT: id={}", id);
            } catch (RuntimeException e) {
                log.error(
                        "Outbox notification was sent for id={} but SENT state could not be persisted; it may be sent again",
                        id,
                        e);
            }
            return;
        }
        int attempts = claimed.getNotificationAttemptCount();
        if (attempts >= maxAttempts) {
            try {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(id)),
                        new Update().set("notificationStatus", NotificationStatus.FAILED),
                        JournalEmailInfo.class);
            } catch (RuntimeException e) {
                log.error("Could not mark notification FAILED for id={}", id, e);
            }
            log.error(
                    "Notification outbox exhausted after {} attempts for id={}; use the resend endpoint",
                    attempts,
                    id);
        } else {
            log.warn("Notification outbox send failed for id={}, attempt {}", id, attempts);
        }
    }
}
