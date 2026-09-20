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
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.ekwong.mailmcpserver.send.MailCompositionService;
import uk.ekwong.mailmcpserver.send.MailForwardRequest;
import uk.ekwong.mailmcpserver.send.MailReplyRequest;
import uk.ekwong.mailmcpserver.send.MailSendCommand;
import uk.ekwong.mailmcpserver.send.MailSendIdempotencyStore;
import uk.ekwong.mailmcpserver.send.MailSendInProgressException;
import uk.ekwong.mailmcpserver.send.MailSendProperties;
import uk.ekwong.mailmcpserver.send.MailSendRequest;
import uk.ekwong.mailmcpserver.send.MailSendRequestMapper;
import uk.ekwong.mailmcpserver.send.MailSendResponse;
import uk.ekwong.mailmcpserver.send.MailSendService;
import uk.ekwong.mailmcpserver.send.MailSendTask;
import uk.ekwong.mailmcpserver.send.MailSendTaskNotFoundException;
import uk.ekwong.mailmcpserver.send.MailSendTaskService;

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
 * <p>All three endpoints accept either {@code application/json} with Base64 attachments or {@code
 * multipart/form-data}, where the {@code request} part carries the same JSON body and every
 * attachment is uploaded as its own {@code attachments} file part.
 *
 * <p>The archive id of the reply and forward endpoints is the id returned by the MCP tools {@code
 * search_mails} / {@code get_mail_by_id} (MongoDB {@code _id} / object storage key): the archived
 * original email is read from object storage and reused for recipients, subject, body quoting and
 * attachments. The SMTP server, account and password are taken from the request and are never
 * stored, so any mailbox can be used as the sender.
 *
 * <p>Retries can be made safe with an {@code Idempotency-Key} header and the {@code Prefer:
 * respond-async} header makes the delivery run in the background ({@code 202} plus {@code GET
 * /api/mails/tasks/{id}}).
 */
@RestController
@RequestMapping("/api/mails")
@Tag(
        name = "Mail sending",
        description =
                "Send mail through an SMTP server given in the request, reply to or forward an "
                        + "archived mail by its archive id")
