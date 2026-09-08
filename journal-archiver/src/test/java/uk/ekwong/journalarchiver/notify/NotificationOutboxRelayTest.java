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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.ekwong.journalarchiver.config.AppProperties;
import uk.ekwong.journalarchiver.model.JournalEmailInfo;

class NotificationOutboxRelayTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final MailMetaPublisher publisher = mock(MailMetaPublisher.class);
    private final AppProperties properties = new AppProperties();
    private final NotificationOutboxRelay relay =
            new NotificationOutboxRelay(mongoTemplate, publisher, properties);

    @Test
    void marksSentWhenOutboxPublishSucceeds() {
        JournalEmailInfo claimed = info("id-1", 1);
        AtomicInteger calls = new AtomicInteger();
        when(mongoTemplate.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(JournalEmailInfo.class)))
                .thenAnswer(invocation -> calls.getAndIncrement() == 0 ? claimed : null);
        when(publisher.publish(claimed)).thenReturn(true);

        assertThat(relay.scanOnce()).isEqualTo(1);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate)
                .updateFirst(any(Query.class), updateCaptor.capture(), eq(JournalEmailInfo.class));
        Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertThat(set.get("notificationStatus").toString()).isEqualTo("SENT");
    }

    @Test
    void marksFailedWhenMaxAttemptsExhausted() {
        JournalEmailInfo claimed = info("id-2", 10);
        AtomicInteger calls = new AtomicInteger();
        when(mongoTemplate.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(JournalEmailInfo.class)))
                .thenAnswer(invocation -> calls.getAndIncrement() == 0 ? claimed : null);
        when(publisher.publish(claimed)).thenReturn(false);

        assertThat(relay.scanOnce()).isEqualTo(1);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate)
                .updateFirst(any(Query.class), updateCaptor.capture(), eq(JournalEmailInfo.class));
        Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertThat(set.get("notificationStatus").toString()).isEqualTo("FAILED");
    }

    @Test
    void skipsScanWhenRocketMqNotificationsAreDisabled() {
        properties.getNotify().getRocketMq().setEnabled(false);

        assertThat(relay.scanOnce()).isZero();

        verify(mongoTemplate, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(JournalEmailInfo.class));
    }

    private JournalEmailInfo info(String id, int attempts) {
        JournalEmailInfo info = new JournalEmailInfo();
        info.setId(id);
        info.setObjectKey(id);
        info.setNotificationStatus(NotificationStatus.PENDING);
        info.setNotificationAttemptCount(attempts);
        return info;
    }
}
