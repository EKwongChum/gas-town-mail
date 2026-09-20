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
import jakarta.mail.internet.MimeMessage;

/**
 * Delivers a composed MIME message through an SMTP server. Kept behind an interface so the mail
 * construction can be tested without a network connection.
 */
public interface MailTransport {

    /** Creates a MIME message bound to a session configured from the given SMTP settings. */
    MimeMessage newMessage(SmtpSettings settings);

    /** Connects to the server and sends the message to all of its recipients. */
    void send(SmtpSettings settings, MimeMessage message) throws MessagingException;
}
