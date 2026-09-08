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

package uk.ekwong.journalarchiver.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;
import uk.ekwong.mailcommon.mail.MailMetaMessage;
import uk.ekwong.mailcommon.trace.TraceIds;

/**
 * Publishes the {@code mail_meta_topic} RocketMQ message after the email has been stored. Delivery
 * is best-effort: a failure is logged but does not roll back the already completed MongoDB / object
 * storage writes.
 */
@Service
public class RocketMailMetaPublisher implements MailMetaPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMailMetaPublisher.class);

    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;
    private final AppProperties.Notify.RocketMq config;
    private volatile boolean started;

    public RocketMailMetaPublisher(
            DefaultMQProducer producer, ObjectMapper objectMapper, AppProperties properties) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.config = properties.getNotify().getRocketMq();
    }

    @Override
    public boolean publish(JournalEmailInfo info) {
        if (!config.isEnabled()) {
            log.debug("RocketMQ notification disabled; skipping publish for id={}", info.getId());
            return false;
        }
        try {
            ensureStarted();
            MailMetaMessage meta =
                    new MailMetaMessage(
                            info.getId(),
                            info.getSender(),
                            info.getFrom(),
                            info.getTo(),
                            info.getCc(),
                            info.getSubject(),
                            info.getMessageId(),
                            info.getEnvelopeSender(),
                            info.getObjectKey(),
                            info.getReceivedAt(),
                            info.getCreatedAt(),
                            info.getUpdatedAt(),
                            info.getModificationCount());
            String payload = objectMapper.writeValueAsString(meta);
            String tag = config.getTag();
            // the RocketMQ message key is the MongoDB document id
            // (which is also the object storage key)
            Message message =
                    new Message(
                            config.getTopic(),
                            tag == null || tag.isBlank() ? "" : tag,
                            info.getId(),
                            payload.getBytes(StandardCharsets.UTF_8));
            message.putUserProperty(TraceIds.ROCKETMQ_PROPERTY, TraceIds.currentOrGenerate());
            SendResult result = producer.send(message, config.getSendTimeoutMs());
            log.info(
                    "Published mail meta notification to RocketMQ: topic={}, tag={}, key={}, msgId={}",
                    config.getTopic(),
                    tag,
                    info.getId(),
                    result.getMsgId());
            return true;
        } catch (Exception e) {
            log.error(
                    "Failed to publish mail meta notification to RocketMQ for id={}",
                    info.getId(),
                    e);
            return false;
        }
    }

    /**
     * Starts the producer on first use. {@code start()} is only valid from the CREATE_JUST state,
     * so we check the current state first (calling it on a running producer would throw). The
     * method is synchronized so concurrent first publishes cannot start the same producer twice.
     */
    private synchronized void ensureStarted() throws MQClientException {
        if (started) {
            return;
        }
        producer.start();
        started = true;
        log.info("RocketMQ producer started");
    }
}
