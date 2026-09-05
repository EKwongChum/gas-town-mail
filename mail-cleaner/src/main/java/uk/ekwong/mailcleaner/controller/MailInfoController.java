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

package uk.ekwong.mailcleaner.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ekwong.mailcleaner.service.MailDeleteRequest;
import uk.ekwong.mailcleaner.service.MailDeleteResponse;
import uk.ekwong.mailcleaner.service.MailDeletionService;
import uk.ekwong.mailcleaner.service.OriginalEmailDownloadService;

/**
 * HTTP interface to manage archived emails: batch-delete Elasticsearch {@code mail_info} documents
 * and download the original email as {@code .eml}.
 *
 * <pre>
 * POST /api/mail-info/delete
 * Content-Type: application/json
 *
 * {
 *   "ids": ["id-1", "id-2"]
 * }
 *
 * GET /api/mail-info/original?id=id-1
 * </pre>
 */
@RestController
@RequestMapping("/api/mail-info")
@Tag(
        name = "Mail info management",
        description =
                "Manage archived emails: delete Elasticsearch mail_info documents and download "
                        + "original .eml content from object storage")
public class MailInfoController {

    private static final MediaType MESSAGE_RFC822 = MediaType.parseMediaType("message/rfc822");

    private final MailDeletionService deletionService;
    private final OriginalEmailDownloadService originalEmailDownloadService;

    public MailInfoController(
            MailDeletionService deletionService,
            OriginalEmailDownloadService originalEmailDownloadService) {
        this.deletionService = deletionService;
        this.originalEmailDownloadService = originalEmailDownloadService;
    }

    @PostMapping("/delete")
    @Operation(
            summary = "Batch delete Elasticsearch documents by id",
            description =
                    "Deletes the mail_info documents with the given archive ids "
                            + "(MongoDB _id / object storage key). Blank ids are ignored, duplicates are "
                            + "collapsed, and ids that do not exist are reported in notFoundIds. "
                            + "Empty requests or more than 1000 ids return 400.")
    public MailDeleteResponse delete(@RequestBody MailDeleteRequest request) {
        return deletionService.deleteByIds(request.ids());
    }

    @GetMapping("/original")
    @Operation(
            summary = "Download the original email as .eml",
            description =
                    "Downloads the raw original email stored in object storage as message/rfc822. "
                            + "The id is the archive id (MongoDB _id / object storage key), so it "
                            + "should be URL-encoded when it contains reserved characters. Returns "
                            + "the bytes as an attachment with an .eml filename, or 404 when the "
                            + "object does not exist.")
    public ResponseEntity<byte[]> downloadOriginal(@RequestParam String id) {
        byte[] content = originalEmailDownloadService.download(id);
        return ResponseEntity.ok()
                .contentType(MESSAGE_RFC822)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(id))
                .body(content);
    }

    private String contentDisposition(String id) {
        String filename = id.trim().replaceAll("[^A-Za-z0-9._-]", "_") + ".eml";
        return ContentDisposition.attachment().filename(filename).build().toString();
    }
}
