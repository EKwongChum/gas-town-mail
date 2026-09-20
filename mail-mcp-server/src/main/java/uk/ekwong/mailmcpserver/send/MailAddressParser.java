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

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Parses and validates the address fields of a send request with the same strictness as an SMTP
 * client: each entry may hold one address or a comma separated list such as {@code Alice
 * <alice@example.com>, bob@example.com}. Results are de-duplicated by address, keeping the first
 * spelling that was supplied.
 */
final class MailAddressParser {

    private MailAddressParser() {}

    /**
     * Parses all values of one address field (to / cc), returning the addresses in the order they
     * were supplied, without duplicates.
     */
    static List<String> parseList(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            for (InternetAddress address : parse(value, field)) {
                unique.putIfAbsent(bareAddress(address), address.toUnicodeString());
            }
        }
        return List.copyOf(unique.values());
    }

    /** Parses an address field that must hold exactly one address, e.g. the From header. */
    static String parseSingle(String value, String field) {
        List<InternetAddress> addresses = parse(value, field);
        if (addresses.size() != 1) {
            throw new IllegalArgumentException(field + " must contain exactly one address");
        }
        return addresses.get(0).toUnicodeString();
    }

    /** Returns the mailbox part of every address in the given field, for recipient comparisons. */
    static List<String> bareAddresses(List<String> values, String field) {
        List<String> bare = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            for (InternetAddress address : parse(value, field)) {
                bare.add(bareAddress(address));
            }
        }
        return bare;
    }

    /** Returns the mailbox part (no display name, lower case) of a single address string. */
    static String bareAddress(String value, String field) {
        List<InternetAddress> addresses = parse(value, field);
        return addresses.isEmpty() ? "" : bareAddress(addresses.get(0));
    }

    private static String bareAddress(InternetAddress internetAddress) {
        String mailbox = internetAddress.getAddress();
        return mailbox == null ? "" : mailbox.toLowerCase(Locale.ROOT);
    }

    private static List<InternetAddress> parse(String value, String field) {
        InternetAddress[] addresses;
        try {
            addresses = InternetAddress.parse(value, true);
        } catch (AddressException e) {
            throw new IllegalArgumentException(
                    field + " contains an invalid address: " + e.getMessage());
        }
        List<InternetAddress> result = new ArrayList<>(addresses.length);
        for (InternetAddress address : addresses) {
            try {
                address.validate();
            } catch (AddressException e) {
                throw new IllegalArgumentException(
                        field + " contains an invalid address '" + value + "': " + e.getMessage());
            }
            result.add(address);
        }
        return result;
    }
}
