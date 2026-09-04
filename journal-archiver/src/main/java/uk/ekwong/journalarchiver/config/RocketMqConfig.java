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

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the RocketMQ producer. The producer is configured here but started lazily before the
 * first send (see {@code RocketMailMetaPublisher}), so a temporarily unreachable name server never
 * leaves the producer in a START_FAILED state that would require an application restart.
 */
@Configuration
public class RocketMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RocketMqConfig.class);

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer rocketMqProducer(AppProperties properties) {
        AppProperties.Notify.RocketMq mq = properties.getNotify().getRocketMq();
        DefaultMQProducer producer = new DefaultMQProducer(mq.getProducerGroup());
        producer.setNamesrvAddr(mq.getNameServer());
        producer.setSendMsgTimeout((int) mq.getSendTimeoutMs());
        producer.setRetryTimesWhenSendFailed(mq.getRetryTimesWhenSendFailed());
        log.info(
                "RocketMQ producer '{}' initialized on name server {}",
                mq.getProducerGroup(),
                mq.getNameServer());
        return producer;
    }
}
