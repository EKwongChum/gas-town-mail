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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import uk.ekwong.mailmcpserver.download.OriginalMailNotFoundException;
import uk.ekwong.mailmcpserver.send.MailSendFailedException;
import uk.ekwong.mailmcpserver.send.SmtpHostNotAllowedException;

/**
 * Error handling for the outbound mail endpoints: {@code 400} for invalid requests (missing SMTP
 * coordinates, malformed addresses, attachment limits), {@code 404} when the archived original mail
 * of a reply/forward does not exist and {@code 502} when the SMTP server rejected the mail or could
 * not be reached. Multipart requests additionally map an exceeded upload limit and a missing {@code
 * request} part to {@code 400}, and an unsupported content type to {@code 415}.
 */
@RestControllerAdvice(assignableTypes = MailSendController.class)
public class MailSendExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MailSendExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
        log.warn("Invalid mail send request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(SmtpHostNotAllowedException.class)
    public ResponseEntity<ApiError> handleHostNotAllowed(SmtpHostNotAllowedException e) {
        log.warn("Rejected an SMTP host: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ApiError("Malformed request body"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("Missing multipart part: {}", e.getRequestPartName());
        return ResponseEntity.badRequest()
                .body(new ApiError("Missing required part '" + e.getRequestPartName() + "'"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("Rejected an oversized multipart upload: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(
                        new ApiError(
                                "Uploaded attachments exceed the multipart limits "
                                        + "(spring.servlet.multipart.max-file-size / max-request-size)"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported request content type: {}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(
                        new ApiError(
                                "Unsupported request content type: use application/json, or "
                                        + "multipart/form-data whose 'request' part is "
                                        + "application/json"));
    }

    @ExceptionHandler(OriginalMailNotFoundException.class)
    public ResponseEntity<ApiError> handleOriginalEmailNotFound(OriginalMailNotFoundException e) {
        log.warn("Original email not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(MailSendFailedException.class)
    public ResponseEntity<ApiError> handleSendFailed(MailSendFailedException e) {
        log.error("Sending mail failed: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unhandled exception in the mail send endpoint", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Internal server error"));
    }

    public record ApiError(String error) {}
}
