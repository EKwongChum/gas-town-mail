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

package uk.ekwong.journalarchiver.smtp;

import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.service.JournalProcessingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.subethamail.smtp.server.SMTPServer;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Starts and stops the embedded SMTP server. The server binds to all
 * interfaces by default so it can receive journal reports from any source.
 */
@Component
public class SmtpReceiver {

    private static final Logger log = LoggerFactory.getLogger(SmtpReceiver.class);

    private final JournalProcessingService processingService;
    private final AppProperties properties;
    private SMTPServer server;

    public SmtpReceiver(JournalProcessingService processingService, AppProperties properties) {
        this.processingService = processingService;
        this.properties = properties;
    }

    @PostConstruct
    public void start() throws Exception {
        AppProperties.Smtp smtp = properties.getSmtp();

        Optional<InetAddress> bindAddress = isAnyAddress(smtp.getBindAddress())
                ? Optional.empty() // empty means all interfaces
                : Optional.of(InetAddress.getByName(smtp.getBindAddress()));

        server = SMTPServer.port(smtp.getPort())
                .hostName(smtp.getHostname())
                .bindAddress(bindAddress)
                .messageHandlerFactory(context ->
                        new CapturingMessageHandler(context, processingService, smtp.getMaxMessageSize()))
                .maxConnections(smtp.getMaxConnections())
                .maxRecipients(smtp.getMaxRecipients())
                .connectionTimeoutMs(smtp.getConnectionTimeoutSeconds() * 1000)
                .requireTLS(smtp.isRequireTls())
                .insertReceivedHeaders(!smtp.isDisableReceivedHeaders())
                .maxMessageSize(smtp.getMaxMessageSize())
                .build();

        server.start();
        log.info("SMTP server started on {}:{} (hostname={})",
                smtp.getBindAddress(), smtp.getPort(), smtp.getHostname());
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop();
            log.info("SMTP server stopped");
        }
    }

    private boolean isAnyAddress(String bindAddress) {
        return bindAddress == null
                || bindAddress.isBlank()
                || "0.0.0.0".equals(bindAddress)
                || "::".equals(bindAddress);
    }
}
