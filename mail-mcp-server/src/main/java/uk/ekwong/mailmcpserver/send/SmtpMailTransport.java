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

import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends mail with the Jakarta Mail SMTP provider (angus-mail, already on the classpath through
 * mail-common). Every request configures its own short-lived session from the SMTP coordinates it
 * supplied, so no credentials are kept in the application context.
 */
@Component
public class SmtpMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailTransport.class);

    private final MailSendProperties properties;

    public SmtpMailTransport(MailSendProperties properties) {
        this.properties = properties;
    }

    @Override
    public MimeMessage newMessage(SmtpSettings settings) {
        return new MimeMessage(session(settings));
    }

    @Override
    public void send(SmtpSettings settings, MimeMessage message) throws MessagingException {
        Session session = message.getSession() == null ? session(settings) : message.getSession();
        Transport transport = session.getTransport();
        try {
            if (settings.authenticated()) {
                transport.connect(
                        settings.host(), settings.port(), settings.username(), settings.password());
            } else {
                transport.connect(settings.host(), settings.port(), null, null);
            }
            transport.sendMessage(message, message.getAllRecipients());
        } finally {
            closeQuietly(transport);
        }
    }

    private Session session(SmtpSettings settings) {
        Properties mailProperties = new Properties();
        mailProperties.put("mail.transport.protocol", "smtp");
        mailProperties.put("mail.smtp.host", settings.host());
        mailProperties.put("mail.smtp.port", String.valueOf(settings.port()));
        mailProperties.put("mail.smtp.auth", String.valueOf(settings.authenticated()));
        mailProperties.put(
                "mail.smtp.connectiontimeout",
                String.valueOf(properties.getConnectTimeout().toMillis()));
        mailProperties.put(
                "mail.smtp.timeout", String.valueOf(properties.getReadTimeout().toMillis()));
        mailProperties.put(
                "mail.smtp.writetimeout", String.valueOf(properties.getWriteTimeout().toMillis()));
        switch (settings.encryption()) {
            case NONE -> {
                // plain connection, no TLS
            }
            case SSL -> mailProperties.put("mail.smtp.ssl.enable", "true");
            case STARTTLS -> {
                mailProperties.put("mail.smtp.starttls.enable", "true");
                mailProperties.put("mail.smtp.starttls.required", "true");
            }
            case AUTO -> {
                if (settings.port() == 465) {
                    mailProperties.put("mail.smtp.ssl.enable", "true");
                } else {
                    mailProperties.put("mail.smtp.starttls.enable", "true");
                }
            }
        }
        return settings.authenticated()
                ? Session.getInstance(mailProperties, authenticator(settings))
                : Session.getInstance(mailProperties);
    }

    private Authenticator authenticator(SmtpSettings settings) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(settings.username(), settings.password());
            }
        };
    }

    private void closeQuietly(Transport transport) {
        try {
            transport.close();
        } catch (MessagingException e) {
            log.debug("Closing the SMTP connection failed: {}", e.getMessage());
        }
    }
}
