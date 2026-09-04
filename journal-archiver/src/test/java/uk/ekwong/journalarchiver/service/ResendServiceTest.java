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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;
import uk.ekwong.journalarchiver.model.ResendRequest;
import uk.ekwong.journalarchiver.model.ResendResponse;
import uk.ekwong.journalarchiver.notify.MailMetaPublisher;
import uk.ekwong.journalarchiver.repository.JournalEmailInfoRepository;

class ResendServiceTest {

    private final JournalEmailInfoRepository repository = mock(JournalEmailInfoRepository.class);
    private final MailMetaPublisher publisher = mock(MailMetaPublisher.class);
    private final Executor directExecutor = Runnable::run;
    private final ResendService service =
            new ResendService(repository, publisher, directExecutor, new AppProperties());

    private final Instant timeGe = Instant.parse("2026-08-16T00:00:00Z");
    private final Instant timeLt = Instant.parse("2026-08-17T00:00:00Z");

    @Test
    void resendsExplicitIdsDirectly() {
        JournalEmailInfo info = info("id-1");
        when(repository.findById("id-1")).thenReturn(Optional.of(info));
        when(publisher.publish(info)).thenReturn(true);

        ResendResponse response = service.resend(new ResendRequest(null, null, List.of("id-1")));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isZero();
        assertThat(response.ids()).containsExactly("id-1");
        verify(publisher).publish(info);
        verify(repository, never()).findByCreatedAtGreaterThanEqual(any());
        verify(repository, never()).findByCreatedAtLessThan(any());
        verify(repository, never())
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any());
    }

    @Test
    void countsMissingIdsAsFailed() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        ResendResponse response = service.resend(new ResendRequest(null, null, List.of("missing")));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.succeeded()).isZero();
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.notFoundIds()).containsExactly("missing");
        assertThat(response.ids()).isEmpty();
        verifyNoInteractions(publisher);
    }

    @Test
    void countsFailedPublishAsFailed() {
        JournalEmailInfo info = info("id-1");
        when(repository.findById("id-1")).thenReturn(Optional.of(info));
        when(publisher.publish(info)).thenReturn(false);

        ResendResponse response = service.resend(new ResendRequest(null, null, List.of("id-1")));

        assertThat(response.succeeded()).isZero();
        assertThat(response.failed()).isEqualTo(1);
    }

    @Test
    void findsIdsByTimeRangeWhenNoIdsProvided() {
        JournalEmailInfo a = info("id-a");
        JournalEmailInfo b = info("id-b");
        when(repository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(timeGe, timeLt))
                .thenReturn(List.of(a, b));
        when(publisher.publish(any(JournalEmailInfo.class))).thenReturn(true);

        ResendResponse response = service.resend(new ResendRequest(timeGe, timeLt, null));

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.succeeded()).isEqualTo(2);
        assertThat(response.ids()).containsExactly("id-a", "id-b");
        verify(repository).findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(timeGe, timeLt);
        verify(publisher).publish(a);
        verify(publisher).publish(b);
    }

    @Test
    void usesTimeGeOnlyQuery() {
        when(repository.findByCreatedAtGreaterThanEqual(timeGe)).thenReturn(List.of(info("id-a")));
        when(publisher.publish(any(JournalEmailInfo.class))).thenReturn(true);

        service.resend(new ResendRequest(timeGe, null, null));

        verify(repository).findByCreatedAtGreaterThanEqual(timeGe);
    }

    @Test
    void usesTimeLtOnlyQuery() {
        when(repository.findByCreatedAtLessThan(timeLt)).thenReturn(List.of(info("id-b")));
        when(publisher.publish(any(JournalEmailInfo.class))).thenReturn(true);

        service.resend(new ResendRequest(null, timeLt, null));

        verify(repository).findByCreatedAtLessThan(timeLt);
    }

    @Test
    void idsTakePrecedenceOverTimeParams() {
        JournalEmailInfo info = info("id-1");
        when(repository.findById("id-1")).thenReturn(Optional.of(info));
        when(publisher.publish(info)).thenReturn(true);

        service.resend(new ResendRequest(timeGe, timeLt, List.of("id-1")));

        verify(repository).findById("id-1");
        verify(repository, never()).findByCreatedAtGreaterThanEqual(any());
        verify(repository, never()).findByCreatedAtLessThan(any());
        verify(repository, never())
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any());
    }

    @Test
    void rejectsWhenNothingProvided() {
        assertThatThrownBy(() -> service.resend(new ResendRequest(null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one of");
    }

    @Test
    void rejectsWhenTooManyIds() {
        AppProperties properties = new AppProperties();
        properties.getResend().setMaxIds(2);
        ResendService limited =
                new ResendService(repository, publisher, directExecutor, properties);

        assertThatThrownBy(
                        () -> limited.resend(new ResendRequest(null, null, List.of("a", "b", "c"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many ids");
    }

    private JournalEmailInfo info(String id) {
        JournalEmailInfo info = new JournalEmailInfo();
        info.setId(id);
        info.setObjectKey(id);
        info.setCreatedAt(Instant.parse("2026-08-16T08:00:00Z"));
        return info;
    }
}
