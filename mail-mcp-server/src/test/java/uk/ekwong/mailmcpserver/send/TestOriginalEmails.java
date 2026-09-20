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

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

/** Raw archived emails used by the reply/forward tests. */
public final class TestOriginalEmails {

    static final String ATTACHMENT_TEXT = "attachment-bytes";
    static final byte[] ATTACHMENT_BYTES = ATTACHMENT_TEXT.getBytes(StandardCharsets.UTF_8);

    private TestOriginalEmails() {}

    /** A multipart original mail with a quoted-printable body and one attachment. */
    public static byte[] withAttachment() {
        return withCrlf(
                        """
                        From: Alice <alice@example.com>
                        Reply-To: Alice Replies <alice.reply@example.com>
                        To: Bob <bob@example.com>
                        Cc: Carol <carol@example.com>
                        Subject: %s
                        Date: Sun, 16 Aug 2026 09:30:00 +0800
                        Message-ID: <original-123@example.com>
                        References: <thread-1@example.com>
                        MIME-Version: 1.0
                        Content-Type: multipart/mixed; boundary="original-boundary"

                        --original-boundary
                        Content-Type: text/plain; charset="UTF-8"
                        Content-Transfer-Encoding: base64

                        %s
                        --original-boundary
                        Content-Type: application/pdf; name="%s"
                        Content-Transfer-Encoding: base64
                        Content-Disposition: attachment; filename="%s"

                        %s
                        --original-boundary--
                        """)
                .formatted(
                        encodedWord("Quarterly report"),
                        base64("Hello Bob,\nplease review the numbers.\n"),
                        encodedWord("report.pdf"),
                        encodedWord("report.pdf"),
                        base64(ATTACHMENT_TEXT))
                .getBytes(StandardCharsets.UTF_8);
    }

    /** An original mail whose only body part is HTML. */
    public static byte[] htmlOnly() {
        return withCrlf(
                        """
                        From: Alice <alice@example.com>
                        To: Bob <bob@example.com>
                        Subject: HTML only
                        Date: Sun, 16 Aug 2026 09:30:00 +0800
                        Message-ID: <html-1@example.com>
                        MIME-Version: 1.0
                        Content-Type: text/html; charset="UTF-8"

                        <html><body><p>Hello Bob</p><p>please review</p></body></html>
                        """)
                .getBytes(StandardCharsets.UTF_8);
    }

    /** An original mail without Reply-To, References and attachments. */
    public static byte[] plain() {
        return withCrlf(
                        """
                        From: Alice <alice@example.com>
                        To: Bob <bob@example.com>
                        Subject: Plain mail
                        Date: Sun, 16 Aug 2026 09:30:00 +0800
                        Message-ID: <plain-1@example.com>
                        MIME-Version: 1.0
                        Content-Type: text/plain; charset="UTF-8"

                        Just a plain body.
                        """)
                .getBytes(StandardCharsets.UTF_8);
    }

    public static MimeMessage parse(byte[] raw) throws MessagingException {
        return new MimeMessage(
                Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
    }

    private static String withCrlf(String raw) {
        return raw.replace("\n", "\r\n");
    }

    private static String base64(String value) {
        return Base64.getMimeEncoder(76, new byte[] {'\n'})
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodedWord(String value) {
        return "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                + "?=";
    }
}
