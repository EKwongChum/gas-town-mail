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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import uk.ekwong.journalarchiver.service.JournalProcessingService;
import uk.ekwong.mailcommon.trace.TraceIds;

/**
 * Handles a single SMTP mail transaction: captures the envelope sender and recipients, reads the
 * full message data and hands it to the processing pipeline.
 */
public class CapturingMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(CapturingMessageHandler.class);

    private final MessageContext context;
    private final JournalProcessingService processingService;
    private final int maxMessageSize;

    private String envelopeSender;
    private final List<String> recipients = new ArrayList<>();

    public CapturingMessageHandler(
            MessageContext context,
            JournalProcessingService processingService,
            int maxMessageSize) {
        this.context = context;
        this.processingService = processingService;
        this.maxMessageSize = maxMessageSize;
    }

    @Override
    public void from(String from) throws RejectException {
        this.envelopeSender = from;
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        this.recipients.add(recipient);
    }

    @Override
    public String data(InputStream data) throws RejectException, IOException {
        byte[] raw = readAll(data);
        SocketAddress clientAddress = context.getRemoteAddress();
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        MDC.put(TraceIds.MDC_KEY, TraceIds.generate());
        try {
            log.info(
                    "Received message via SMTP: client={}, envelope sender={}, recipients={}, size={} bytes",
                    clientAddress,
                    envelopeSender,
                    recipients,
                    raw.length);
            processingService.process(raw, envelopeSender, List.copyOf(recipients), clientAddress);
            return null; // keep the standard "250 Ok" response
        } finally {
            restoreContext(previousContext);
        }
    }

    @Override
    public void done() {
        // nothing to do; processing is handed off asynchronously
    }

    private byte[] readAll(InputStream in) throws IOException, RejectException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxMessageSize) {
                // 552 = message size exceeds fixed maximum message size
                throw new RejectException(
                        552,
                        "Message exceeds the maximum allowed size of " + maxMessageSize + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private void restoreContext(Map<String, String> previousContext) {
        if (previousContext == null || previousContext.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
    }
}
