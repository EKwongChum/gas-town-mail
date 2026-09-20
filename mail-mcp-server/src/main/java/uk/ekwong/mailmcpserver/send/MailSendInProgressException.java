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

/**
 * Thrown when a request reuses an {@code Idempotency-Key} whose delivery is still running (mapped
 * to HTTP {@code 409}).
 */
public class MailSendInProgressException extends RuntimeException {

    public MailSendInProgressException(String idempotencyKey) {
        super(
                "A mail with the idempotency key '"
                        + idempotencyKey
                        + "' is still being sent; retry later or use a new key");
    }
}
