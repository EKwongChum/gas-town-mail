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

import org.junit.jupiter.api.Test;

class MailSendRateLimiterTest {

    private final MailSendProperties properties = new MailSendProperties();
    private final MailSendRateLimiter limiter = new MailSendRateLimiter(properties);

    @Test
    void isDisabledUntilALimitIsConfigured() {
        assertThat(limiter.enabled()).isFalse();

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.acquire("10.0.0.1").allowed()).isTrue();
        }
    }

    @Test
    void limitsRequestsPerClient() {
        properties.getRateLimit().setRequestsPerMinute(2);

        assertThat(limiter.acquire("10.0.0.1").allowed()).isTrue();
        assertThat(limiter.acquire("10.0.0.1").allowed()).isTrue();

        MailSendRateLimiter.Decision rejected = limiter.acquire("10.0.0.1");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);

        // another client keeps its own window
        assertThat(limiter.acquire("10.0.0.2").allowed()).isTrue();
    }
}
