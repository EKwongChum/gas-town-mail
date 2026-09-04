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

import java.time.Instant;
import java.util.List;

/**
 * Request body for the notification resend endpoint.
 *
 * <p>When {@code ids} is provided it takes precedence and the matching documents are resent
 * directly. Otherwise the documents whose creation time falls into {@code [timeGe, timeLt)} are
 * looked up and resent.
 *
 * @param timeGe data creation time greater than or equal to this value (optional)
 * @param timeLt data creation time less than this value (optional)
 * @param ids MongoDB document ids to resend (optional)
 */
public record ResendRequest(Instant timeGe, Instant timeLt, List<String> ids) {}
