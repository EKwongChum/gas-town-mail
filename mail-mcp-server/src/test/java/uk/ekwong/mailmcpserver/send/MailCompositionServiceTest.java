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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import uk.ekwong.mailcommon.storage.ObjectStorageService;
import uk.ekwong.mailmcpserver.download.OriginalMailNotFoundException;

class MailCompositionServiceTest {

    private static final String ARCHIVE_ID =
            "YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=";

    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private MailSendProperties properties;
    private MailCompositionService service;

    @BeforeEach
    void setUp() {
        properties = new MailSendProperties();
        service =
                new MailCompositionService(
                        new MailSendRequestMapper(properties),
                        storage,
                        new OriginalEmailContentParser());
    }

    @Test
    void replyTargetsTheOriginalReplyToAndQuotesTheOriginalBody() {
        givenArchivedMail();

        MailSendCommand command = service.composeReply(replyRequest(null, null, null));

        assertThat(command.to()).containsExactly("Alice Replies <alice.reply@example.com>");
        assertThat(command.cc()).isEmpty();
        assertThat(command.subject()).isEqualTo("Re: Quarterly report");
        assertThat(command.body())
                .startsWith(
                        "Thanks for the update\n\nOn 2026-08-16T01:30:00Z, Alice <alice@example.com> wrote:\n")
                .contains("> Hello Bob,")
                .contains("> please review the numbers.");
        assertThat(command.inReplyTo()).isEqualTo("<original-123@example.com>");
        assertThat(command.references())
                .containsExactly("<thread-1@example.com>", "<original-123@example.com>");
        assertThat(command.attachments()).isEmpty();
    }

    @Test
    void replyKeepsCallerRecipientsAndSubject() {
        givenArchivedMail();

        MailSendCommand command =
                service.composeReply(
                        replyRequest(
                                List.of("Dave <dave@example.com>"),
                                List.of("erin@example.com"),
                                "Re: Quarterly report"));

        assertThat(command.to()).containsExactly("Dave <dave@example.com>");
        assertThat(command.cc()).containsExactly("erin@example.com");
        assertThat(command.subject()).isEqualTo("Re: Quarterly report");
    }

    @Test
    void replyAllCopiesTheOriginalRecipientsExceptTheOwnAddress() {
        givenArchivedMail();

        MailReplyRequest request =
                new MailReplyRequest(
                        ARCHIVE_ID,
                        "smtp.example.com",
                        587,
                        "alice@example.com",
                        "secret",
                        null,
                        "Alice <alice@example.com>",
                        List.of(),
                        List.of(),
                        null,
                        "Thanks",
                        null,
                        List.of(),
                        true,
                        null,
                        null);

        MailSendCommand command = service.composeReply(request);

        assertThat(command.to()).containsExactly("Alice Replies <alice.reply@example.com>");
        assertThat(command.cc())
                .containsExactly("Bob <bob@example.com>", "Carol <carol@example.com>");
    }

    @Test
    void replyCanCarryTheOriginalAttachments() {
        givenArchivedMail();

        MailSendCommand command =
                service.composeReply(
                        new MailReplyRequest(
                                ARCHIVE_ID,
                                "smtp.example.com",
                                587,
                                "alice@example.com",
                                "secret",
                                null,
                                null,
                                List.of(),
                                List.of(),
                                null,
                                "Thanks",
                                null,
                                List.of(),
                                null,
                                Boolean.FALSE,
                                Boolean.TRUE));

        assertThat(command.body()).isEqualTo("Thanks");
        assertThat(command.attachments()).hasSize(1);
        assertThat(command.attachments().get(0).content())
                .isEqualTo(TestOriginalEmails.ATTACHMENT_BYTES);
    }

    @Test
    void forwardEmbedsTheOriginalAndCarriesItsAttachments() {
        givenArchivedMail();

        MailSendCommand command =
                service.composeForward(
                        forwardRequest(List.of("Dave <dave@example.com>"), null, null));

        assertThat(command.subject()).isEqualTo("Fwd: Quarterly report");
        assertThat(command.body())
                .startsWith("See below\n\n---------- Forwarded message ---------\n")
                .contains("From: Alice <alice@example.com>")
                .contains("Date: 2026-08-16T01:30:00Z")
                .contains("Subject: Quarterly report")
                .contains("To: Bob <bob@example.com>")
                .contains("Cc: Carol <carol@example.com>")
                .contains("Hello Bob,")
                .contains("please review the numbers.");
        assertThat(command.attachments()).hasSize(1);
        assertThat(command.attachments().get(0).filename()).isEqualTo("report.pdf");
        assertThat(command.inReplyTo()).isNull();
        assertThat(command.references()).isEmpty();
    }

    @Test
    void forwardKeepsAnExistingPrefixAndCanSkipTheOriginalParts() {
        givenArchivedMail();

        MailSendCommand command =
                service.composeForward(
                        forwardRequest(
                                List.of("dave@example.com"),
                                "Fwd: Quarterly report",
                                Boolean.FALSE));

        assertThat(command.subject()).isEqualTo("Fwd: Quarterly report");
        assertThat(command.body()).isEqualTo("See below");
        assertThat(command.attachments()).hasSize(1);
    }

    @Test
    void forwardWithoutRecipientsIsRejected() {
        givenArchivedMail();

        assertThatThrownBy(() -> service.composeForward(forwardRequest(List.of(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("to must not be empty when forwarding");
    }

    @Test
    void forwardedAttachmentsMustRespectTheAttachmentLimits() {
        givenArchivedMail();
        properties.setMaxAttachmentSize(DataSize.ofBytes(4));

        assertThatThrownBy(
                        () ->
                                service.composeForward(
                                        forwardRequest(List.of("d@example.com"), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-attachment limit")
                .hasMessageContaining("includeOriginalAttachments=false");
    }

    @Test
    void missingArchivedMailIsReportedAsNotFound() {
        when(storage.readIfPresent(ARCHIVE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.composeReply(replyRequest(null, null, null)))
                .isInstanceOf(OriginalMailNotFoundException.class)
                .hasMessageContaining(ARCHIVE_ID);
        assertThatThrownBy(
                        () ->
                                service.composeForward(
                                        forwardRequest(List.of("d@example.com"), null, null)))
                .isInstanceOf(OriginalMailNotFoundException.class);
    }

    @Test
    void blankArchiveIdIsRejected() {
        assertThatThrownBy(() -> service.composeReply(replyRequest(null, null, null, " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
    }

    private void givenArchivedMail() {
        when(storage.readIfPresent(ARCHIVE_ID))
                .thenReturn(Optional.of(TestOriginalEmails.withAttachment()));
    }

    private MailReplyRequest replyRequest(List<String> to, List<String> cc, String subject) {
        return replyRequest(to, cc, subject, ARCHIVE_ID);
    }

    private MailReplyRequest replyRequest(
            List<String> to, List<String> cc, String subject, String id) {
        return new MailReplyRequest(
                id,
                "smtp.example.com",
                587,
                "alice@example.com",
                "secret",
                null,
                null,
                to,
                cc,
                subject,
                "Thanks for the update",
                null,
                List.of(),
                null,
                null,
                null);
    }

    private MailForwardRequest forwardRequest(
            List<String> to, String subject, Boolean includeOriginalBody) {
        return new MailForwardRequest(
                ARCHIVE_ID,
                "smtp.example.com",
                587,
                "alice@example.com",
                "secret",
                null,
                null,
                to,
                List.of(),
                subject,
                "See below",
                null,
                List.of(),
                includeOriginalBody,
                null);
    }
}
