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

package uk.ekwong.mailmcpserver.send;

import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

/**
 * Runs deliveries in the background for requests that asked for it ({@code Prefer: respond-async})
 * and exposes their state, so a slow SMTP server no longer occupies an HTTP worker.
 */
@Service
public class MailSendTaskService {

    private static final Logger log = LoggerFactory.getLogger(MailSendTaskService.class);

    private final MailSendService sendService;
    private final MailSendTaskStore taskStore;
    private final AsyncTaskExecutor executor;

    public MailSendTaskService(
            MailSendService sendService,
            MailSendTaskStore taskStore,
            @Qualifier("mailSendExecutor") AsyncTaskExecutor executor) {
        this.sendService = sendService;
        this.taskStore = taskStore;
        this.executor = executor;
    }

    /**
     * Queues the command for background delivery.
     *
     * @throws MailSendQueueFullException when the pool and its queue are saturated
     */
    public MailSendTask submit(MailSendCommand command) {
        MailSendTask task = taskStore.create();
        Map<String, String> context = MDC.getCopyOfContextMap();
        try {
            executor.execute(() -> deliver(task.id(), command, context));
        } catch (TaskRejectedException e) {
            taskStore.remove(task.id());
            log.warn("Rejected a background delivery: the queue is full");
            throw new MailSendQueueFullException();
        }
        return task;
    }

    /** State of a background delivery, empty when the task is unknown or expired. */
    public Optional<MailSendTask> find(String taskId) {
        return taskStore.find(taskId);
    }

    private void deliver(String taskId, MailSendCommand command, Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        }
        try {
            taskStore.complete(taskId, sendService.send(command));
        } catch (RuntimeException e) {
            log.warn("Background delivery {} failed: {}", taskId, e.getMessage());
            taskStore.fail(taskId, e.getMessage());
        } finally {
            MDC.clear();
        }
    }
}
