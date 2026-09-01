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

package uk.ekwong.journalarchiver.notify;

import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RocketMailMetaPublisherTest {

    private final DefaultMQProducer producer = mock(DefaultMQProducer.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void publishesJsonPayloadWithMongoIdAsMessageKey() throws Exception {
        AppProperties properties = new AppProperties();
        RocketMailMetaPublisher publisher = new RocketMailMetaPublisher(producer, objectMapper, properties);
        JournalEmailInfo info = sampleInfo();
        when(producer.send(any(Message.class), anyLong())).thenReturn(mock(SendResult.class));

        assertThat(publisher.publish(info)).isTrue();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(messageCaptor.capture(), eq(3000L));
        Message sent = messageCaptor.getValue();
        assertThat(sent.getTopic()).isEqualTo("mail_meta_topic");
        assertThat(sent.getTags()).isEqualTo("mail-meta");
        // the message key is the MongoDB document id (also the object storage key)
        assertThat(sent.getKeys()).isEqualTo(info.getId());
        String body = new String(sent.getBody(), StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"id\":\"" + info.getId() + "\"")
                .contains("\"messageId\":\"<m-1@example.com>\"")
                .contains("\"objectKey\":\"" + info.getObjectKey() + "\"")
                .contains("\"modificationCount\":3");
    }

    @Test
    void publishesWithoutTagWhenTagIsBlank() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getNotify().getRocketMq().setTag(" ");
        RocketMailMetaPublisher publisher = new RocketMailMetaPublisher(producer, objectMapper, properties);
        when(producer.send(any(Message.class), anyLong())).thenReturn(mock(SendResult.class));

        assertThat(publisher.publish(sampleInfo())).isTrue();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(messageCaptor.capture(), eq(3000L));
        // an empty tag is not stored as a property by the RocketMQ client
        assertThat(messageCaptor.getValue().getTags()).isNullOrEmpty();
        assertThat(messageCaptor.getValue().getKeys()).isNotNull();
    }

    @Test
    void skipsPublishWhenDisabled() {
        AppProperties properties = new AppProperties();
        properties.getNotify().getRocketMq().setEnabled(false);
        RocketMailMetaPublisher publisher = new RocketMailMetaPublisher(producer, objectMapper, properties);

        assertThat(publisher.publish(sampleInfo())).isFalse();
        verifyNoInteractions(producer);
    }

    @Test
    void returnsFalseWhenSendFails() throws Exception {
        AppProperties properties = new AppProperties();
        RocketMailMetaPublisher publisher = new RocketMailMetaPublisher(producer, objectMapper, properties);
        when(producer.send(any(Message.class), anyLong()))
                .thenThrow(new MQClientException("name server unreachable", null));

        assertThat(publisher.publish(sampleInfo())).isFalse();
    }

    private JournalEmailInfo sampleInfo() {
        JournalEmailInfo info = new JournalEmailInfo();
        info.setId("QWxpY2UgPGFsaWNlQGV4YW1wbGUuY29tPg==_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=");
        info.setSender("Alice <alice@example.com>");
        info.setFrom("Alice <alice@example.com>");
        info.setTo("Bob <bob@example.com>");
        info.setCc("Carol <carol@example.com>");
        info.setSubject("Quarterly report");
        info.setMessageId("<m-1@example.com>");
        info.setEnvelopeSender("postmaster@corp.local");
        info.setObjectKey(info.getId());
        info.setReceivedAt(Instant.parse("2026-08-16T05:00:00Z"));
        info.setCreatedAt(Instant.parse("2026-08-16T05:00:00Z"));
        info.setUpdatedAt(Instant.parse("2026-08-16T06:00:00Z"));
        info.setModificationCount(3);
        return info;
    }
}
