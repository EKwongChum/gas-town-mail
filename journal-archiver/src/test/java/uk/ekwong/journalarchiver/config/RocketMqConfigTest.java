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

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.Test;

class RocketMqConfigTest {

    @Test
    void createsProducerFromAppProperties() {
        AppProperties properties = new AppProperties();
        properties.getNotify().getRocketMq().setNameServer("ns:9876");
        properties.getNotify().getRocketMq().setProducerGroup("test-producer");
        properties.getNotify().getRocketMq().setSendTimeoutMs(5000);
        properties.getNotify().getRocketMq().setRetryTimesWhenSendFailed(3);

        DefaultMQProducer producer = new RocketMqConfig().rocketMqProducer(properties);
        try {
            assertThat(producer.getProducerGroup()).isEqualTo("test-producer");
            assertThat(producer.getNamesrvAddr()).isEqualTo("ns:9876");
            assertThat(producer.getSendMsgTimeout()).isEqualTo(5000);
            assertThat(producer.getRetryTimesWhenSendFailed()).isEqualTo(3);
        } finally {
            producer.shutdown();
        }
    }
}
