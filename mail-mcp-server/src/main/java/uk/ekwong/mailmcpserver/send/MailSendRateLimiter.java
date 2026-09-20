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

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter for the outbound mail endpoints, keyed by client (normally the remote
 * address). It exists so a single caller cannot use the service as a bulk mail relay; it is
 * disabled while {@code app.send.rate-limit.requests-per-minute} is 0.
 */
@Component
public class MailSendRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final MailSendProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis = new AtomicLong();

    public MailSendRateLimiter(MailSendProperties properties) {
        this.properties = properties;
    }

    /** Whether rate limiting is switched on. */
    public boolean enabled() {
        return properties.getRateLimit().enabled();
    }

    /** Result of one admission check. */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    /** Records one request of the given client and reports whether it is allowed. */
    public Decision acquire(String client) {
        int limit = properties.getRateLimit().getRequestsPerMinute();
        if (limit <= 0) {
            return new Decision(true, 0);
        }
        long now = System.currentTimeMillis();
        Window window =
                windows.compute(
                        client,
                        (key, current) -> {
                            if (current == null || now - current.startMillis >= 60_000) {
                                return new Window(now, 1);
                            }
                            current.count++;
                            return current;
                        });
        sweep(now);
        if (window.count <= limit) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1, (60_000 - (now - window.startMillis)) / 1000);
        return new Decision(false, retryAfter);
    }

    private void sweep(long now) {
        if (windows.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        long previousSweep = lastSweepMillis.get();
        if (now - previousSweep < Duration.ofMinutes(1).toMillis()
                || !lastSweepMillis.compareAndSet(previousSweep, now)) {
            return;
        }
        Iterator<Map.Entry<String, Window>> entries = windows.entrySet().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().getValue().startMillis >= 60_000) {
                entries.remove();
            }
        }
    }

    private static final class Window {

        private final long startMillis;
        private long count;

        private Window(long startMillis, long count) {
            this.startMillis = startMillis;
            this.count = count;
        }
    }
}
