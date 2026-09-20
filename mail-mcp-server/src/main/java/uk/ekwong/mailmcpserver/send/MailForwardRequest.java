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
 * Request body of {@code POST /api/mails/forward}: the send parameters plus the archive id of the
 * archived mail that is being forwarded (the id returned by the MCP tools {@code search_mails} /
 * {@code get_mail_by_id}).
 *
 * <p>Unlike a reply, a forward always needs explicit recipients: {@code to} is required.
 *
 * @param id archive id (MongoDB {@code _id} / object storage key) of the mail to forward
 * @param includeOriginalBody whether to include the forwarded original body; optional, defaults to
 *     {@code true}
 * @param includeOriginalAttachments whether to carry over the original attachments; optional,
 *     defaults to {@code true}
 */
public record MailForwardRequest(
        String id,
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
        List<MailAttachmentRequest> attachments,
        Boolean includeOriginalBody,
        Boolean includeOriginalAttachments)
        implements MailSendFields {}
