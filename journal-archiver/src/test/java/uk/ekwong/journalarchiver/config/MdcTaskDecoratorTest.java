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

package uk.ekwong.journalarchiver.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.ekwong.mailcommon.trace.TraceIds;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesCallerTraceIdIntoAsynchronousTask() throws Exception {
        MDC.put(TraceIds.MDC_KEY, "trace-abc");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<String> workerTraceId = new AtomicReference<>();
        try {
            Runnable task =
                    new MdcTaskDecorator()
                            .decorate(() -> workerTraceId.set(MDC.get(TraceIds.MDC_KEY)));
            executor.submit(task).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(workerTraceId.get()).isEqualTo("trace-abc");
    }
}
