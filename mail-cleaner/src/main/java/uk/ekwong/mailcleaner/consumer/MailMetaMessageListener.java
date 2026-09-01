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

import uk.ekwong.mailcleaner.service.MailCleaningService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RocketMQ message listener for the {@code mail_meta_topic} topic. Each
 * message triggers one mail cleaning job; failures return
 * {@code RECONSUME_LATER} so RocketMQ redelivers the message.
 */
@Component
public class MailMetaMessageListener implements MessageListenerConcurrently {

    private static final Logger log = LoggerFactory.getLogger(MailMetaMessageListener.class);

    private final MailCleaningService mailCleaningService;

    public MailMetaMessageListener(MailCleaningService mailCleaningService) {
        this.mailCleaningService = mailCleaningService;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        boolean allSucceeded = true;
        for (MessageExt msg : msgs) {
            try {
                String payload = new String(msg.getBody(), StandardCharsets.UTF_8);
                mailCleaningService.clean(payload, msg.getKeys());
            } catch (Exception e) {
                log.error("Mail cleaning failed, message key={}, will redeliver", msg.getKeys(), e);
                allSucceeded = false;
            }
        }
        // still process the rest of the batch; only redeliver when something failed
        return allSucceeded
                ? ConsumeConcurrentlyStatus.CONSUME_SUCCESS
                : ConsumeConcurrentlyStatus.RECONSUME_LATER;
    }
}
