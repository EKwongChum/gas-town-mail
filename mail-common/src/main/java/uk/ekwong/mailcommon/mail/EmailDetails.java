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
import java.util.List;

/**
 * Basic information extracted from an email: header fields plus the MIME
 * content type and the names of all attachments.
 */
public record EmailDetails(
        String sender,
        String from,
        String to,
        String cc,
        String messageId,
        Instant receivedTime,
        String subject,
        String contentType,
        List<String> attachmentNames
) {
}
