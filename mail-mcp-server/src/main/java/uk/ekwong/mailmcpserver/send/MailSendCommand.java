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
 * A fully validated mail that is ready to be handed to the SMTP client. Reply and forward
 * composition start from the command built from the request and replace recipients, subject, body,
 * attachments and threading headers.
 *
 * @param inReplyTo value of the {@code In-Reply-To} header, {@code null} for a new mail
 * @param references values of the {@code References} header, empty for a new mail
 * @param envelopeFrom SMTP {@code MAIL FROM} address, {@code null} to use the {@code From} header
 * @param messageId value of the {@code Message-ID} header, generated from the sender domain
 */
public record MailSendCommand(
        SmtpSettings smtp,
        String from,
        List<String> to,
        List<String> cc,
        String subject,
        String body,
        boolean html,
        List<MailAttachment> attachments,
        String inReplyTo,
        List<String> references,
        String envelopeFrom,
        String messageId) {

    public MailSendCommand {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        references = references == null ? List.of() : List.copyOf(references);
        subject = subject == null ? "" : subject;
        body = body == null ? "" : body;
    }

    public MailSendCommand withRecipients(List<String> newTo, List<String> newCc) {
        return new MailSendCommand(
                smtp,
                from,
                newTo,
                newCc,
                subject,
                body,
                html,
                attachments,
                inReplyTo,
                references,
                envelopeFrom,
                messageId);
    }

    public MailSendCommand withSubject(String newSubject) {
        return new MailSendCommand(
                smtp,
                from,
                to,
                cc,
                newSubject,
                body,
                html,
                attachments,
                inReplyTo,
                references,
                envelopeFrom,
                messageId);
    }

    public MailSendCommand withBody(String newBody) {
        return new MailSendCommand(
                smtp,
                from,
                to,
                cc,
                subject,
                newBody,
                html,
                attachments,
                inReplyTo,
                references,
                envelopeFrom,
                messageId);
    }

    public MailSendCommand withAttachments(List<MailAttachment> newAttachments) {
        return new MailSendCommand(
                smtp,
                from,
                to,
                cc,
                subject,
                body,
                html,
                newAttachments,
                inReplyTo,
                references,
                envelopeFrom,
                messageId);
    }

    public MailSendCommand withThreading(String newInReplyTo, List<String> newReferences) {
        return new MailSendCommand(
                smtp,
                from,
                to,
                cc,
                subject,
                body,
                html,
                attachments,
                newInReplyTo,
                newReferences,
                envelopeFrom,
                messageId);
    }

    /** Total size of all attachments in bytes. */
    public long attachmentBytes() {
        return attachments.stream().mapToLong(MailAttachment::size).sum();
    }
}
