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

package uk.ekwong.mailmcpserver.send;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Verifies that the {@code app.send.*} keys of application.yml bind to the properties bean. */
class MailSendPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsTheApplicationYamlDefaults() {
        contextRunner.run(
                context -> {
                    MailSendProperties properties = context.getBean(MailSendProperties.class);
                    assertThat(properties.getMaxAttachmentSize().toBytes())
                            .isEqualTo(10L * 1024 * 1024);
                    assertThat(properties.getMaxTotalAttachmentSize().toBytes())
                            .isEqualTo(20L * 1024 * 1024);
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.getWriteTimeout()).isEqualTo(Duration.ofSeconds(60));
                });
    }

    @Test
    void bindsOverriddenValues() {
        contextRunner
                .withPropertyValues(
                        "app.send.max-attachment-size=1MB",
                        "app.send.max-total-attachment-size=2MB",
                        "app.send.connect-timeout=2s")
                .run(
                        context -> {
                            MailSendProperties properties =
                                    context.getBean(MailSendProperties.class);
                            assertThat(properties.getMaxAttachmentSize().toBytes())
                                    .isEqualTo(1024L * 1024);
                            assertThat(properties.getMaxTotalAttachmentSize().toBytes())
                                    .isEqualTo(2L * 1024 * 1024);
                            assertThat(properties.getConnectTimeout())
                                    .isEqualTo(Duration.ofSeconds(2));
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MailSendProperties.class)
    static class TestConfiguration {}
}
