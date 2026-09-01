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

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    private static AppProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("app", Bindable.of(AppProperties.class))
                .orElseGet(AppProperties::new);
    }

    @Test
    void appliesDefaults() {
        AppProperties properties = bind(Map.of());

        assertThat(properties.getSmtp().getPort()).isEqualTo(2525);
        assertThat(properties.getSmtp().getMaxMessageSize()).isEqualTo(20 * 1024 * 1024);
        assertThat(properties.getNotify().getRocketMq().getNameServer()).isEqualTo("127.0.0.1:9876");
        assertThat(properties.getNotify().getRocketMq().getTopic()).isEqualTo("mail_meta_topic");
        assertThat(properties.getNotify().getRocketMq().getTag()).isEqualTo("mail-meta");
    }

    @Test
    void bindsKebabCaseNameServerProperty() {
        AppProperties properties = bind(Map.of(
                "app.notify.rocketmq.name-server", "rocketmq-namesrv:9876"));

        assertThat(properties.getNotify().getRocketMq().getNameServer())
                .isEqualTo("rocketmq-namesrv:9876");
    }

    @Test
    void bindsNestedPropertiesAcrossSections() {
        AppProperties properties = bind(Map.of(
                "app.smtp.port", "2526",
                "app.smtp.max-message-size", "1048576",
                "app.notify.rocketmq.topic", "custom_topic",
                "app.notify.rocketmq.send-timeout-ms", "5000"));

        assertThat(properties.getSmtp().getPort()).isEqualTo(2526);
        assertThat(properties.getSmtp().getMaxMessageSize()).isEqualTo(1048576);
        assertThat(properties.getNotify().getRocketMq().getTopic()).isEqualTo("custom_topic");
        assertThat(properties.getNotify().getRocketMq().getSendTimeoutMs()).isEqualTo(5000);
    }
}