public class MailSendController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    static final String PREFER_HEADER = "Prefer";

    private static final String ASYNC_PREFERENCE = "respond-async";
    private static final String TASKS_PATH = "/api/mails/tasks";

    /**
     * Every endpoint accepts both content types, so both mappings of a path carry the same
     * documentation: springdoc merges them into a single OpenAPI operation.
     */
    private static final String CONTENT_TYPE_NOTE =
            " Accepts application/json with Base64 attachments (at most 10 MB each and 20 MB in "
                    + "total) or multipart/form-data, where the 'request' part carries the same JSON "
                    + "body (Content-Type: application/json) and every attachment is uploaded as its "
                    + "own 'attachments' file part.";

    /** Delivery behaviour shared by the three endpoints. */
    private static final String DELIVERY_NOTE =
            " Send an 'Idempotency-Key' header to make a retry safe: a repeated request with the "
                    + "same key returns the first result instead of delivering the mail twice. Send "
                    + "'Prefer: respond-async' to deliver in the background: the call then answers "
                    + "202 with a task id that can be polled at GET /api/mails/tasks/{id}.";

    private static final String SEND_DESCRIPTION =
            "Sends one mail through the SMTP server given in the request body."
                    + CONTENT_TYPE_NOTE
                    + " The response contains the generated Message-ID and the envelope sender that "
                    + "was used."
                    + DELIVERY_NOTE;

    private static final String REPLY_DESCRIPTION =
            "Sends a reply to the archived mail with the given archive id (the id returned by the "
                    + "MCP tools search_mails / get_mail_by_id): the original Reply-To/From becomes "
                    + "the recipient unless to is given, the subject is prefixed with Re:, the "
                    + "original body is quoted and the reply keeps the original thread via "
                    + "In-Reply-To/References."
                    + CONTENT_TYPE_NOTE
                    + DELIVERY_NOTE;

    private static final String FORWARD_DESCRIPTION =
            "Forwards the archived mail with the given archive id (the id returned by the MCP tools "
                    + "search_mails / get_mail_by_id) to the recipients in the request: the subject "
                    + "is prefixed with Fwd:, the original body is embedded in a forwarded-message "
                    + "block and the original attachments are carried over unless "
                    + "includeOriginalAttachments is false."
                    + CONTENT_TYPE_NOTE
                    + DELIVERY_NOTE;

    private final MailSendService sendService;
    private final MailSendRequestMapper requestMapper;
    private final MailCompositionService compositionService;
    private final MailSendIdempotencyStore idempotencyStore;
    private final MailSendTaskService taskService;
    private final MailSendProperties properties;

    public MailSendController(
            MailSendService sendService,
            MailSendRequestMapper requestMapper,
            MailCompositionService compositionService,
            MailSendIdempotencyStore idempotencyStore,
            MailSendTaskService taskService,
            MailSendProperties properties) {
        this.sendService = sendService;
        this.requestMapper = requestMapper;
        this.compositionService = compositionService;
        this.idempotencyStore = idempotencyStore;
        this.taskService = taskService;
        this.properties = properties;
    }

    @PostMapping("/send")
    @Operation(operationId = "sendMail", summary = "Send a mail", description = SEND_DESCRIPTION)
    public ResponseEntity<Object> send(
            @RequestBody MailSendRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = PREFER_HEADER, required = false) String prefer) {
        return deliver(idempotencyKey, prefer, requestMapper.toCommand(request), false);
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "sendMail", summary = "Send a mail", description = SEND_DESCRIPTION)
    public ResponseEntity<Object> sendMultipart(
            @RequestPart("request") MailSendRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = PREFER_HEADER, required = false) String prefer) {
        return deliver(
                idempotencyKey,
                prefer,
                requestMapper.toMultipartCommand(request, attachments),
                true);
    }

    @PostMapping("/reply")
    @Operation(
            operationId = "replyMail",
            summary = "Reply to an archived mail",
            description = REPLY_DESCRIPTION)
    public ResponseEntity<Object> reply(
            @RequestBody MailReplyRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = PREFER_HEADER, required = false) String prefer) {
        return deliver(idempotencyKey, prefer, compositionService.composeReply(request), false);
    }

    @PostMapping(value = "/reply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "replyMail",
            summary = "Reply to an archived mail",
            description = REPLY_DESCRIPTION)
    public ResponseEntity<Object> replyMultipart(
            @RequestPart("request") MailReplyRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = PREFER_HEADER, required = false) String prefer) {
        return deliver(
                idempotencyKey,
                prefer,
                compositionService.composeReply(
                        request, requestMapper.toMultipartCommand(request, attachments)),
                true);
    }

    @PostMapping("/forward")
    @Operation(
            operationId = "forwardMail",
            summary = "Forward an archived mail",
            description = FORWARD_DESCRIPTION)
    public ResponseEntity<Object> forward(
            @RequestBody MailForwardRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = PREFER_HEADER, required = false) String prefer) {
        return deliver(idempotencyKey, prefer, compositionService.composeForward(request), false);
    }

    @PostMapping(value = "/forward", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "forwardMail",
            summary = "Forward an archived mail",
            description = FORWARD_DESCRIPTION)
    public ResponseEntity<Object> forwardMultipart(
            @RequestPart("request") MailForwardRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = PREFER_HEADER, required = false) String prefer) {
        return deliver(
                idempotencyKey,
                prefer,
                compositionService.composeForward(
                        request, requestMapper.toMultipartCommand(request, attachments)),
                true);
    }

    @GetMapping("/tasks/{id}")
    @Operation(
            summary = "State of a background delivery",
            description =
                    "Returns PENDING while the mail is being delivered, then SUCCEEDED with the "
                            + "result of the delivery or FAILED with its error. Tasks are kept for "
                            + "app.send.async.task-ttl and return 404 afterwards.")
    public MailSendTask task(@PathVariable String id) {
        return taskService.find(id).orElseThrow(() -> new MailSendTaskNotFoundException(id));
    }

    /**
     * Runs one delivery, honouring the idempotency key and the asynchronous preference of the
     * request.
     */
    private ResponseEntity<Object> deliver(
            String idempotencyKey,
            String prefer,
            MailSendCommand command,
            boolean attachmentsFromRequest) {
        String key = idempotencyStore.enabled() ? idempotencyStore.normalize(idempotencyKey) : null;
        String token = null;
        if (key != null) {
            MailSendIdempotencyStore.Reservation reservation = idempotencyStore.reserve(key);
            if (reservation instanceof MailSendIdempotencyStore.Reservation.Completed completed) {
                return replay(completed.result());
            }
            if (reservation instanceof MailSendIdempotencyStore.Reservation.InFlight) {
                throw new MailSendInProgressException(key);
            }
            token = ((MailSendIdempotencyStore.Reservation.Acquired) reservation).token();
        }
        try {
            if (asynchronous(prefer)) {
                // a background delivery outlives the request, so streamed uploads have to be read
                MailSendCommand queued =
                        attachmentsFromRequest
                                ? requestMapper.materializeAttachments(command)
                                : command;
                return accept(queued, key, token);
            }
            MailSendResponse response = sendService.send(command);
            if (key != null) {
                idempotencyStore.complete(
                        key, token, MailSendIdempotencyStore.StoredSend.of(response));
            }
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (key != null) {
                idempotencyStore.release(key, token);
            }
            throw e;
        }
    }

    private ResponseEntity<Object> replay(MailSendIdempotencyStore.StoredSend stored) {
        if (stored.asyncTaskId() == null) {
            return ResponseEntity.ok(stored.response());
        }
        String status =
                taskService
                        .find(stored.asyncTaskId())
                        .map(task -> task.status().name())
                        .orElse("UNKNOWN");
        return accepted(stored.asyncTaskId(), status);
    }

    private ResponseEntity<Object> accept(MailSendCommand command, String key, String token) {
        if (!properties.getAsync().isEnabled()) {
            throw new IllegalArgumentException(
                    "asynchronous delivery is disabled (app.send.async.enabled=false)");
        }
        MailSendTask task = taskService.submit(command);
        if (key != null) {
            idempotencyStore.complete(
                    key, token, MailSendIdempotencyStore.StoredSend.ofTask(task.id()));
        }
        return accepted(task.id(), task.status().name());
    }

    private ResponseEntity<Object> accepted(String taskId, String status) {
        String location = TASKS_PATH + "/" + taskId;
        return ResponseEntity.accepted()
                .location(URI.create(location))
                .body(new MailSendAcceptedResponse(taskId, status, location));
    }

    private boolean asynchronous(String prefer) {
        return prefer != null
                && Arrays.stream(prefer.split(","))
                        .map(String::trim)
                        .anyMatch(value -> value.equalsIgnoreCase(ASYNC_PREFERENCE));
    }
}
