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
 * The parts of an archived original email that a reply or forward needs: threading headers,
 * recipients, the plain text body and the attachments.
 *
 * @param from value of the {@code From} header
 * @param replyTo value of the {@code Reply-To} header, {@code null} when the mail has none
 * @param references values of the {@code References} header, newest last
 * @param date value of the {@code Date} header
 * @param textBody plain text body extracted from the mail, empty when the mail has no text part
 */
public record OriginalEmailContent(
        String from,
        String replyTo,
        List<String> to,
        List<String> cc,
        String subject,
        String messageId,
        List<String> references,
        Instant date,
        String textBody,
        List<MailAttachment> attachments) {

    public OriginalEmailContent {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        references = references == null ? List.of() : List.copyOf(references);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        textBody = textBody == null ? "" : textBody;
    }
}
