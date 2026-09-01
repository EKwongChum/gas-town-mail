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

package uk.ekwong.mailcleaner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class CleanerProperties {

    private RocketMq rocketmq = new RocketMq();

    public RocketMq getRocketmq() {
        return rocketmq;
    }

    public void setRocketmq(RocketMq rocketmq) {
        this.rocketmq = rocketmq;
    }

    public static class RocketMq {
        private String nameServer = "127.0.0.1:9876";
        private String consumerGroup = "mail-cleaner-consumer";
        private String topic = "mail_meta_topic";
        private String tag = "*";
        private long startRetryIntervalMs = 30_000;

        public String getNameServer() {
            return nameServer;
        }

        public void setNameServer(String nameServer) {
            this.nameServer = nameServer;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public long getStartRetryIntervalMs() {
            return startRetryIntervalMs;
        }

        public void setStartRetryIntervalMs(long startRetryIntervalMs) {
            this.startRetryIntervalMs = startRetryIntervalMs;
        }
    }
}
