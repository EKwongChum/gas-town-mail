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
import uk.ekwong.mailmcpserver.security.SecurityProperties;

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

    @Test
    void bindsTheOutboundMailSafetyDefaults() {
        contextRunner.run(
                context -> {
                    MailSendProperties properties = context.getBean(MailSendProperties.class);
                    assertThat(properties.getAllowedSmtpHosts()).isEmpty();
                    assertThat(properties.isVerifyServerIdentity()).isTrue();
                    assertThat(properties.isAllowPlaintextCredentials()).isFalse();
                    assertThat(properties.getRateLimit().enabled()).isFalse();
                    assertThat(properties.getRateLimit().isTrustForwardedFor()).isFalse();
                });
    }

    @Test
    void bindsTheSecurityDefaults() {
        contextRunner.run(
                context -> {
                    SecurityProperties properties = context.getBean(SecurityProperties.class);
                    assertThat(properties.getApiKey()).isEmpty();
                    assertThat(properties.getHeaderName()).isEqualTo("X-API-Key");
                    assertThat(properties.getProtectedPaths())
                            .containsExactly("/api/mails/**", "/mcp");
                });
    }

    @Test
    void bindsOverriddenSafetyValues() {
        contextRunner
                .withPropertyValues(
                        "app.send.allowed-smtp-hosts=smtp.example.com, *.corp.example",
                        "app.send.verify-server-identity=false",
                        "app.send.allow-plaintext-credentials=true",
                        "app.send.rate-limit.requests-per-minute=5",
                        "app.send.rate-limit.trust-forwarded-for=true",
                        "app.security.api-key=secret",
                        "app.security.protected-paths=/mcp")
                .run(
                        context -> {
                            MailSendProperties send = context.getBean(MailSendProperties.class);
                            assertThat(send.getAllowedSmtpHosts())
                                    .containsExactly("smtp.example.com", "*.corp.example");
                            assertThat(send.isVerifyServerIdentity()).isFalse();
                            assertThat(send.isAllowPlaintextCredentials()).isTrue();
                            assertThat(send.getRateLimit().getRequestsPerMinute()).isEqualTo(5);
                            assertThat(send.getRateLimit().isTrustForwardedFor()).isTrue();

                            SecurityProperties security = context.getBean(SecurityProperties.class);
                            assertThat(security.getApiKey()).isEqualTo("secret");
                            assertThat(security.getProtectedPaths()).containsExactly("/mcp");
                        });
    }

    @Test
    void bindsTheDeliverySafetyDefaults() {
        contextRunner.run(
                context -> {
                    MailSendProperties properties = context.getBean(MailSendProperties.class);
                    assertThat(properties.getIdempotencyTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.getAsync().isEnabled()).isTrue();
                    assertThat(properties.getAsync().getThreads()).isEqualTo(2);
                    assertThat(properties.getAsync().getQueueCapacity()).isEqualTo(50);
                    assertThat(properties.getAsync().getTaskTtl())
                            .isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void bindsOverriddenDeliveryValues() {
        contextRunner
                .withPropertyValues(
                        "app.send.idempotency-ttl=1m",
                        "app.send.async.enabled=false",
                        "app.send.async.threads=4",
                        "app.send.async.queue-capacity=10",
                        "app.send.async.task-ttl=2m")
                .run(
                        context -> {
                            MailSendProperties properties =
                                    context.getBean(MailSendProperties.class);
                            assertThat(properties.getIdempotencyTtl())
                                    .isEqualTo(Duration.ofMinutes(1));
                            assertThat(properties.getAsync().isEnabled()).isFalse();
                            assertThat(properties.getAsync().getThreads()).isEqualTo(4);
                            assertThat(properties.getAsync().getQueueCapacity()).isEqualTo(10);
                            assertThat(properties.getAsync().getTaskTtl())
                                    .isEqualTo(Duration.ofMinutes(2));
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({MailSendProperties.class, SecurityProperties.class})
    static class TestConfiguration {}
}
