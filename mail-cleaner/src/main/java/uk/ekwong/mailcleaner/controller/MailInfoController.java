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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ekwong.mailcleaner.service.MailDeleteRequest;
import uk.ekwong.mailcleaner.service.MailDeleteResponse;
import uk.ekwong.mailcleaner.service.MailDeletionService;

/**
 * HTTP interface to manage the Elasticsearch {@code mail_info} index.
 *
 * <pre>
 * POST /api/mail-info/delete
 * Content-Type: application/json
 *
 * {
 *   "ids": ["id-1", "id-2"]
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/mail-info")
@Tag(
        name = "Mail info management",
        description = "Manage documents in the Elasticsearch mail_info index")
public class MailInfoController {

    private final MailDeletionService deletionService;

    public MailInfoController(MailDeletionService deletionService) {
        this.deletionService = deletionService;
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
}
