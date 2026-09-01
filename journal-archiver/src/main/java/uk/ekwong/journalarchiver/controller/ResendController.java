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

package uk.ekwong.journalarchiver.controller;

import uk.ekwong.journalarchiver.model.ResendRequest;
import uk.ekwong.journalarchiver.model.ResendResponse;
import uk.ekwong.journalarchiver.service.ResendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP interface to re-publish archived email notifications.
 *
 * <pre>
 * POST /api/journal-emails/resend
 * Content-Type: application/json
 *
 * {
 *   "timeGe": "2026-08-16T00:00:00Z",   // optional, createdAt >= timeGe
 *   "timeLt": "2026-08-17T00:00:00Z",   // optional, createdAt < timeLt
 *   "ids": ["id-1", "id-2"]             // optional, takes precedence when provided
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/journal-emails")
@Tag(name = "Notification resend", description = "Re-publish mail_meta_topic notifications for archived emails")
public class ResendController {

    private final ResendService resendService;

    public ResendController(ResendService resendService) {
        this.resendService = resendService;
    }

    @PostMapping("/resend")
    @Operation(summary = "Resend archive notifications",
            description = "Re-publishes mail_meta_topic notifications for archived emails. "
                    + "Provide ids to resend specific documents (takes precedence), or a "
                    + "[timeGe, timeLt) range to resend documents created in that interval.")
    public ResendResponse resend(@RequestBody ResendRequest request) {
        return resendService.resend(request);
    }
}
