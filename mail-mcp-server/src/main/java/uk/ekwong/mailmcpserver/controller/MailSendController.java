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

package uk.ekwong.mailmcpserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ekwong.mailmcpserver.send.MailCompositionService;
import uk.ekwong.mailmcpserver.send.MailForwardRequest;
import uk.ekwong.mailmcpserver.send.MailReplyRequest;
import uk.ekwong.mailmcpserver.send.MailSendRequest;
import uk.ekwong.mailmcpserver.send.MailSendRequestMapper;
import uk.ekwong.mailmcpserver.send.MailSendResponse;
import uk.ekwong.mailmcpserver.send.MailSendService;

/**
 * HTTP interface to send mail through an SMTP server supplied with each request, and to answer or
 * forward an archived mail.
 *
 * <pre>
 * POST /api/mails/send      {"smtpHost": "...", "to": ["..."], "subject": "...", ...}
 * POST /api/mails/reply     {"id": "&lt;archive id&gt;", "smtpHost": "...", ...}
 * POST /api/mails/forward   {"id": "&lt;archive id&gt;", "smtpHost": "...", "to": ["..."], ...}
 * </pre>
 *
 * <p>The archive id of the reply and forward endpoints is the id returned by the MCP tools {@code
 * search_mails} / {@code get_mail_by_id} (MongoDB {@code _id} / object storage key): the archived
 * original email is read from object storage and reused for recipients, subject, body quoting and
 * attachments. The SMTP server, account and password are taken from the request and are never
 * stored, so any mailbox can be used as the sender.
 */
@RestController
@RequestMapping("/api/mails")
@Tag(
        name = "Mail sending",
        description =
                "Send mail through an SMTP server given in the request, reply to or forward an "
                        + "archived mail by its archive id")
public class MailSendController {

    private final MailSendService sendService;
    private final MailSendRequestMapper requestMapper;
    private final MailCompositionService compositionService;

    public MailSendController(
            MailSendService sendService,
            MailSendRequestMapper requestMapper,
            MailCompositionService compositionService) {
        this.sendService = sendService;
        this.requestMapper = requestMapper;
        this.compositionService = compositionService;
    }

    @PostMapping("/send")
    @Operation(
            summary = "Send a mail",
            description =
                    "Sends one mail through the SMTP server given in the request body. "
                            + "Attachments are Base64 encoded, each attachment may be at most "
                            + "10 MB and all attachments together at most 20 MB. The response "
                            + "contains the generated Message-ID.")
    public MailSendResponse send(@RequestBody MailSendRequest request) {
        return sendService.send(requestMapper.toCommand(request));
    }

    @PostMapping("/reply")
    @Operation(
            summary = "Reply to an archived mail",
            description =
                    "Sends a reply to the archived mail with the given archive id (the id "
                            + "returned by the MCP tools search_mails / get_mail_by_id). The "
                            + "original Reply-To/From becomes the recipient unless to is given, the "
                            + "subject is prefixed with Re:, the original body is quoted and the "
                            + "reply keeps the original thread via In-Reply-To/References.")
    public MailSendResponse reply(@RequestBody MailReplyRequest request) {
        return sendService.send(compositionService.composeReply(request));
    }

    @PostMapping("/forward")
    @Operation(
            summary = "Forward an archived mail",
            description =
                    "Forwards the archived mail with the given archive id (the id returned by the "
                            + "MCP tools search_mails / get_mail_by_id) to the recipients in the "
                            + "request. The subject is prefixed with Fwd:, the original body is "
                            + "embedded in a forwarded-message block and the original attachments "
                            + "are carried over unless includeOriginalAttachments is false.")
    public MailSendResponse forward(@RequestBody MailForwardRequest request) {
        return sendService.send(compositionService.composeForward(request));
    }
}
