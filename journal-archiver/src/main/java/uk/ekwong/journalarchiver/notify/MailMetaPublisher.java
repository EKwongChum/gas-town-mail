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

import uk.ekwong.journalarchiver.model.JournalEmailInfo;

/**
 * Publishes a notification once an email is fully archived (metadata in MongoDB and raw email in
 * object storage).
 */
public interface MailMetaPublisher {

    /**
     * Publishes a notification for an archived email.
     *
     * @return {@code true} when the message was published successfully, {@code false} otherwise
     *     (disabled or delivery failed)
     */
    boolean publish(JournalEmailInfo info);
}
