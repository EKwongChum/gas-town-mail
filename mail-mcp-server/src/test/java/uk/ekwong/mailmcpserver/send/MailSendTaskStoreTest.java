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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailSendTaskStoreTest {

    private final MailSendTaskStore store = new MailSendTaskStore(new MailSendProperties());

    @Test
    void tracksTheLifecycleOfABackgroundDelivery() {
        MailSendTask pending = store.create();
        assertThat(pending.status()).isEqualTo(MailSendTask.Status.PENDING);
        assertThat(store.find(pending.id())).isPresent();

        store.complete(
                pending.id(),
                new MailSendResponse(
                        "<sent@example.com>",
                        "alice@example.com",
                        List.of("bob@example.com"),
                        List.of(),
                        "Subject",
                        0,
                        0,
                        "alice@example.com",
                        Instant.parse("2026-09-21T00:00:00Z")));

        MailSendTask completed = store.find(pending.id()).orElseThrow();
        assertThat(completed.status()).isEqualTo(MailSendTask.Status.SUCCEEDED);
        assertThat(completed.response().messageId()).isEqualTo("<sent@example.com>");
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void recordsFailuresAndForgetsRemovedTasks() {
        MailSendTask task = store.create();

        store.fail(task.id(), "connection refused");
        assertThat(store.find(task.id()).orElseThrow().status())
                .isEqualTo(MailSendTask.Status.FAILED);
        assertThat(store.find(task.id()).orElseThrow().error()).isEqualTo("connection refused");

        store.remove(task.id());
        assertThat(store.find(task.id())).isEmpty();
    }
}
