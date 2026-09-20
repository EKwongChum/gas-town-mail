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

import java.util.List;

/**
 * The send parameters shared by the send, reply and forward endpoints (reply and forward add the
 * archive id of the mail they are based on on top of these fields).
 */
public interface MailSendFields {

    String smtpHost();

    Integer smtpPort();

    String smtpUsername();

    String smtpPassword();

    String smtpEncryption();

    String from();

    List<String> to();

    List<String> cc();

    String subject();

    String content();

    Boolean html();

    List<MailAttachmentRequest> attachments();
}
