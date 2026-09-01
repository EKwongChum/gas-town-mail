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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalEmailExtractorTest {

    private final OriginalEmailExtractor extractor = new OriginalEmailExtractor();

    @Test
    void extractsEmbeddedOriginalFromJournalReport() throws Exception {
        byte[] receivedRaw = TestEmails.journalReport().getBytes(StandardCharsets.UTF_8);
        MimeMessage received = TestEmails.parse(TestEmails.journalReport());

        OriginalEmail original = extractor.extractOriginal(received, receivedRaw, true);

        String raw = new String(original.raw(), StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("Journal report");
        assertThat(raw).contains("Hello Bob, please review the quarterly numbers.");
        assertThat(original.message().getMessageID()).isEqualTo("<original-123@example.com>");
        assertThat(original.message().getFrom()[0].toString()).isEqualTo("Alice <alice@example.com>");
    }

    @Test
    void fallsBackToReceivedMessageWhenNoEmbeddedOriginal() throws Exception {
        byte[] receivedRaw = TestEmails.plainEmail().getBytes(StandardCharsets.UTF_8);
        MimeMessage received = TestEmails.parse(TestEmails.plainEmail());

        OriginalEmail original = extractor.extractOriginal(received, receivedRaw, true);

        assertThat(original.raw()).isEqualTo(receivedRaw);
        assertThat(original.message()).isSameAs(received);
    }

    @Test
    void doesNotLookForEmbeddedOriginalWhenDisabled() throws Exception {
        byte[] receivedRaw = TestEmails.journalReport().getBytes(StandardCharsets.UTF_8);
        MimeMessage received = TestEmails.parse(TestEmails.journalReport());

        OriginalEmail original = extractor.extractOriginal(received, receivedRaw, false);

        assertThat(original.raw()).isEqualTo(receivedRaw);
    }
}
