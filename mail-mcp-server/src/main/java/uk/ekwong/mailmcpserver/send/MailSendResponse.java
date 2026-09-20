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
import java.util.List;

/**
 * Result of a successful send. The SMTP server accepted the mail for the listed recipients; the
 * generated {@code messageId} is the value used for the {@code Message-ID} header, so a reply to
 * this mail can be correlated again.
 */
public record MailSendResponse(
        String messageId,
        String from,
        List<String> to,
        List<String> cc,
        String subject,
        int attachmentCount,
        long attachmentBytes,
        Instant sentAt) {}
