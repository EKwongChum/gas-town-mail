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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OriginalEmailContentParserTest {

    private final OriginalEmailContentParser parser = new OriginalEmailContentParser();

    @Test
    void readsHeadersBodyAndAttachments() {
        byte[] raw = TestOriginalEmails.withAttachment();
        OriginalEmailContent content = parser.parse(raw);

        assertThat(content.from()).isEqualTo("Alice <alice@example.com>");
        assertThat(content.replyTo()).isEqualTo("Alice Replies <alice.reply@example.com>");
        assertThat(content.to()).containsExactly("Bob <bob@example.com>");
        assertThat(content.cc()).containsExactly("Carol <carol@example.com>");
        assertThat(content.subject()).isEqualTo("Quarterly report");
        assertThat(content.messageId()).isEqualTo("<original-123@example.com>");
        assertThat(content.references()).containsExactly("<thread-1@example.com>");
        assertThat(content.date()).isEqualTo(Instant.parse("2026-08-16T01:30:00Z"));
        assertThat(content.textBody()).contains("please review the numbers.");

        assertThat(content.attachmentNames()).containsExactly("report.pdf");

        assertThat(parser.readAttachments(raw)).hasSize(1);
        MailAttachment attachment = parser.readAttachments(raw).get(0);
        assertThat(attachment.filename()).isEqualTo("report.pdf");
        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(attachment.content()).isEqualTo(TestOriginalEmails.ATTACHMENT_BYTES);
    }

    @Test
    void fallsBackToTextOfHtmlBody() {
        OriginalEmailContent content = parser.parse(TestOriginalEmails.htmlOnly());

        assertThat(content.textBody()).isEqualTo("Hello Bob\n\nplease review");
        assertThat(content.attachmentNames()).isEmpty();
        assertThat(content.references()).isEmpty();
    }

    @Test
    void readsPlainMailWithoutReplyToOrAttachments() {
        OriginalEmailContent content = parser.parse(TestOriginalEmails.plain());

        assertThat(content.replyTo()).isNull();
        assertThat(content.textBody()).contains("Just a plain body.");
        assertThat(content.attachmentNames()).isEmpty();
    }

    @Test
    void handlesEmptyInput() {
        OriginalEmailContent content = parser.parse(new byte[0]);

        assertThat(content.from()).isNull();
        assertThat(content.textBody()).isEmpty();
        assertThat(content.attachmentNames()).isEmpty();
    }
}
