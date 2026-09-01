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

import static org.assertj.core.api.Assertions.assertThat;

class JournalDetectorTest {

    private final JournalDetector detector = new JournalDetector();

    @Test
    void detectsJournalReportByHeader() throws Exception {
        MimeMessage message = TestEmails.parse(TestEmails.journalReport());
        assertThat(detector.isJournal(message, true, true)).isTrue();
    }

    @Test
    void doesNotDetectWhenAllDetectionSwitchesAreOff() throws Exception {
        MimeMessage message = TestEmails.parse(TestEmails.journalReport());
        assertThat(detector.isJournal(message, false, false)).isFalse();
    }

    @Test
    void detectsJournalReportByEmbeddedRfc822PartWhenHeaderDetectionIsOff() throws Exception {
        MimeMessage message = TestEmails.parse(TestEmails.journalReport());
        assertThat(detector.isJournal(message, false, true)).isTrue();
    }

    @Test
    void doesNotDetectPlainEmail() throws Exception {
        MimeMessage message = TestEmails.parse(TestEmails.plainEmail());
        assertThat(detector.isJournal(message, true, true)).isFalse();
    }
}
