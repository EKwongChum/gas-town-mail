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

package uk.ekwong.mailcleaner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.MultiGetItem;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.Query;
import uk.ekwong.mailcommon.es.MailInfoDocument;

class MailDeletionServiceTest {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final MailDeletionService service = new MailDeletionService(operations);

    @Test
    void deletesExistingIdsAndReportsNotFoundIds() {
        MailInfoDocument doc1 =
                MailInfoDocument.from(
                        "id-1",
                        "a@example.com",
                        "a@example.com",
                        "b@example.com",
                        null,
                        "<m-1@example.com>",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        "Subject",
                        "text/plain",
                        List.of());
        MailInfoDocument doc2 =
                MailInfoDocument.from(
                        "id-2",
                        "a@example.com",
                        "a@example.com",
                        "b@example.com",
                        null,
                        "<m-2@example.com>",
                        Instant.parse("2026-08-02T00:00:00Z"),
                        "Subject",
                        "text/plain",
                        List.of());
        when(operations.multiGet(any(Query.class), eq(MailInfoDocument.class)))
                .thenReturn(
                        List.of(
                                MultiGetItem.of(doc1, null),
                                MultiGetItem.of(doc2, null),
                                MultiGetItem.of(null, null)));
        when(operations.delete(any(Query.class), eq(MailInfoDocument.class)))
                .thenReturn(ByQueryResponse.builder().withDeleted(2).build());

        MailDeleteResponse response = service.deleteByIds(List.of("id-1", "id-2", "id-missing"));

        assertThat(response.requested()).isEqualTo(3);
        assertThat(response.deleted()).isEqualTo(2);
        assertThat(response.notFoundIds()).containsExactly("id-missing");

        ArgumentCaptor<Query> deleteCaptor = ArgumentCaptor.forClass(Query.class);
        verify(operations).delete(deleteCaptor.capture(), eq(MailInfoDocument.class));
        NativeQuery deleteQuery = (NativeQuery) deleteCaptor.getValue();
        assertThat(deleteQuery.getQuery().isIds()).isTrue();
        assertThat(deleteQuery.getQuery().ids().values()).containsExactlyInAnyOrder("id-1", "id-2");
    }

    @Test
    void returnsZeroDeletedWhenNoIdsExist() {
        when(operations.multiGet(any(Query.class), eq(MailInfoDocument.class)))
                .thenReturn(List.of(MultiGetItem.of(null, null), MultiGetItem.of(null, null)));

        MailDeleteResponse response = service.deleteByIds(List.of("missing-1", "missing-2"));

        assertThat(response.requested()).isEqualTo(2);
        assertThat(response.deleted()).isZero();
        assertThat(response.notFoundIds()).containsExactly("missing-1", "missing-2");
        verify(operations, never()).delete(any(Query.class), eq(MailInfoDocument.class));
    }

    @Test
    void trimsBlankIdsAndRemovesDuplicates() {
        MailInfoDocument doc =
                MailInfoDocument.from(
                        "id-1",
                        "a@example.com",
                        "a@example.com",
                        "b@example.com",
                        null,
                        "<m-1@example.com>",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        "Subject",
                        "text/plain",
                        List.of());
        when(operations.multiGet(any(Query.class), eq(MailInfoDocument.class)))
                .thenReturn(List.of(MultiGetItem.of(doc, null)));
        when(operations.delete(any(Query.class), eq(MailInfoDocument.class)))
                .thenReturn(ByQueryResponse.builder().withDeleted(1).build());

        MailDeleteResponse response = service.deleteByIds(List.of("  id-1  ", "id-1", "", "   "));

        assertThat(response.requested()).isEqualTo(1);
        assertThat(response.deleted()).isEqualTo(1);
        assertThat(response.notFoundIds()).isEmpty();
    }

    @Test
    void rejectsEmptyRequest() {
        assertThatThrownBy(() -> service.deleteByIds(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids must not be empty");
        assertThatThrownBy(() -> service.deleteByIds(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(operations, never()).multiGet(any(Query.class), any());
    }

    @Test
    void rejectsTooManyIds() {
        List<String> ids =
                java.util.stream.IntStream.rangeClosed(
                                1, MailDeletionService.MAX_IDS_PER_REQUEST + 1)
                        .mapToObj(i -> "id-" + i)
                        .toList();

        assertThatThrownBy(() -> service.deleteByIds(ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many ids");
    }
}
