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

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Keeps the state of background deliveries for {@code app.send.async.task-ttl}; the store is in
 * memory and per instance, which fits the single instance deployments this service targets.
 */
@Component
public class MailSendTaskStore {

    private static final int MAX_ENTRIES = 10_000;

    private final MailSendProperties properties;
    private final Map<String, MailSendTask> tasks = new ConcurrentHashMap<>();

    public MailSendTaskStore(MailSendProperties properties) {
        this.properties = properties;
    }

    /** Registers a new pending delivery. */
    public MailSendTask create() {
        MailSendTask task = MailSendTask.pending(UUID.randomUUID().toString(), Instant.now());
        tasks.put(task.id(), task);
        sweep();
        return task;
    }

    public void complete(String id, MailSendResponse response) {
        update(id, task -> task.succeeded(response, Instant.now()));
    }

    public void fail(String id, String error) {
        update(id, task -> task.failed(error, Instant.now()));
    }

    public Optional<MailSendTask> find(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public void remove(String id) {
        tasks.remove(id);
    }

    private void update(String id, java.util.function.UnaryOperator<MailSendTask> change) {
        tasks.computeIfPresent(id, (ignored, task) -> change.apply(task));
    }

    private void sweep() {
        if (tasks.size() <= MAX_ENTRIES) {
            return;
        }
        long now = System.currentTimeMillis();
        long ttlMillis = properties.getAsync().getTaskTtl().toMillis();
        Iterator<Map.Entry<String, MailSendTask>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            MailSendTask task = iterator.next().getValue();
            if (task.completedAt() != null
                    && now - task.completedAt().toEpochMilli() >= ttlMillis) {
                iterator.remove();
            }
        }
    }
}
