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

import jakarta.mail.Address;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.util.StringUtils;

/**
 * Generates the archive id: {@code Base64(sender address)_Base64(Message-Id)}. Only the mailbox
 * part of the sender is used, so {@code Alice <alice@example.com>} and {@code alice@example.com}
 * produce the same id. The same id is used as the MongoDB document id and the object storage key.
 */
public final class EmailIdGenerator {

    private EmailIdGenerator() {}

    public static String generate(String sender, String messageId) {
        return base64(senderAddress(sender)) + "_" + base64(normalize(messageId));
    }

    /**
     * Extracts the mailbox from an address header value, dropping the display name, angle brackets
     * and trailing comments: {@code Alice <alice@example.com>}, {@code <alice@example.com>} and
     * {@code alice@example.com (Alice)} all yield {@code alice@example.com}.
     */
    private static String senderAddress(String sender) {
        String value = normalize(sender);
        if (value.isEmpty()) {
            return value;
        }
        try {
            Address[] addresses = InternetAddress.parse(value, false);
            if (addresses.length > 0
                    && addresses[0] instanceof InternetAddress address
                    && StringUtils.hasText(address.getAddress())) {
                return address.getAddress().trim();
            }
        } catch (AddressException e) {
            // fall through to the plain angle-bracket extraction below
        }
        int open = value.lastIndexOf('<');
        int close = open < 0 ? -1 : value.indexOf('>', open + 1);
        if (open >= 0 && close > open) {
            return value.substring(open + 1, close).trim();
        }
        int comment = value.indexOf('(');
        return comment > 0 ? value.substring(0, comment).trim() : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
