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

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class TestEmails {

    private TestEmails() {}

    public static MimeMessage parse(String raw) throws Exception {
        return new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    public static String withCrlf(String raw) {
        return raw.replace("\n", "\r\n");
    }

    public static String journalReport() {
        return withCrlf(
                """
                Return-Path: <>
                From: Microsoft Exchange Server <postmaster@corp.local>
                To: journal@archive.local
                Subject: Journal report
                Date: Fri, 16 Aug 2026 10:00:00 +0800
                Message-ID: <journal-wrapper-001@corp.local>
                MIME-Version: 1.0
                X-MS-Journal-Report: application/msgtnef
                Content-Type: multipart/mixed; boundary="journal-boundary"

                --journal-boundary
                Content-Type: text/plain; charset="us-ascii"

                Journal report for a message sent by alice@example.com.

                --journal-boundary
                Content-Type: message/rfc822

                From: Alice <alice@example.com>
                To: Bob <bob@example.com>
                Cc: Carol <carol@example.com>
                Subject: Quarterly report
                Date: Fri, 16 Aug 2026 09:30:00 +0800
                Message-ID: <original-123@example.com>
                MIME-Version: 1.0
                Content-Type: text/plain; charset="us-ascii"

                Hello Bob, please review the quarterly numbers.

                --journal-boundary--
                """);
    }

    public static String plainEmail() {
        return withCrlf(
                """
                From: Alice <alice@example.com>
                To: Bob <bob@example.com>
                Subject: Just a normal email
                Date: Fri, 16 Aug 2026 09:00:00 +0800
                Message-ID: <plain-001@example.com>
                MIME-Version: 1.0
                Content-Type: text/plain; charset="us-ascii"

                Nothing special here.
                """);
    }

    public static String emailWithAttachments() {
        return withCrlf(
                """
                From: Alice <alice@example.com>
                To: Bob <bob@example.com>
                Cc: Carol <carol@example.com>
                Subject: Project files
                Date: Fri, 14 Aug 2026 08:30:00 +0800
                Message-ID: <attach-001@example.com>
                MIME-Version: 1.0
                Content-Type: multipart/mixed; boundary="attach-boundary"

                --attach-boundary
                Content-Type: text/plain; charset="us-ascii"

                Please find the files attached.

                --attach-boundary
                Content-Type: application/pdf; name="report.pdf"
                Content-Disposition: attachment; filename="report.pdf"

                %PDF-1.4 fake

                --attach-boundary
                Content-Type: text/plain; name="notes.txt"
                Content-Disposition: attachment; filename="notes.txt"

                some notes

                --attach-boundary--
                """);
    }
}
