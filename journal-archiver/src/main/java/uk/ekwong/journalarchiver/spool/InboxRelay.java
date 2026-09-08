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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.service.JournalProcessingService;
import uk.ekwong.mailcommon.trace.TraceIds;

/**
 * Consumes the durable SMTP inbox. Accepted files are claimed into {@code work/}, processed on the
 * journal processing executor and deleted only after the archive pipeline has handled them. Files
 * whose processing still cannot be handled (for example the dead-letter write itself fails) are
 * moved back to the inbox after a delay and retried.
 */
@Component
public class InboxRelay {

    private static final Logger log = LoggerFactory.getLogger(InboxRelay.class);

    private final MailInbox inbox;
    private final JournalProcessingService processingService;
    private final Executor processingExecutor;
    private final AppProperties.Processing config;
    private final AtomicBoolean recoveredOnStartup = new AtomicBoolean();
    private ScheduledExecutorService scheduler;

    public InboxRelay(
            MailInbox inbox,
            JournalProcessingService processingService,
            Executor processingExecutor,
            AppProperties properties) {
        this.inbox = inbox;
        this.processingService = processingService;
        this.processingExecutor = processingExecutor;
        this.config = properties.getProcessing();
    }

    @PostConstruct
    public void start() {
        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "mail-inbox-relay");
                            thread.setDaemon(true);
                            return thread;
                        });
        scheduler.scheduleWithFixedDelay(
                this::poll,
                0,
                Math.max(100, config.getSpoolPollIntervalMs()),
                TimeUnit.MILLISECONDS);
        log.info(
                "Inbox relay started (poll interval {} ms, max batch {}, retry delay {} ms)",
                config.getSpoolPollIntervalMs(),
                config.getSpoolMaxBatch(),
                config.getSpoolRetryDelayMs());
    }

    /** Polls once; exposed for tests and the scheduled loop. Returns number claimed. */
    public int pollOnce() {
        try {
            if (recoveredOnStartup.compareAndSet(false, true)) {
                inbox.recoverWork();
            }
            List<SpooledMessage> claimed = inbox.claim(Math.max(1, config.getSpoolMaxBatch()));
            for (SpooledMessage message : claimed) {
                submit(message);
            }
            return claimed.size();
        } catch (IOException | RuntimeException e) {
            log.error("Inbox relay poll failed", e);
            return 0;
        }
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void poll() {
        pollOnce();
    }

    private void submit(SpooledMessage message) {
        try {
            processingExecutor.execute(
                    () -> {
                        try {
                            handle(message);
                        } catch (RuntimeException e) {
                            log.error(
                                    "Spooled message processing failed for {}", message.file(), e);
                            scheduleRetry(message);
                        }
                    });
        } catch (RejectedExecutionException e) {
            log.error("Could not submit spooled message for processing", e);
            scheduleRetry(message);
        }
    }

    private void handle(SpooledMessage message) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        MDC.put(
                TraceIds.MDC_KEY,
                message.traceId() == null ? TraceIds.generate() : message.traceId());
        try {
            boolean handled;
            try {
                handled = processingService.process(message);
            } catch (RuntimeException e) {
                log.error("Spooled message processing threw for {}", message.file(), e);
                scheduleRetry(message);
                return;
            }
            if (!handled) {
                log.error(
                        "Spooled message processing could not complete; keeping file for retry: {}",
                        message.file());
                scheduleRetry(message);
                return;
            }
            try {
                inbox.delete(message.file());
            } catch (IOException e) {
                log.error("Could not delete handled spool file {}", message.file(), e);
                scheduleRetry(message);
            }
        } finally {
            if (previousContext == null || previousContext.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousContext);
            }
        }
    }

    private void scheduleRetry(SpooledMessage message) {
        if (scheduler == null) {
            return;
        }
        scheduler.schedule(
                () -> {
                    try {
                        inbox.release(message.file());
                    } catch (IOException e) {
                        log.error("Could not release spool file {} for retry", message.file(), e);
                    }
                },
                Math.max(100, config.getSpoolRetryDelayMs()),
                TimeUnit.MILLISECONDS);
    }
}
