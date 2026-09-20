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
 * One attachment of an outgoing mail, Base64 encoded because the endpoints take JSON bodies.
 *
 * <pre>
 * {
 *   "filename": "report.pdf",
 *   "contentType": "application/pdf",
 *   "contentBase64": "JVBERi0xLjcK..."
 * }
 * </pre>
 *
 * @param filename file name shown by the receiving mail client (required)
 * @param contentType MIME content type; {@code application/octet-stream} when omitted
 * @param contentBase64 attachment bytes, Base64 encoded; line breaks are tolerated
 */
public record MailAttachmentRequest(String filename, String contentType, String contentBase64) {}
