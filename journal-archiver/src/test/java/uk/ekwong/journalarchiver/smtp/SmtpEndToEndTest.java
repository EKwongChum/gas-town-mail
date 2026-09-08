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
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.subethamail.smtp.server.SMTPServer;
import uk.ekwong.journalarchiver.TestEmails;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;
import uk.ekwong.journalarchiver.notify.MailMetaPublisher;
import uk.ekwong.journalarchiver.service.DeadLetterStore;
import uk.ekwong.journalarchiver.service.JournalMailFilter;
import uk.ekwong.journalarchiver.service.JournalProcessingService;
import uk.ekwong.journalarchiver.spool.InboxRelay;
import uk.ekwong.journalarchiver.spool.MailInbox;
import uk.ekwong.mailcommon.mail.EmailDetailsExtractor;
import uk.ekwong.mailcommon.mail.EmailIdGenerator;
import uk.ekwong.mailcommon.mail.JournalDetector;
import uk.ekwong.mailcommon.mail.OriginalEmailExtractor;
import uk.ekwong.mailcommon.storage.ObjectStorageService;

class SmtpEndToEndTest {

    @TempDir Path tempDir;

    @Test
    void receivesJournalEmailViaSmtpAndArchivesIt() throws Exception {
        int port = freePort();
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MailMetaPublisher publisher = mock(MailMetaPublisher.class);
        DeadLetterStore deadLetterStore = mock(DeadLetterStore.class);
        AppProperties properties = new AppProperties();
        properties.getProcessing().setSpoolDir(tempDir.resolve("spool").toString());
        properties.getProcessing().setDeadLetterDir(tempDir.resolve("dead-letter").toString());
        MailInbox inbox = new MailInbox(properties, new ObjectMapper());

        JournalProcessingService processor =
                new JournalProcessingService(
                        new JournalDetector(),
                        new OriginalEmailExtractor(),
                        new EmailDetailsExtractor(),
                        mongoTemplate,
                        storage,
                        publisher,
                        deadLetterStore,
                        new JournalMailFilter(),
                        properties);

        String expectedId =
                EmailIdGenerator.generate(
                        "Alice <alice@example.com>", "<original-123@example.com>");
        JournalEmailInfo savedInfo = savedInfo(expectedId);
        when(mongoTemplate.findById(expectedId, JournalEmailInfo.class)).thenReturn(savedInfo);

        SMTPServer server =
                SMTPServer.port(port)
                        .hostName("test.local")
                        .insertReceivedHeaders(false)
                        .messageHandlerFactory(
                                context ->
                                        new CapturingMessageHandler(
                                                context,
                                                inbox,
                                                properties.getSmtp().getMaxMessageSize()))
                        .build();
        server.start();

        try {
            sendViaRawSmtp(port, TestEmails.journalReport());
            drainOnce(inbox, processor, properties);

            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
            verify(mongoTemplate)
                    .upsert(
                            queryCaptor.capture(),
                            updateCaptor.capture(),
                            eq(JournalEmailInfo.class));
            assertThat(queryCaptor.getValue().getQueryObject().get("_id")).isEqualTo(expectedId);
            Document updateDoc = updateCaptor.getValue().getUpdateObject();
            Document inc = (Document) updateDoc.get("$inc");
            assertThat(inc.get("modificationCount")).isEqualTo(1);
            assertThat(updateDoc.get("$setOnInsert")).isNotNull();
            assertThat(updateDoc.get("$set")).isNotNull();

            ArgumentCaptor<byte[]> objectCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(storage).store(eq(expectedId), objectCaptor.capture());
            String stored = new String(objectCaptor.getValue(), StandardCharsets.UTF_8);
            assertThat(stored).contains("Message-ID: <original-123@example.com>");
            assertThat(stored).contains("Hello Bob, please review the quarterly numbers.");
            assertThat(stored).doesNotContain("Journal report");

            ArgumentCaptor<JournalEmailInfo> notifyCaptor =
                    ArgumentCaptor.forClass(JournalEmailInfo.class);
            verify(publisher).publish(notifyCaptor.capture());
            assertThat(notifyCaptor.getValue().getId()).isEqualTo(expectedId);
            assertThat(notifyCaptor.getValue().getModificationCount()).isEqualTo(1);

            InOrder inOrder = inOrder(storage, mongoTemplate, publisher);
            inOrder.verify(storage).store(eq(expectedId), any(byte[].class));
            inOrder.verify(mongoTemplate)
                    .upsert(any(Query.class), any(Update.class), eq(JournalEmailInfo.class));
            inOrder.verify(publisher).publish(any(JournalEmailInfo.class));

            verifyNoInteractions(deadLetterStore);
        } finally {
            server.stop();
        }
    }

