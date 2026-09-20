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

/**
 * State of a background delivery, returned by {@code GET /api/mails/tasks/{id}}.
 *
 * @param status {@code PENDING} while the mail is being delivered, then {@code SUCCEEDED} or {@code
 *     FAILED}
 * @param response result of a successful delivery
 * @param error failure reason of a {@code FAILED} delivery
 */
public record MailSendTask(
        String id,
        Status status,
        MailSendResponse response,
        String error,
        Instant createdAt,
        Instant completedAt) {

    public enum Status {
        PENDING,
        SUCCEEDED,
        FAILED
    }

    public static MailSendTask pending(String id, Instant createdAt) {
        return new MailSendTask(id, Status.PENDING, null, null, createdAt, null);
    }

    public MailSendTask succeeded(MailSendResponse response, Instant completedAt) {
        return new MailSendTask(id, Status.SUCCEEDED, response, null, createdAt, completedAt);
    }

    public MailSendTask failed(String error, Instant completedAt) {
        return new MailSendTask(id, Status.FAILED, null, error, createdAt, completedAt);
    }
}
