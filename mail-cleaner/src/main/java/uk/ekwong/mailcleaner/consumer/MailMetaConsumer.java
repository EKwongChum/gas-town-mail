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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.ekwong.mailcleaner.config.CleanerProperties;

/**
 * Starts and stops the RocketMQ push consumer that subscribes to {@code mail_meta_topic}. If the
 * name server / broker is unreachable at startup, the consumer is recreated and restarted
 * periodically instead of requiring an application restart.
 */
@Component
public class MailMetaConsumer {

    private static final Logger log = LoggerFactory.getLogger(MailMetaConsumer.class);

    private final CleanerProperties properties;
    private final MailMetaMessageListener listener;
    private final ScheduledExecutorService startRetryExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread thread = new Thread(r, "rocketmq-consumer-start-retry");
                        thread.setDaemon(true);
                        return thread;
                    });
    private volatile DefaultMQPushConsumer consumer;
    private volatile boolean stopping;

    public MailMetaConsumer(CleanerProperties properties, MailMetaMessageListener listener) {
        this.properties = properties;
        this.listener = listener;
    }

    @PostConstruct
    public void start() {
        startConsumer();
    }

    public boolean isRunning() {
        return consumer != null;
    }

    private void startConsumer() {
        if (stopping) {
            return;
        }
        CleanerProperties.RocketMq mq = properties.getRocketmq();
        try {
            DefaultMQPushConsumer c = new DefaultMQPushConsumer(mq.getConsumerGroup());
            c.setNamesrvAddr(mq.getNameServer());
            c.subscribe(mq.getTopic(), mq.getTag());
            c.registerMessageListener(listener);
            c.setConsumeThreadMin(2);
            c.setConsumeThreadMax(4);
            c.start();
            this.consumer = c;
            log.info(
                    "RocketMQ consumer '{}' started on topic '{}' (name server {})",
                    mq.getConsumerGroup(),
                    mq.getTopic(),
                    mq.getNameServer());
        } catch (Exception e) {
            this.consumer = null;
            log.warn(
                    "RocketMQ consumer failed to start (name server={}): {}; retrying in {} ms",
                    mq.getNameServer(),
                    e.getMessage(),
                    mq.getStartRetryIntervalMs());
            scheduleRetry(mq);
        }
    }

    private void scheduleRetry(CleanerProperties.RocketMq mq) {
        if (stopping) {
            return;
        }
        startRetryExecutor.schedule(
                this::startConsumer, mq.getStartRetryIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        stopping = true;
        startRetryExecutor.shutdownNow();
        if (consumer != null) {
            consumer.shutdown();
            consumer = null;
            log.info("RocketMQ consumer stopped");
        }
    }
}
