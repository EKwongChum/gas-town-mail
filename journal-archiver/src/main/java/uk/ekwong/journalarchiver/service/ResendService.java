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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;
import uk.ekwong.journalarchiver.model.ResendRequest;
import uk.ekwong.journalarchiver.model.ResendResponse;
import uk.ekwong.journalarchiver.notify.MailMetaPublisher;
import uk.ekwong.journalarchiver.repository.JournalEmailInfoRepository;

/**
 * Re-publishes the RocketMQ notification for archived emails, either for a list of explicit ids or
 * for all emails created in a given time range.
 */
@Service
public class ResendService {

    private static final Logger log = LoggerFactory.getLogger(ResendService.class);

    private final JournalEmailInfoRepository repository;
    private final MailMetaPublisher mailMetaPublisher;
    private final Executor resendExecutor;
    private final AppProperties properties;

    public ResendService(
            JournalEmailInfoRepository repository,
            MailMetaPublisher mailMetaPublisher,
            Executor resendExecutor,
            AppProperties properties) {
        this.repository = repository;
        this.mailMetaPublisher = mailMetaPublisher;
        this.resendExecutor = resendExecutor;
        this.properties = properties;
    }

    public ResendResponse resend(ResendRequest request) {
        ResolvedTargets resolved = resolveTargets(request);
        List<JournalEmailInfo> targets = resolved.targets();
        List<String> notFoundIds = resolved.notFoundIds();
        int total = targets.size() + notFoundIds.size();
        if (total == 0) {
            return new ResendResponse(0, 0, 0, List.of(), List.of());
        }

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        List<String> processed = new ArrayList<>();
        for (JournalEmailInfo info : targets) {
            processed.add(info.getId());
            futures.add(
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return mailMetaPublisher.publish(info);
                                } catch (Exception e) {
                                    log.error(
                                            "Failed to resend notification for id={}",
                                            info.getId(),
                                            e);
                                    return false;
                                }
                            },
                            resendExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        int succeeded = (int) futures.stream().filter(CompletableFuture::join).count();
        int failed = total - succeeded;
        log.info(
                "Resend finished: total={}, succeeded={}, failed={}, notFound={}",
                total,
                succeeded,
                failed,
                notFoundIds.size());
        return new ResendResponse(total, succeeded, failed, processed, notFoundIds);
    }

    /**
     * Resolves the documents to resend. When {@code ids} are provided each id is looked up (missing
     * ids stay as {@code null} and count as failed); otherwise the documents created in the given
     * time range are used directly.
     */
    private ResolvedTargets resolveTargets(ResendRequest request) {
        List<JournalEmailInfo> targets = new ArrayList<>();
        List<String> notFoundIds = new ArrayList<>();
        if (request.ids() != null && !request.ids().isEmpty()) {
            List<String> ids = request.ids().stream().distinct().toList();
            if (ids.size() > properties.getResend().getMaxIds()) {
                throw new IllegalArgumentException(
                        "Too many ids: "
                                + ids.size()
                                + " (maximum is "
                                + properties.getResend().getMaxIds()
                                + ")");
            }
            for (String id : ids) {
                repository
                        .findById(id)
                        .ifPresentOrElse(
                                targets::add,
                                () -> {
                                    log.warn(
                                            "Cannot resend notification: journal email not found in MongoDB, id={}",
                                            id);
                                    notFoundIds.add(id);
                                });
            }
            return new ResolvedTargets(targets, notFoundIds);
        }

        Instant timeGe = request.timeGe();
        Instant timeLt = request.timeLt();
        if (timeGe == null && timeLt == null) {
            throw new IllegalArgumentException(
                    "At least one of 'ids', 'timeGe' or 'timeLt' must be provided");
        }

        List<JournalEmailInfo> found;
        if (timeGe != null && timeLt != null) {
            found = repository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(timeGe, timeLt);
        } else if (timeGe != null) {
            found = repository.findByCreatedAtGreaterThanEqual(timeGe);
        } else {
            found = repository.findByCreatedAtLessThan(timeLt);
        }
        log.info(
                "Found {} journal emails created in range [timeGe={}, timeLt={})",
                found.size(),
                timeGe,
                timeLt);
        return new ResolvedTargets(found, List.of());
    }

    private record ResolvedTargets(List<JournalEmailInfo> targets, List<String> notFoundIds) {}
}
