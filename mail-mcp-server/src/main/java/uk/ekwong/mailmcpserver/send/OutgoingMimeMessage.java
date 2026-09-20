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
import org.springframework.util.StringUtils;

/**
 * A {@link MimeMessage} that keeps the Message-ID of the send command. The mail client would
 * otherwise generate (and on every save overwrite) the header, so the value returned to the caller
 * could differ from the one the recipient sees.
 */
final class OutgoingMimeMessage extends MimeMessage {

    private final String messageId;

    OutgoingMimeMessage(Session session, String messageId) {
        super(session);
        this.messageId = messageId;
    }

    @Override
    protected void updateMessageID() throws MessagingException {
        if (getHeader("Message-ID") != null) {
            return;
        }
        if (StringUtils.hasText(messageId)) {
            setHeader("Message-ID", messageId);
        } else {
            super.updateMessageID();
        }
    }
}
