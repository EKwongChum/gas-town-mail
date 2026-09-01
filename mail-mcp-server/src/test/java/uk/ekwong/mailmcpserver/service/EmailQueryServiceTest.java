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

package uk.ekwong.mailmcpserver.service;

import uk.ekwong.mailcommon.es.MailInfoDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailQueryServiceTest {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final EmailQueryService service = new EmailQueryService(operations);

    private static MailInfoDocument sampleDocument() {
        return MailInfoDocument.from(
                "id-1",
                "sender@example.com",
                "from@example.com",
                "to@example.com",
                null,
                "<message-1@example.com>",
                Instant.parse("2026-08-01T00:00:00Z"),
                "Weekly report",
                "multipart/mixed",
                List.of("report.pdf"));
    }

    @Test
    void searchReturnsTotalAndDocuments() {
        MailInfoDocument document = sampleDocument();
        @SuppressWarnings("unchecked")
        SearchHit<MailInfoDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(document);
        @SuppressWarnings("unchecked")
        SearchHits<MailInfoDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(hit));

        when(operations.count(any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(MailInfoDocument.class))).thenReturn(42L);
        when(operations.search(any(NativeQuery.class), eq(MailInfoDocument.class))).thenReturn(hits);

        MailSearchRequest request = new MailSearchRequest(
                "report", "sender@example.com", null, null, null, null,
                Instant.parse("2026-08-01T00:00:00Z"), null, 0, 20);

        SearchResult result = service.search(request);

        assertThat(result.total()).isEqualTo(42L);
        assertThat(result.documents()).containsExactly(document);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(queryCaptor.capture(), eq(MailInfoDocument.class));
        assertThat(queryCaptor.getValue().getPageable().getPageNumber()).isZero();
        assertThat(queryCaptor.getValue().getPageable().getPageSize()).isEqualTo(20);
        assertThat(queryCaptor.getValue().getQuery()).isNotNull();
    }

    @Test
    void searchCapsPageSizeAtMaximum() {
        @SuppressWarnings("unchecked")
        SearchHits<MailInfoDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.count(any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(MailInfoDocument.class))).thenReturn(0L);
        when(operations.search(any(NativeQuery.class), eq(MailInfoDocument.class))).thenReturn(hits);

        service.search(new MailSearchRequest(null, null, null, null, null, null,
                null, null, 1, 10_000));

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(queryCaptor.capture(), eq(MailInfoDocument.class));
        assertThat(queryCaptor.getValue().getPageable().getPageSize()).isEqualTo(EmailQueryService.MAX_PAGE_SIZE);
    }

    @Test
    void countReturnsDocumentCount() {
        when(operations.count(any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(MailInfoDocument.class))).thenReturn(7L);

        long count = service.count(new MailSearchRequest("report", null, null, null, null,
                null, null, null, 0, 20));

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void findByIdReturnsDocumentWhenPresent() {
        MailInfoDocument document = sampleDocument();
        when(operations.get("id-1", MailInfoDocument.class)).thenReturn(document);

        Optional<MailInfoDocument> result = service.findById("id-1");

        assertThat(result).contains(document);
    }

    @Test
    void findByIdReturnsEmptyForBlankId() {
        assertThat(service.findById(" ")).isEmpty();
    }
}
