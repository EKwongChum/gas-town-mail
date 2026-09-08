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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.RejectException;
import uk.ekwong.journalarchiver.service.JournalProcessingService;
import uk.ekwong.mailcommon.trace.TraceIds;

class CapturingMessageHandlerTest {

    private final MessageContext context = mock(MessageContext.class);
    private final JournalProcessingService processingService = mock(JournalProcessingService.class);
    private final SocketAddress clientAddress = new InetSocketAddress("127.0.0.1", 12345);

    @BeforeEach
    void setUp() {
        MDC.clear();
        when(context.getRemoteAddress()).thenReturn(clientAddress);
    }

    @Test
    void capturesEnvelopeAndDelegatesRawMessageBytes() throws Exception {
        CapturingMessageHandler handler =
                new CapturingMessageHandler(context, processingService, 1024 * 1024);
        handler.from("sender@example.com");
        handler.recipient("to@example.com");
        handler.recipient("cc@example.com");

        String raw = "From: a@example.com\r\nSubject: test\r\n\r\nbody";

        assertThat(handler.data(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8))))
                .isNull();

        verify(processingService)
                .process(
                        raw.getBytes(StandardCharsets.UTF_8),
                        "sender@example.com",
                        List.of("to@example.com", "cc@example.com"),
                        clientAddress);
    }

    @Test
    void rejectsMessageExceedingMaxSizeWithSmtp552() throws Exception {
        CapturingMessageHandler handler =
                new CapturingMessageHandler(context, processingService, 8);
        handler.from("sender@example.com");
        handler.recipient("to@example.com");

        assertThatThrownBy(
                        () ->
                                handler.data(
                                        new ByteArrayInputStream(
                                                "0123456789".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(
                        RejectException.class, e -> assertThat(e.getCode()).isEqualTo(552));

        verifyNoInteractions(processingService);
    }

    @Test
    void exposesTraceIdWhileProcessingAndCleansItUpAfterwards() throws Exception {
        CapturingMessageHandler handler =
                new CapturingMessageHandler(context, processingService, 1024 * 1024);
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            seenTraceId.set(MDC.get(TraceIds.MDC_KEY));
                            return null;
                        })
                .when(processingService)
                .process(any(), any(), any(), any());

        handler.data(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));

        assertThat(seenTraceId.get()).matches("[0-9a-f-]{36}");
        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }
}
