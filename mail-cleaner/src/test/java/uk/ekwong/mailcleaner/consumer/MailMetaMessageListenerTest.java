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

package uk.ekwong.mailcleaner.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.ekwong.mailcleaner.service.MailCleaningService;
import uk.ekwong.mailcommon.trace.TraceIds;

class MailMetaMessageListenerTest {

    private final MailCleaningService cleaningService = mock(MailCleaningService.class);
    private final MailMetaMessageListener listener = new MailMetaMessageListener(cleaningService);

    @BeforeEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void consumesMessageAfterSuccessfulCleaning() {
        MessageExt message = new MessageExt();
        message.setTopic("mail_meta_topic");
        message.setKeys("id-1");
        message.setBody("{}".getBytes(StandardCharsets.UTF_8));

        ConsumeConcurrentlyStatus status = listener.consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(cleaningService).clean("{}", "id-1");
    }

    @Test
    void requestsRedeliveryWhenCleaningFails() {
        MessageExt message = new MessageExt();
        message.setTopic("mail_meta_topic");
        message.setKeys("id-2");
        message.setBody("{}".getBytes(StandardCharsets.UTF_8));
        doThrow(new MailCleaningService.MailCleanException("boom", null))
                .when(cleaningService)
                .clean("{}", "id-2");

        ConsumeConcurrentlyStatus status = listener.consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
    }

    @Test
    void processesWholeBatchAndRedeliversWhenAnyMessageFails() {
        MessageExt ok = message("id-ok");
        MessageExt bad = message("id-bad");
        doThrow(new MailCleaningService.MailCleanException("boom", null))
                .when(cleaningService)
                .clean("{}", "id-bad");

        ConsumeConcurrentlyStatus status = listener.consumeMessage(List.of(ok, bad), null);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        verify(cleaningService).clean("{}", "id-ok");
        verify(cleaningService).clean("{}", "id-bad");
    }

    @Test
    void usesMessageTraceIdDuringCleaningAndCleansUpMdc() {
        MessageExt message = message("id-1");
        message.putUserProperty(TraceIds.ROCKETMQ_PROPERTY, "trace-abc");
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            seenTraceId.set(MDC.get(TraceIds.MDC_KEY));
                            return null;
                        })
                .when(cleaningService)
                .clean("{}", "id-1");

        listener.consumeMessage(List.of(message), null);

        assertThat(seenTraceId.get()).isEqualTo("trace-abc");
        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    private MessageExt message(String key) {
        MessageExt message = new MessageExt();
        message.setTopic("mail_meta_topic");
        message.setKeys(key);
        message.setBody("{}".getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
