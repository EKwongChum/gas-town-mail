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
}
