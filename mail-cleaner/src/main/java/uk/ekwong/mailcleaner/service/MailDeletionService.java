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

import uk.ekwong.mailcommon.es.MailInfoDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.MultiGetItem;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes archived email metadata from the Elasticsearch {@code mail_info}
 * index by archive id (MongoDB id / object storage key).
 */
@Service
public class MailDeletionService {

    private static final Logger log = LoggerFactory.getLogger(MailDeletionService.class);

    public static final int MAX_IDS_PER_REQUEST = 1000;

    private final ElasticsearchOperations elasticsearchOperations;

    public MailDeletionService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * Deletes the documents with the given ids. Blank values are ignored and
     * duplicates are collapsed. An empty request (after normalization) is
     * rejected; more than {@link #MAX_IDS_PER_REQUEST} ids is also rejected.
     *
     * @return how many documents were requested, deleted and which ids were not found
     */
    public MailDeleteResponse deleteByIds(List<String> ids) {
        List<String> normalized = normalize(ids);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        if (normalized.size() > MAX_IDS_PER_REQUEST) {
            throw new IllegalArgumentException("too many ids, max " + MAX_IDS_PER_REQUEST + " per request");
        }

        // One multi-get round trip tells us which ids actually exist, so the
        // caller can distinguish "deleted" from "was already gone".
        Set<String> existing = findExistingIds(normalized);
        List<String> notFound = normalized.stream()
                .filter(id -> !existing.contains(id))
                .toList();

        long deleted = 0;
        if (!existing.isEmpty()) {
            Query deleteQuery = NativeQuery.builder()
                    .withQuery(q -> q.ids(i -> i.values(new ArrayList<>(existing))))
                    .build();
            ByQueryResponse response = elasticsearchOperations.delete(deleteQuery, MailInfoDocument.class);
            deleted = response.getDeleted();
            log.info("Deleted {} of {} requested mail_info documents ({} not found)",
                    deleted, normalized.size(), notFound.size());
        } else {
            log.info("No mail_info documents to delete: all {} ids were not found", normalized.size());
        }
        return new MailDeleteResponse(normalized.size(), deleted, notFound);
    }

    private Set<String> findExistingIds(List<String> ids) {
        NativeQuery lookupQuery = NativeQuery.builder().withIds(ids).build();
        List<MultiGetItem<MailInfoDocument>> items =
                elasticsearchOperations.multiGet(lookupQuery, MailInfoDocument.class);
        Set<String> existing = new LinkedHashSet<>();
        for (MultiGetItem<MailInfoDocument> item : items) {
            if (item != null && item.hasItem() && item.getItem().getId() != null) {
                existing.add(item.getItem().getId());
            }
        }
        return existing;
    }

    private List<String> normalize(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }
}
