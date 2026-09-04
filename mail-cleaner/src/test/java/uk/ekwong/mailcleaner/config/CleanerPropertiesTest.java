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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CleanerPropertiesTest {

    private static CleanerProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("app", Bindable.of(CleanerProperties.class))
                .orElseGet(CleanerProperties::new);
    }

    @Test
    void appliesDefaults() {
        CleanerProperties properties = bind(Map.of());

        assertThat(properties.getRocketmq().getNameServer()).isEqualTo("127.0.0.1:9876");
        assertThat(properties.getRocketmq().getConsumerGroup()).isEqualTo("mail-cleaner-consumer");
        assertThat(properties.getRocketmq().getTopic()).isEqualTo("mail_meta_topic");
        assertThat(properties.getRocketmq().getTag()).isEqualTo("*");
        assertThat(properties.getRocketmq().getStartRetryIntervalMs()).isEqualTo(30_000);
    }

    @Test
    void bindsKebabCaseNameServerProperty() {
        CleanerProperties properties =
                bind(
                        Map.of(
                                "app.rocketmq.name-server", "rocketmq-namesrv:9876",
                                "app.rocketmq.start-retry-interval-ms", "5000"));

        assertThat(properties.getRocketmq().getNameServer()).isEqualTo("rocketmq-namesrv:9876");
        assertThat(properties.getRocketmq().getStartRetryIntervalMs()).isEqualTo(5000);
    }
}
