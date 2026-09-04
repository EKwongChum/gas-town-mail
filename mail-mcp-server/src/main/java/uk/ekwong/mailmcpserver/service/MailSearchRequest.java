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

import java.time.Instant;

/**
 * Filters for querying the Elasticsearch {@code mail_info} index. All filters are optional and
 * combined with AND.
 *
 * @param keyword free-text keyword matched against subject, sender, from, to and message id
 * @param sender exact sender address
 * @param from exact from address
 * @param to exact to address
 * @param cc exact cc address
 * @param messageId exact Message-Id
 * @param receivedTimeGe received time greater than or equal to (inclusive), ISO-8601
 * @param receivedTimeLt received time less than (exclusive), ISO-8601
 * @param page zero-based page number
 * @param size page size, max 100
 */
public record MailSearchRequest(
        String keyword,
        String sender,
        String from,
        String to,
        String cc,
        String messageId,
        Instant receivedTimeGe,
        Instant receivedTimeLt,
        int page,
        int size) {}
