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

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Remembers the outcome of a delivery for the {@code Idempotency-Key} of a request, so a client
 * that retries after a timeout does not send the same mail twice.
 *
 * <p>The store is in memory and per instance: with several replicas the key is only effective
 * against retries that reach the same instance.
 */
@Component
public class MailSendIdempotencyStore {

    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_KEY_LENGTH = 200;

    private final MailSendProperties properties;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public MailSendIdempotencyStore(MailSendProperties properties) {
        this.properties = properties;
    }

    /** What a finished request answered with: a synchronous response or a background task id. */
    public record StoredSend(MailSendResponse response, String asyncTaskId) {

        public static StoredSend of(MailSendResponse response) {
            return new StoredSend(response, null);
        }

        public static StoredSend ofTask(String asyncTaskId) {
            return new StoredSend(null, asyncTaskId);
        }
    }

    /** Outcome of reserving a key for one request. */
    public sealed interface Reservation {

        /** The key was used before and the delivery already finished. */
        record Completed(StoredSend result) implements Reservation {}

        /** Another request with the same key is still running. */
        record InFlight() implements Reservation {}

        /** The caller now owns the key and has to complete or release it. */
        record Acquired(String token) implements Reservation {}
    }

    public boolean enabled() {
        return properties.getIdempotencyTtl() != null
                && !properties.getIdempotencyTtl().isZero()
                && !properties.getIdempotencyTtl().isNegative();
    }

    /**
     * Normalizes a client supplied key.
     *
     * @return the trimmed key, {@code null} when no key was sent
     * @throws IllegalArgumentException when the key is blank or too long
     */
    public String normalize(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not be longer than " + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    /** Reserves the key for one request. */
    public Reservation reserve(String key) {
        long now = System.currentTimeMillis();
        String token = UUID.randomUUID().toString();
        long ttlMillis = properties.getIdempotencyTtl().toMillis();
        Entry entry =
                entries.compute(
                        key,
                        (ignored, current) -> {
                            if (current == null || current.expired(now, ttlMillis)) {
                                return new Entry(token, now, null);
                            }
                            return current;
                        });
        sweep(now, ttlMillis);
        if (token.equals(entry.token)) {
            return new Reservation.Acquired(token);
        }
        return entry.result == null
                ? new Reservation.InFlight()
                : new Reservation.Completed(entry.result);
    }

    /** Marks the request of the given reservation as finished. */
    public void complete(String key, String token, StoredSend result) {
        entries.computeIfPresent(
                key,
                (ignored, current) ->
                        current.token.equals(token)
                                ? new Entry(current.token, current.startedAt, result)
                                : current);
    }

    /** Frees the key after a failed attempt, so the client may retry with the same key. */
    public void release(String key, String token) {
        entries.computeIfPresent(
                key,
                (ignored, current) ->
                        current.token.equals(token) && current.result == null ? null : current);
    }

    private void sweep(long now, long ttlMillis) {
        if (entries.size() <= MAX_ENTRIES) {
            return;
        }
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expired(now, ttlMillis)) {
                iterator.remove();
            }
        }
    }

    private record Entry(String token, long startedAt, StoredSend result) {

        private boolean expired(long now, long ttlMillis) {
            return now - startedAt >= ttlMillis;
        }
    }
}
