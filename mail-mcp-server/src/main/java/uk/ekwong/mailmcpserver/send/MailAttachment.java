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
 * An attachment of an outgoing mail. The content is streamed from its source while the mail is
 * written, so only the size is held in memory.
 *
 * @param filename file name as it should appear in the mail client
 * @param contentType MIME content type, e.g. {@code application/pdf}
 * @param size size of the content in bytes
 * @param content source of the attachment bytes, opened once per read
 */
public record MailAttachment(
        String filename, String contentType, long size, AttachmentContent content) {

    /** Creates an attachment whose content is already in memory. */
    public static MailAttachment of(String filename, String contentType, byte[] content) {
        byte[] bytes = content == null ? new byte[0] : content;
        return new MailAttachment(
                filename, contentType, bytes.length, AttachmentContent.ofBytes(bytes));
    }
}
