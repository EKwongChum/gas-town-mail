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

import java.util.List;

/**
 * Request body of {@code POST /api/mails/send}: everything needed to deliver one mail through the
 * SMTP server given in the request.
 *
 * @param smtpHost SMTP server host name or IP address (required)
 * @param smtpPort SMTP server port, e.g. 25 / 465 / 587 (required)
 * @param smtpUsername SMTP account; blank means the server is used without authentication
 * @param smtpPassword SMTP password; required when {@code smtpUsername} is given
 * @param smtpEncryption {@code none} / {@code starttls} / {@code ssl}; optional, defaults to
 *     implicit TLS on port 465 and opportunistic STARTTLS otherwise
 * @param from From address (display name allowed); optional, defaults to {@code smtpUsername}
 * @param to To recipients (required)
 * @param cc Cc recipients (optional)
 * @param subject mail subject (optional)
 * @param content mail body (optional)
 * @param html whether {@code content} is HTML; optional, defaults to {@code false}
 * @param attachments attachments, each at most 10 MB and 20 MB in total
 */
public record MailSendRequest(
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpPassword,
        String smtpEncryption,
        String from,
        List<String> to,
        List<String> cc,
        String subject,
        String content,
        Boolean html,
        List<MailAttachmentRequest> attachments)
        implements MailSendFields {}
