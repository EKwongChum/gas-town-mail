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

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Settings for the outbound mail endpoints ({@code POST /api/mails/send|reply|forward}).
 *
 * <p>The attachment limits are enforced per attachment and for the sum of all attachments of one
 * outgoing mail, including the attachments carried over from an archived email when forwarding. The
 * timeouts are handed to the SMTP client so a slow or unreachable server cannot block a request
 * forever.
 */
@ConfigurationProperties(prefix = "app.send")
public class MailSendProperties {

    /** Maximum size of a single attachment, 10 MB by default. */
    private DataSize maxAttachmentSize = DataSize.ofMegabytes(10);

    /** Maximum sum of all attachment sizes of one mail, 20 MB by default. */
    private DataSize maxTotalAttachmentSize = DataSize.ofMegabytes(20);

    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(30);
    private Duration writeTimeout = Duration.ofSeconds(60);

    /**
     * SMTP servers the endpoints are allowed to deliver to. Empty (the default) allows any server;
     * entries may use the {@code *.example.com} wildcard form.
     */
    private List<String> allowedSmtpHosts = List.of();

    /**
     * Whether the SMTP server certificate must match the host name it was reached under. Disable
     * only for servers with a self-signed certificate.
     */
    private boolean verifyServerIdentity = true;

    /**
     * Whether an SMTP password may be sent over an unencrypted connection. Disabled by default:
     * credentials force STARTTLS (or implicit TLS) unless this is switched on.
     */
    private boolean allowPlaintextCredentials;

    private RateLimit rateLimit = new RateLimit();
    private Duration idempotencyTtl = Duration.ofMinutes(15);
    private Async async = new Async();

    public DataSize getMaxAttachmentSize() {
        return maxAttachmentSize;
    }

    public void setMaxAttachmentSize(DataSize maxAttachmentSize) {
        this.maxAttachmentSize = maxAttachmentSize;
    }

    public DataSize getMaxTotalAttachmentSize() {
        return maxTotalAttachmentSize;
    }

    public void setMaxTotalAttachmentSize(DataSize maxTotalAttachmentSize) {
        this.maxTotalAttachmentSize = maxTotalAttachmentSize;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public List<String> getAllowedSmtpHosts() {
        return allowedSmtpHosts;
    }

    public void setAllowedSmtpHosts(List<String> allowedSmtpHosts) {
        this.allowedSmtpHosts = allowedSmtpHosts;
    }

    public boolean isVerifyServerIdentity() {
        return verifyServerIdentity;
    }

    public void setVerifyServerIdentity(boolean verifyServerIdentity) {
        this.verifyServerIdentity = verifyServerIdentity;
    }

    public boolean isAllowPlaintextCredentials() {
        return allowPlaintextCredentials;
    }

    public void setAllowPlaintextCredentials(boolean allowPlaintextCredentials) {
        this.allowPlaintextCredentials = allowPlaintextCredentials;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    /**
     * How long an {@code Idempotency-Key} is remembered: a repeated request with the same key
     * returns the first result instead of delivering the mail twice. Zero disables the check.
     */
    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async;
    }

    /** Per-client limit of the outbound mail endpoints; disabled when requests per minute is 0. */
    public static class RateLimit {

        private int requestsPerMinute;
        private boolean trustForwardedFor;

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }

        public boolean enabled() {
            return requestsPerMinute > 0;
        }

        /**
         * Whether the first {@code X-Forwarded-For} entry is used instead of the socket address.
         */
        public boolean isTrustForwardedFor() {
            return trustForwardedFor;
        }

        public void setTrustForwardedFor(boolean trustForwardedFor) {
            this.trustForwardedFor = trustForwardedFor;
        }
    }

    /** Background delivery of a request that asked for it with {@code Prefer: respond-async}. */
    public static class Async {

        private boolean enabled = true;
        private int threads = 2;
        private int queueCapacity = 50;
        private Duration taskTtl = Duration.ofMinutes(15);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public Duration getTaskTtl() {
            return taskTtl;
        }

        public void setTaskTtl(Duration taskTtl) {
            this.taskTtl = taskTtl;
        }
    }
}
