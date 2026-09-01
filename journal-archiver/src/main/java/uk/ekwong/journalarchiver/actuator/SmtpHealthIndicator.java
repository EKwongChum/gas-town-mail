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

package uk.ekwong.journalarchiver.actuator;

import uk.ekwong.journalarchiver.smtp.SmtpReceiver;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports whether the embedded SMTP server is accepting connections.
 */
@Component
public class SmtpHealthIndicator implements HealthIndicator {

    private final SmtpReceiver smtpReceiver;

    public SmtpHealthIndicator(SmtpReceiver smtpReceiver) {
        this.smtpReceiver = smtpReceiver;
    }

    @Override
    public Health health() {
        if (smtpReceiver.isRunning()) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "SMTP server is not running").build();
    }
}
