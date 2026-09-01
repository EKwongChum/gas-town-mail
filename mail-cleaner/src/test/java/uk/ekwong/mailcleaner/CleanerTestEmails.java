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

package uk.ekwong.mailcleaner;

public final class CleanerTestEmails {

    private CleanerTestEmails() {
    }

    public static String emailWithAttachments() {
        return """
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
                """.replace("\n", "\r\n");
    }
}