    @Test
    void ignoresPlainNonJournalEmail() throws Exception {
        int port = freePort();
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MailMetaPublisher publisher = mock(MailMetaPublisher.class);
        DeadLetterStore deadLetterStore = mock(DeadLetterStore.class);
        AppProperties properties = new AppProperties();
        properties.getProcessing().setSpoolDir(tempDir.resolve("spool-2").toString());
        properties.getProcessing().setDeadLetterDir(tempDir.resolve("dead-letter-2").toString());
        MailInbox inbox = new MailInbox(properties, new ObjectMapper());

        JournalProcessingService processor =
                new JournalProcessingService(
                        new JournalDetector(),
                        new OriginalEmailExtractor(),
                        new EmailDetailsExtractor(),
                        mongoTemplate,
                        storage,
                        publisher,
                        deadLetterStore,
                        new JournalMailFilter(),
                        properties);

        SMTPServer server =
                SMTPServer.port(port)
                        .hostName("test.local")
                        .messageHandlerFactory(
                                context ->
                                        new CapturingMessageHandler(
                                                context,
                                                inbox,
                                                properties.getSmtp().getMaxMessageSize()))
                        .build();
        server.start();

        try {
            sendViaRawSmtp(port, TestEmails.plainEmail());
            drainOnce(inbox, processor, properties);
            verifyNoInteractions(mongoTemplate, storage, publisher, deadLetterStore);
        } finally {
            server.stop();
        }
    }

    private void drainOnce(
            MailInbox inbox, JournalProcessingService processor, AppProperties properties) {
        Executor directExecutor = Runnable::run;
        new InboxRelay(inbox, processor, directExecutor, properties).pollOnce();
    }

    private JournalEmailInfo savedInfo(String id) {
        JournalEmailInfo info = new JournalEmailInfo();
        info.setId(id);
        info.setSender("Alice <alice@example.com>");
        info.setFrom("Alice <alice@example.com>");
        info.setTo("Bob <bob@example.com>");
        info.setCc("Carol <carol@example.com>");
        info.setSubject("Quarterly report");
        info.setMessageId("<original-123@example.com>");
        info.setEnvelopeSender("postmaster@corp.local");
        info.setRecipients(List.of("journal@archive.local"));
        info.setClientAddress("/127.0.0.1:1234");
        info.setReceivedAt(Instant.parse("2026-08-16T05:00:00Z"));
        info.setCreatedAt(Instant.parse("2026-08-16T05:00:00Z"));
        info.setUpdatedAt(Instant.parse("2026-08-16T05:00:00Z"));
        info.setModificationCount(1);
        info.setObjectKey(id);
        return info;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void sendViaRawSmtp(int port, String rawMessage) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port);
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream(), StandardCharsets.US_ASCII));
                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(10_000);

            expect(reader, "220");
            write(writer, "EHLO test-client.local");
            expect(reader, "250");
            write(writer, "MAIL FROM:<postmaster@corp.local>");
            expect(reader, "250");
            write(writer, "RCPT TO:<journal@archive.local>");
            expect(reader, "250");
            write(writer, "DATA");
            expect(reader, "354");
            writer.write(rawMessage);
            writer.write("\r\n.\r\n");
            writer.flush();
            expect(reader, "250");
            write(writer, "QUIT");
            expect(reader, "221");
        }
    }

    private static void write(BufferedWriter writer, String command) throws IOException {
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
    }

    private static void expect(BufferedReader reader, String code) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(code + " ")) {
                return;
            }
            if (line.startsWith(code + "-")) {
                continue;
            }
            fail("Unexpected SMTP response: " + line);
        }
        fail("SMTP connection closed before receiving " + code);
    }
}
