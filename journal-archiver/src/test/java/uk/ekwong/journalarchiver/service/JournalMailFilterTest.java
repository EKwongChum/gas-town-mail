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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.mailcommon.mail.EmailDetails;

class JournalMailFilterTest {

    private final JournalMailFilter filter = new JournalMailFilter();

    @Test
    void acceptsEverythingWhenNoFilterIsConfigured() {
        AppProperties.Journal journal = journal(List.of(), List.of(), List.of(), List.of());

        assertThat(filter.accepts(details("alice@example.com", null, null, null), journal))
                .isTrue();
    }

    @Test
    void matchesSenderMailboxIgnoringDisplayName() {
        AppProperties.Journal journal =
                journal(List.of("alice@example.com"), List.of(), List.of(), List.of());

        assertThat(
                        filter.accepts(
                                details(
                                        "Alice <alice@example.com>",
                                        "Alice <alice@example.com>",
                                        null,
                                        null),
                                journal))
                .isTrue();
        assertThat(filter.accepts(details("bob@example.com", null, null, null), journal)).isFalse();
    }

    @Test
    void acceptsWhenFromToOrCcContainsOneOfTheConfiguredMailboxes() {
        AppProperties.Journal journal =
                journal(
                        List.of(),
                        List.of("alice@example.com"),
                        List.of("bob@example.com", "dave@example.com"),
                        List.of("carol@example.com"));

        assertThat(
                        filter.accepts(
                                details(
                                        "Alice <alice@example.com>",
                                        "Alice <alice@example.com>",
                                        "Bob <bob@example.com>, Dave <dave@example.com>",
                                        "Carol <carol@example.com>"),
                                journal))
                .isTrue();
    }

    @Test
    void combinesAllConfiguredConditionsWithAnd() {
        AppProperties.Journal journal =
                journal(
                        List.of("alice@example.com"),
                        List.of(),
                        List.of("bob@example.com"),
                        List.of());

        assertThat(
                        filter.accepts(
                                details(
                                        "Alice <alice@example.com>",
                                        null,
                                        "Dave <dave@example.com>",
                                        null),
                                journal))
                .isFalse();
    }

    @Test
    void multipleConfiguredMailboxesAreAlternatives() {
        AppProperties.Journal journal =
                journal(
                        List.of("alice@example.com", "bob@example.com"),
                        List.of(),
                        List.of(),
                        List.of());

        assertThat(filter.accepts(details("bob@example.com", null, null, null), journal)).isTrue();
    }

    @Test
    void matchingIsCaseInsensitive() {
        AppProperties.Journal journal =
                journal(
                        List.of(),
                        List.of("ALICE@EXAMPLE.COM"),
                        List.of("Bob@Example.com"),
                        List.of());

        assertThat(
                        filter.accepts(
                                details(null, "alice@example.com", "bob@example.com", null),
                                journal))
                .isTrue();
    }

    @Test
    void missingFieldFailsWhenItsFilterIsConfigured() {
        AppProperties.Journal journal =
                journal(List.of(), List.of(), List.of(), List.of("carol@example.com"));

        assertThat(filter.accepts(details("alice@example.com", null, null, null), journal))
                .isFalse();
    }

    private AppProperties.Journal journal(
            List<String> senderEmails,
            List<String> fromEmails,
            List<String> toEmails,
            List<String> ccEmails) {
        AppProperties.Journal journal = new AppProperties.Journal();
        journal.getFilter().setSenderEmails(senderEmails);
        journal.getFilter().setFromEmails(fromEmails);
        journal.getFilter().setToEmails(toEmails);
        journal.getFilter().setCcEmails(ccEmails);
        return journal;
    }

    private EmailDetails details(String sender, String from, String to, String cc) {
        return new EmailDetails(sender, from, to, cc, null, null, null, null, List.of());
    }
}
