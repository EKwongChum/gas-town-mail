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

package uk.ekwong.mailcommon.trace;

import java.util.UUID;
import org.slf4j.MDC;

/** Shared keys and helpers for end-to-end trace correlation across the three applications. */
public final class TraceIds {

    /** MDC key used by every log line belonging to one logical mail-processing trace. */
    public static final String MDC_KEY = "traceId";

    /** RocketMQ user property used to carry the trace id from journal-archiver to mail-cleaner. */
    public static final String ROCKETMQ_PROPERTY = "traceId";

    private TraceIds() {}

    /** Returns a random trace id. */
    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /** Returns the current MDC trace id, or generates a new one when none is present. */
    public static String currentOrGenerate() {
        String current = MDC.get(MDC_KEY);
        return current == null || current.isBlank() ? generate() : current;
    }
}
