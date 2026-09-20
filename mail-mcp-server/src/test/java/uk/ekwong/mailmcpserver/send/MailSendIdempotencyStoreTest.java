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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailSendIdempotencyStoreTest {

    private final MailSendProperties properties = new MailSendProperties();
    private final MailSendIdempotencyStore store = new MailSendIdempotencyStore(properties);

    @Test
    void remembersTheResultOfAFinishedRequest() {
        MailSendIdempotencyStore.Reservation first = store.reserve("key");
        assertThat(first).isInstanceOf(MailSendIdempotencyStore.Reservation.Acquired.class);

        MailSendResponse response = response("<sent@example.com>");
        store.complete(
                "key",
                ((MailSendIdempotencyStore.Reservation.Acquired) first).token(),
                MailSendIdempotencyStore.StoredSend.of(response));

        MailSendIdempotencyStore.Reservation replay = store.reserve("key");
        assertThat(replay).isInstanceOf(MailSendIdempotencyStore.Reservation.Completed.class);
        assertThat(((MailSendIdempotencyStore.Reservation.Completed) replay).result().response())
                .isEqualTo(response);
    }

    @Test
    void reportsARequestThatIsStillRunning() {
        store.reserve("key");

        assertThat(store.reserve("key"))
                .isInstanceOf(MailSendIdempotencyStore.Reservation.InFlight.class);
    }

    @Test
    void releasesTheKeyAfterAFailure() {
        MailSendIdempotencyStore.Reservation failed = store.reserve("key");
        store.release("key", ((MailSendIdempotencyStore.Reservation.Acquired) failed).token());

        assertThat(store.reserve("key"))
                .isInstanceOf(MailSendIdempotencyStore.Reservation.Acquired.class);
    }

    @Test
    void keepsATaskReferenceForAsynchronousRequests() {
        MailSendIdempotencyStore.Reservation reservation = store.reserve("key");
        store.complete(
                "key",
                ((MailSendIdempotencyStore.Reservation.Acquired) reservation).token(),
                MailSendIdempotencyStore.StoredSend.ofTask("task-1"));

        MailSendIdempotencyStore.Reservation replay = store.reserve("key");
        assertThat(((MailSendIdempotencyStore.Reservation.Completed) replay).result().asyncTaskId())
                .isEqualTo("task-1");
    }

    @Test
    void forgetsKeysAfterTheirTtl() throws Exception {
        properties.setIdempotencyTtl(Duration.ofMillis(20));
        MailSendIdempotencyStore.Reservation reservation = store.reserve("key");
        store.complete(
                "key",
                ((MailSendIdempotencyStore.Reservation.Acquired) reservation).token(),
                MailSendIdempotencyStore.StoredSend.of(response("<sent@example.com>")));

        Thread.sleep(40);

        assertThat(store.reserve("key"))
                .isInstanceOf(MailSendIdempotencyStore.Reservation.Acquired.class);
    }

    @Test
    void canBeDisabledWithATtlOfZero() {
        properties.setIdempotencyTtl(Duration.ZERO);

        assertThat(store.enabled()).isFalse();
    }

    @Test
    void validatesTheKeyOfARequest() {
        assertThat(store.normalize(" key ")).isEqualTo("key");
        assertThat(store.normalize(null)).isNull();
        assertThatThrownBy(() -> store.normalize("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");
        assertThatThrownBy(() -> store.normalize("x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longer than");
    }

    private MailSendResponse response(String messageId) {
        return new MailSendResponse(
                messageId,
                "alice@example.com",
                List.of("bob@example.com"),
                List.of(),
                "Subject",
                0,
                0,
                "alice@example.com",
                Instant.parse("2026-09-21T00:00:00Z"));
    }
}
