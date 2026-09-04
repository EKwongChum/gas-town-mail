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

package uk.ekwong.mailcommon.es;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch document in the {@code mail_info} index. The id is the archive id (MongoDB document
 * id / object storage key), so re-processing the same notification updates the same document
 * instead of duplicating it. Shared by mail-cleaner (writer) and mail-mcp-server (reader).
 */
@Document(indexName = "mail_info")
public class MailInfoDocument {

    @Id private String id;

    @Field(type = FieldType.Keyword)
    private String sender;

    @Field(type = FieldType.Keyword)
    private String from;

    @Field(type = FieldType.Keyword)
    private String to;

    @Field(type = FieldType.Keyword)
    private String cc;

    @Field(type = FieldType.Keyword)
    private String messageId;

    @Field(type = FieldType.Date)
    private Instant receivedTime;

    @Field(type = FieldType.Text)
    private String subject;

    @Field(type = FieldType.Keyword)
    private String contentType;

    @Field(type = FieldType.Keyword)
    private List<String> attachmentNames;

    public MailInfoDocument() {}

    public static MailInfoDocument from(
            String id,
            String sender,
            String from,
            String to,
            String cc,
            String messageId,
            Instant receivedTime,
            String subject,
            String contentType,
            List<String> attachmentNames) {
        MailInfoDocument doc = new MailInfoDocument();
        doc.id = id;
        doc.sender = sender;
        doc.from = from;
        doc.to = to;
        doc.cc = cc;
        doc.messageId = messageId;
        doc.receivedTime = receivedTime;
        doc.subject = subject;
        doc.contentType = contentType;
        doc.attachmentNames = attachmentNames;
        return doc;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Instant getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(Instant receivedTime) {
        this.receivedTime = receivedTime;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public List<String> getAttachmentNames() {
        return attachmentNames;
    }

    public void setAttachmentNames(List<String> attachmentNames) {
        this.attachmentNames = attachmentNames;
    }
}
