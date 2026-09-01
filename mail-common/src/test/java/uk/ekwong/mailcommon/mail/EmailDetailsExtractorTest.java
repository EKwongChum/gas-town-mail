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

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDetailsExtractorTest {

    private final EmailDetailsExtractor extractor = new EmailDetailsExtractor();

    @Test
    void extractsAllBasicFields() throws Exception {
        MimeMessage message = TestEmails.parse("""
                Sender: sender@example.com
                From: Alice <alice@example.com>
                To: Bob <bob@example.com>, Dave <dave@example.com>
                Cc: Carol <carol@example.com>
                Subject: =?UTF-8?B?5Lit5paH5Li76aKY?=
                Message-ID: <m-1@example.com>
                Date: Fri, 14 Aug 2026 08:00:00 +0000

                body
                """);

        EmailDetails details = extractor.extract(message, "envelope@example.com", "header");

        assertThat(details.sender()).isEqualTo("sender@example.com");
        assertThat(details.from()).isEqualTo("Alice <alice@example.com>");
        assertThat(details.to()).isEqualTo("Bob <bob@example.com>, Dave <dave@example.com>");
        assertThat(details.cc()).isEqualTo("Carol <carol@example.com>");
        assertThat(details.subject()).isEqualTo("中文主题");
        assertThat(details.messageId()).isEqualTo("<m-1@example.com>");
        assertThat(details.receivedTime()).isEqualTo(Instant.parse("2026-08-14T08:00:00Z"));
        assertThat(details.contentType()).isEqualTo("text/plain");
        assertThat(details.attachmentNames()).isEmpty();
    }

    @Test
    void extractsReceivedTimeContentTypeAndAttachmentNames() throws Exception {
        MimeMessage message = TestEmails.parse(TestEmails.emailWithAttachments());

        EmailDetails details = extractor.extract(message, null, "header");

        assertThat(details.receivedTime()).isEqualTo(Instant.parse("2026-08-14T00:30:00Z"));
        assertThat(details.contentType()).contains("multipart/mixed");
        assertThat(details.attachmentNames()).containsExactly("report.pdf", "notes.txt");
        assertThat(details.sender()).isEqualTo("Alice <alice@example.com>");
    }

    @Test
    void fallsBackToFromWhenNoSenderHeader() throws Exception {
        MimeMessage message = TestEmails.parse("""
                From: alice@example.com
                To: bob@example.com
                Message-ID: <m-2@example.com>

                body
                """);

        EmailDetails details = extractor.extract(message, "envelope@example.com", "header");

        assertThat(details.sender()).isEqualTo("alice@example.com");
        assertThat(details.from()).isEqualTo("alice@example.com");
    }

    @Test
    void usesEnvelopeSenderWhenConfigured() throws Exception {
        MimeMessage message = TestEmails.parse("""
                From: alice@example.com
                To: bob@example.com
                Message-ID: <m-3@example.com>

                body
                """);

        EmailDetails details = extractor.extract(message, "bounce-handler@relay.example", "envelope");

        assertThat(details.sender()).isEqualTo("bounce-handler@relay.example");
        assertThat(details.from()).isEqualTo("alice@example.com");
    }

    @Test
    void leavesMessageIdNullWhenMissing() throws Exception {
        MimeMessage message = TestEmails.parse("""
                From: alice@example.com
                To: bob@example.com

                body
                """);

        EmailDetails details = extractor.extract(message, null, "header");

        assertThat(details.messageId()).isNull();
    }
}
