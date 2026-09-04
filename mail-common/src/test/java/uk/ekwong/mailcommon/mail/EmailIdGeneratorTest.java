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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EmailIdGeneratorTest {

    @Test
    void generatesBase64SenderAndMessageIdCombination() {
        String sender = "Alice <alice@example.com>";
        String messageId = "<original-123@example.com>";

        String id = EmailIdGenerator.generate(sender, messageId);

        String expected =
                Base64.getEncoder().encodeToString(sender.getBytes(StandardCharsets.UTF_8))
                        + "_"
                        + Base64.getEncoder()
                                .encodeToString(messageId.getBytes(StandardCharsets.UTF_8));
        assertThat(id).isEqualTo(expected);
        assertThat(id).doesNotContain("/").doesNotContain("+");
    }

    @Test
    void treatsNullValuesAsEmpty() {
        String id = EmailIdGenerator.generate(null, null);
        String expected =
                Base64.getEncoder().encodeToString(new byte[0])
                        + "_"
                        + Base64.getEncoder().encodeToString(new byte[0]);
        assertThat(id).isEqualTo(expected);
    }

    @Test
    void trimsWhitespace() {
        String id = EmailIdGenerator.generate("  sender@example.com ", " <x@y> ");
        assertThat(id).isEqualTo(EmailIdGenerator.generate("sender@example.com", "<x@y>"));
    }
}
