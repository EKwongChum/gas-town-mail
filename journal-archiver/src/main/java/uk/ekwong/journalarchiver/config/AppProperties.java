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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Smtp smtp = new Smtp();
    private Journal journal = new Journal();
    private Processing processing = new Processing();
    private Resend resend = new Resend();
    private Notify notify = new Notify();

    public Smtp getSmtp() {
        return smtp;
    }

    public void setSmtp(Smtp smtp) {
        this.smtp = smtp;
    }

    public Journal getJournal() {
        return journal;
    }

    public void setJournal(Journal journal) {
        this.journal = journal;
    }

    public Processing getProcessing() {
        return processing;
    }

    public void setProcessing(Processing processing) {
        this.processing = processing;
    }

    public Resend getResend() {
        return resend;
    }

    public void setResend(Resend resend) {
        this.resend = resend;
    }

    public Notify getNotify() {
        return notify;
    }

    public void setNotify(Notify notify) {
        this.notify = notify;
    }

    public static class Smtp {
        private String bindAddress = "0.0.0.0";
        private String hostname = "journal-archiver.local";
        private int port = 2525;
        private int maxMessageSize = 20 * 1024 * 1024;
        private int maxConnections = 200;
        private int maxRecipients = 100;
        private int connectionTimeoutSeconds = 60;
        private boolean requireTls = false;
        private boolean disableReceivedHeaders = true;

        public String getBindAddress() {
            return bindAddress;
        }

        public void setBindAddress(String bindAddress) {
            this.bindAddress = bindAddress;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getMaxMessageSize() {
            return maxMessageSize;
        }

        public void setMaxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getMaxRecipients() {
            return maxRecipients;
        }

        public void setMaxRecipients(int maxRecipients) {
            this.maxRecipients = maxRecipients;
        }

        public int getConnectionTimeoutSeconds() {
            return connectionTimeoutSeconds;
        }

        public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        }

        public boolean isRequireTls() {
            return requireTls;
        }

        public void setRequireTls(boolean requireTls) {
            this.requireTls = requireTls;
        }

        public boolean isDisableReceivedHeaders() {
            return disableReceivedHeaders;
        }

        public void setDisableReceivedHeaders(boolean disableReceivedHeaders) {
            this.disableReceivedHeaders = disableReceivedHeaders;
        }
    }

    public static class Journal {
        private boolean extractOriginalAttachment = true;
        private boolean generateMessageIdIfMissing = true;
        private boolean detectByHeader = true;
        private boolean detectByRfc822Attachment = true;
        private String senderResolution = "header";
        private Filter filter = new Filter();

        public boolean isExtractOriginalAttachment() {
            return extractOriginalAttachment;
        }

        public void setExtractOriginalAttachment(boolean extractOriginalAttachment) {
            this.extractOriginalAttachment = extractOriginalAttachment;
        }

        public boolean isGenerateMessageIdIfMissing() {
            return generateMessageIdIfMissing;
        }

        public void setGenerateMessageIdIfMissing(boolean generateMessageIdIfMissing) {
            this.generateMessageIdIfMissing = generateMessageIdIfMissing;
        }

        public boolean isDetectByHeader() {
            return detectByHeader;
        }

        public void setDetectByHeader(boolean detectByHeader) {
            this.detectByHeader = detectByHeader;
        }

        public boolean isDetectByRfc822Attachment() {
            return detectByRfc822Attachment;
        }

        public void setDetectByRfc822Attachment(boolean detectByRfc822Attachment) {
            this.detectByRfc822Attachment = detectByRfc822Attachment;
        }

        public String getSenderResolution() {
            return senderResolution;
        }

        public void setSenderResolution(String senderResolution) {
            this.senderResolution = senderResolution;
        }

        public Filter getFilter() {
            return filter;
        }

        public void setFilter(Filter filter) {
            this.filter = filter;
        }

        /**
         * Collection filters applied to the original email before it is archived. Each configured
         * list is an allowlist of mailboxes; a list with no values disables that filter. Matching
         * is case-insensitive and ignores display names / surrounding angle brackets.
         */
        public static class Filter {
            private List<String> senderEmails = new ArrayList<>();
            private List<String> fromEmails = new ArrayList<>();
            private List<String> toEmails = new ArrayList<>();
            private List<String> ccEmails = new ArrayList<>();

            public List<String> getSenderEmails() {
                return senderEmails;
            }

            public void setSenderEmails(List<String> senderEmails) {
                this.senderEmails = senderEmails;
            }

            public List<String> getFromEmails() {
                return fromEmails;
            }

            public void setFromEmails(List<String> fromEmails) {
                this.fromEmails = fromEmails;
            }

            public List<String> getToEmails() {
                return toEmails;
            }

            public void setToEmails(List<String> toEmails) {
                this.toEmails = toEmails;
            }

            public List<String> getCcEmails() {
                return ccEmails;
            }

            public void setCcEmails(List<String> ccEmails) {
                this.ccEmails = ccEmails;
            }
        }
    }

    public static class Processing {
        private int corePoolSize = 2;
        private int maxPoolSize = 8;
        private int queueCapacity = 500;
        private int retryMaxAttempts = 3;
        private long retryBackoffMs = 1000;
        private String deadLetterDir = "./data/dead-letter";
        private String spoolDir = "./data/spool";
        private long spoolPollIntervalMs = 1000;
        private long spoolRetryDelayMs = 30000;
        private int spoolMaxBatch = 16;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getRetryMaxAttempts() {
            return retryMaxAttempts;
        }

        public void setRetryMaxAttempts(int retryMaxAttempts) {
            this.retryMaxAttempts = retryMaxAttempts;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public String getDeadLetterDir() {
            return deadLetterDir;
        }

        public void setDeadLetterDir(String deadLetterDir) {
            this.deadLetterDir = deadLetterDir;
        }

        public String getSpoolDir() {
            return spoolDir;
        }

        public void setSpoolDir(String spoolDir) {
            this.spoolDir = spoolDir;
        }

        public long getSpoolPollIntervalMs() {
            return spoolPollIntervalMs;
        }

        public void setSpoolPollIntervalMs(long spoolPollIntervalMs) {
            this.spoolPollIntervalMs = spoolPollIntervalMs;
        }

        public long getSpoolRetryDelayMs() {
            return spoolRetryDelayMs;
        }

        public void setSpoolRetryDelayMs(long spoolRetryDelayMs) {
            this.spoolRetryDelayMs = spoolRetryDelayMs;
        }

        public int getSpoolMaxBatch() {
            return spoolMaxBatch;
        }

        public void setSpoolMaxBatch(int spoolMaxBatch) {
            this.spoolMaxBatch = spoolMaxBatch;
        }
    }

    public static class Resend {
        private int maxIds = 1000;
        private int parallelism = 4;
        private int queueCapacity = 1000;

        public int getMaxIds() {
            return maxIds;
        }

        public void setMaxIds(int maxIds) {
            this.maxIds = maxIds;
        }

        public int getParallelism() {
            return parallelism;
        }

        public void setParallelism(int parallelism) {
            this.parallelism = parallelism;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    public static class Notify {
        private RocketMq rocketMq = new RocketMq();
        private Outbox outbox = new Outbox();

        public RocketMq getRocketMq() {
            return rocketMq;
        }

        public void setRocketMq(RocketMq rocketMq) {
            this.rocketMq = rocketMq;
        }

        public Outbox getOutbox() {
            return outbox;
        }

        public void setOutbox(Outbox outbox) {
            this.outbox = outbox;
        }

        public static class RocketMq {
            private boolean enabled = true;
            private String nameServer = "127.0.0.1:9876";
            private String producerGroup = "journal-archiver-producer";
            private String topic = "mail_meta_topic";
            private String tag = "mail-meta";
            private long sendTimeoutMs = 3000;
            private int retryTimesWhenSendFailed = 2;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getNameServer() {
                return nameServer;
            }

            public void setNameServer(String nameServer) {
                this.nameServer = nameServer;
            }

            public String getProducerGroup() {
                return producerGroup;
            }

            public void setProducerGroup(String producerGroup) {
                this.producerGroup = producerGroup;
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

            public long getSendTimeoutMs() {
                return sendTimeoutMs;
            }

            public void setSendTimeoutMs(long sendTimeoutMs) {
                this.sendTimeoutMs = sendTimeoutMs;
            }

            public int getRetryTimesWhenSendFailed() {
                return retryTimesWhenSendFailed;
            }

            public void setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) {
                this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
            }
        }

        public static class Outbox {
            private long scanIntervalMs = 30000;
            private long graceMs = 60000;
            private long retryBackoffMs = 30000;
            private int maxAttempts = 10;

            public long getScanIntervalMs() {
                return scanIntervalMs;
            }

            public void setScanIntervalMs(long scanIntervalMs) {
                this.scanIntervalMs = scanIntervalMs;
            }

            public long getGraceMs() {
                return graceMs;
            }

            public void setGraceMs(long graceMs) {
                this.graceMs = graceMs;
            }

            public long getRetryBackoffMs() {
                return retryBackoffMs;
            }

            public void setRetryBackoffMs(long retryBackoffMs) {
                this.retryBackoffMs = retryBackoffMs;
            }

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }
        }
    }
}
