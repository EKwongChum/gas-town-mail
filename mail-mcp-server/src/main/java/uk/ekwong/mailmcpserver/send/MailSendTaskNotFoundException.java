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

/**
 * Thrown when a background delivery is unknown or its state has expired (mapped to HTTP {@code
 * 404}).
 */
public class MailSendTaskNotFoundException extends RuntimeException {

    public MailSendTaskNotFoundException(String taskId) {
        super("No mail delivery task with id '" + taskId + "'");
    }
}
