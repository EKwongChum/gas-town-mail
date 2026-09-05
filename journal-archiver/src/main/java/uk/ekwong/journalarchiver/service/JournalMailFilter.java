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

package uk.ekwong.journalarchiver.service;

import jakarta.mail.Address;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.mailcommon.mail.EmailDetails;

/**
 * Decides whether an archived email should be collected. Configuring a list for sender/from/to/cc
 * restricts collection to emails whose corresponding field contains one of the listed mailboxes;
 * leaving a list empty disables that restriction. All four fields are combined with AND.
 */
@Component
public class JournalMailFilter {

    /**
     * Returns whether the extracted email details pass all configured collection filters. When no
     * list is configured (or all lists are empty) every email is accepted.
     */
    public boolean accepts(EmailDetails details, AppProperties.Journal journalConfig) {
        AppProperties.Journal.Filter filter = journalConfig.getFilter();
        return matches(details.sender(), filter.getSenderEmails())
                && matches(details.from(), filter.getFromEmails())
                && matches(details.to(), filter.getToEmails())
                && matches(details.cc(), filter.getCcEmails());
    }

    private boolean matches(String fieldValue, List<String> configuredEmails) {
        Set<String> allowed = normalizeMailboxes(configuredEmails);
        if (allowed.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(fieldValue)) {
            return false;
        }
        for (String mailbox : extractMailboxes(fieldValue)) {
            if (allowed.contains(mailbox)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> normalizeMailboxes(List<String> configuredEmails) {
        Set<String> allowed = new LinkedHashSet<>();
        if (configuredEmails == null) {
            return allowed;
        }
        for (String configured : configuredEmails) {
            String mailbox = normalizeMailbox(configured);
            if (mailbox != null) {
                allowed.add(mailbox);
            }
        }
        return allowed;
    }

    private Set<String> extractMailboxes(String fieldValue) {
        Set<String> mailboxes = new LinkedHashSet<>();
        try {
            Address[] addresses = InternetAddress.parse(fieldValue, false);
            for (Address address : addresses) {
                if (address instanceof InternetAddress internetAddress
                        && StringUtils.hasText(internetAddress.getAddress())) {
                    mailboxes.add(normalizeMailbox(internetAddress.getAddress()));
                }
            }
        } catch (AddressException e) {
            String mailbox = normalizeMailbox(fieldValue);
            if (mailbox != null) {
                mailboxes.add(mailbox);
            }
        }
        return mailboxes;
    }

    private String normalizeMailbox(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String mailbox = value.trim();
        if (mailbox.regionMatches(true, 0, "mailto:", 0, "mailto:".length())) {
            mailbox = mailbox.substring("mailto:".length()).trim();
        }
        if (mailbox.startsWith("<") && mailbox.endsWith(">")) {
            mailbox = mailbox.substring(1, mailbox.length() - 1).trim();
        }
        try {
            Address[] addresses = InternetAddress.parse(mailbox, false);
            if (addresses.length > 0
                    && addresses[0] instanceof InternetAddress internetAddress
                    && StringUtils.hasText(internetAddress.getAddress())) {
                mailbox = internetAddress.getAddress().trim();
            }
        } catch (AddressException ignored) {
            // keep the trimmed raw value as the fallback
        }
        return mailbox.toLowerCase(Locale.ROOT);
    }
}
