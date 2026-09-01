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

package uk.ekwong.mailcommon.mail;

import java.time.Instant;

/**
 * Payload published to RocketMQ after an email has been archived, so other
 * services know the email metadata (MongoDB) and the raw email (object
 * storage) are available. The {@code id} is both the MongoDB document id and
 * the object storage key.
 */
public record MailMetaMessage(
        String id,
        String sender,
        String from,
        String to,
        String cc,
        String subject,
        String messageId,
        String envelopeSender,
        String objectKey,
        Instant receivedAt,
        Instant createdAt,
        Instant updatedAt,
        long modificationCount
) {
}
