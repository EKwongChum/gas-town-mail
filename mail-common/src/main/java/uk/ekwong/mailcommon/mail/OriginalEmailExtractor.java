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
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts the original email from a journal report. If the journal report embeds the original
 * message as a {@code message/rfc822} body part, the raw bytes of that part are used; otherwise the
 * received message itself is treated as the original email.
 */
@Component
public class OriginalEmailExtractor {

    private static final Logger log = LoggerFactory.getLogger(OriginalEmailExtractor.class);

    public OriginalEmail extractOriginal(
            MimeMessage received, byte[] receivedRaw, boolean extractEmbedded)
            throws MessagingException, IOException {
        if (extractEmbedded) {
            Optional<byte[]> embedded = findFirstEmbeddedOriginal(received);
            if (embedded.isPresent()) {
                byte[] raw = embedded.get();
                MimeMessage original =
                        new MimeMessage(
                                Session.getInstance(new Properties()),
                                new ByteArrayInputStream(raw));
                log.info("Extracted embedded original email ({} bytes)", raw.length);
                return new OriginalEmail(raw, original);
            }
            log.info(
                    "No embedded message/rfc822 part found; treating the received message as the original email");
        }
        return new OriginalEmail(receivedRaw, received);
    }

    private Optional<byte[]> findFirstEmbeddedOriginal(Part part)
            throws MessagingException, IOException {
        if (part.isMimeType("message/rfc822")) {
            try (InputStream in = part.getInputStream()) {
                return Optional.of(readAll(in));
            }
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                Optional<byte[]> found = findFirstEmbeddedOriginal(bodyPart);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
