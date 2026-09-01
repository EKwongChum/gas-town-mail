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

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import uk.ekwong.mailcommon.es.MailInfoDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the archived email metadata from the Elasticsearch {@code mail_info}
 * index. Used by the MCP tools.
 */
@Service
public class EmailQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ElasticsearchOperations operations;

    public EmailQueryService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    /**
     * Searches the {@code mail_info} index with the given filters, returning
     * the requested page plus the total number of matching documents.
     */
    public SearchResult search(MailSearchRequest request) {
        int size = Math.min(Math.max(request.size(), 1), MAX_PAGE_SIZE);
        int page = Math.max(request.page(), 0);

        NativeQuery query = NativeQuery.builder()
                .withQuery(buildQuery(request))
                .withPageable(PageRequest.of(page, size))
                .build();

        long total = operations.count(query, MailInfoDocument.class);
        SearchHits<MailInfoDocument> hits = operations.search(query, MailInfoDocument.class);
        List<MailInfoDocument> documents = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        return new SearchResult(total, documents);
    }

    /**
     * Counts the documents matching the given filters (pagination ignored).
     */
    public long count(MailSearchRequest request) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(buildQuery(request))
                .build();
        return operations.count(query, MailInfoDocument.class);
    }

    /**
     * Returns the document with the given archive id (MongoDB id / object
     * storage key), or empty when it does not exist.
     */
    public Optional<MailInfoDocument> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(operations.get(id, MailInfoDocument.class));
    }

    /**
     * Builds a bool query: exact term filters (AND) plus, when a keyword is
     * given, a minimum-should-match clause searching subject, sender, from,
     * to and message id.
     */
    private Query buildQuery(MailSearchRequest request) {
        List<Query> must = new ArrayList<>();
        List<Query> should = new ArrayList<>();

        if (hasText(request.sender())) {
            must.add(Query.of(q -> q.term(t -> t.field("sender").value(request.sender()))));
        }
        if (hasText(request.from())) {
            must.add(Query.of(q -> q.term(t -> t.field("from").value(request.from()))));
        }
        if (hasText(request.to())) {
            must.add(Query.of(q -> q.term(t -> t.field("to").value(request.to()))));
        }
        if (hasText(request.cc())) {
            must.add(Query.of(q -> q.term(t -> t.field("cc").value(request.cc()))));
        }
        if (hasText(request.messageId())) {
            must.add(Query.of(q -> q.term(t -> t.field("messageId").value(request.messageId()))));
        }
        if (request.receivedTimeGe() != null || request.receivedTimeLt() != null) {
            must.add(Query.of(q -> q.range(r -> r.date(d -> {
                d.field("receivedTime");
                if (request.receivedTimeGe() != null) {
                    d.gte(request.receivedTimeGe().toString());
                }
                if (request.receivedTimeLt() != null) {
                    d.lt(request.receivedTimeLt().toString());
                }
                return d;
            }))));
        }
        if (hasText(request.keyword())) {
            should.add(Query.of(q -> q.match(m -> m.field("subject").query(request.keyword()))));
            should.add(Query.of(q -> q.match(m -> m.field("sender").query(request.keyword()))));
            should.add(Query.of(q -> q.match(m -> m.field("from").query(request.keyword()))));
            should.add(Query.of(q -> q.match(m -> m.field("to").query(request.keyword()))));
            should.add(Query.of(q -> q.match(m -> m.field("messageId").query(request.keyword()))));
        }

        if (must.isEmpty() && should.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }
        return Query.of(q -> q.bool(b -> {
            if (!must.isEmpty()) {
                b.must(must);
            }
            if (!should.isEmpty()) {
                b.should(should);
                b.minimumShouldMatch("1");
            }
            return b;
        }));
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
