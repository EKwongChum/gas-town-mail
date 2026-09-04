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

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Detects whether a received message is in journal format.
 *
 * <p>A message is considered a journal report when it carries one of the Exchange journal report
 * headers ({@code X-MS-Journal-Report} or {@code X-MS-Exchange-Organization-Journal-Report}) or
 * embeds the original message as a {@code message/rfc822} body part. Both checks can be toggled
 * through configuration.
 */
@Component
public class JournalDetector {

    private static final String[] JOURNAL_HEADERS = {
        "X-MS-Journal-Report", "X-MS-Exchange-Organization-Journal-Report"
    };

    public boolean isJournal(
            MimeMessage message, boolean detectByHeader, boolean detectByRfc822Attachment)
            throws MessagingException, IOException {
        if (detectByHeader && hasJournalHeader(message)) {
            return true;
        }
        if (detectByRfc822Attachment && containsEmbeddedOriginal(message)) {
            return true;
        }
        return false;
    }

    private boolean hasJournalHeader(MimeMessage message) throws MessagingException {
        for (String header : JOURNAL_HEADERS) {
            if (message.getHeader(header) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEmbeddedOriginal(Part part) throws MessagingException, IOException {
        if (part.isMimeType("message/rfc822")) {
            return true;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (containsEmbeddedOriginal(bodyPart)) {
                    return true;
                }
            }
        }
        return false;
    }
}
