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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Test double that captures the serialized MIME bytes instead of talking to an SMTP server; set
 * {@link #failWith} to simulate a server that cannot be reached.
 */
public final class CapturingMailTransport implements MailTransport {

    private final List<byte[]> sent = new ArrayList<>();
    private MessagingException failWith;

    @Override
    public MimeMessage newMessage(SmtpSettings settings) {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public void send(SmtpSettings settings, MimeMessage message) throws MessagingException {
        if (failWith != null) {
            throw failWith;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            message.writeTo(out);
        } catch (IOException e) {
            throw new MessagingException("Could not serialize the message", e);
        }
        sent.add(out.toByteArray());
    }

    public void failWith(MessagingException failure) {
        this.failWith = failure;
    }

    public boolean isEmpty() {
        return sent.isEmpty();
    }

    /** The last message that was handed to the transport, as it would appear on the wire. */
    public MimeMessage lastMessage() throws MessagingException {
        byte[] raw = sent.get(sent.size() - 1);
        return new MimeMessage(
                Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
    }
}
