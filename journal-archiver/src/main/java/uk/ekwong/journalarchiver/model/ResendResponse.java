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

package uk.ekwong.journalarchiver.model;

import java.util.List;

/**
 * Result of a resend operation.
 *
 * @param total number of ids that were selected for resending
 * @param succeeded number of notifications published successfully
 * @param failed number of ids that could not be resent
 * @param ids the ids that were processed
 * @param notFoundIds ids that do not exist in MongoDB (counted as failed)
 */
public record ResendResponse(
        int total, int succeeded, int failed, List<String> ids, List<String> notFoundIds) {}
