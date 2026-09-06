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
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ekwong.mailmcpserver.download.MailOriginalDownloadRequest;
import uk.ekwong.mailmcpserver.download.MailOriginalDownloadService;
import uk.ekwong.mailmcpserver.download.StagedOriginalEmail;

/**
 * HTTP endpoint that downloads original emails as a zip archive.
 *
 * <pre>
 * POST /api/mail-originals/download
 * Content-Type: application/json
 *
 * {
 *   "ids": ["id-1", "id-2"]
 * }
 * </pre>
 *
 * The original {@code .eml} files are read from object storage into the server temporary directory
 * first, then compressed and returned as {@code application/zip}.
 */
@RestController
@RequestMapping("/api/mail-originals")
@Tag(
        name = "Original email download",
        description =
                "Download original .eml files from object storage as a zip archive by archive id")
public class MailOriginalDownloadController {

    public static final String ZIP_FILENAME = "mail-originals.zip";

    private final MailOriginalDownloadService downloadService;

    public MailOriginalDownloadController(MailOriginalDownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @PostMapping(value = "/download", produces = "application/zip")
    @Operation(
            summary = "Download original emails as a zip archive",
            description =
                    "Reads the original .eml files for the given archive ids (MongoDB _id / "
                            + "object storage key) from object storage into the server temporary "
                            + "directory, compresses them and returns an application/zip response. "
                            + "Blank ids are ignored, duplicates are collapsed; empty requests, more "
                            + "than 1000 ids, or missing objects return an error.")
    public void download(
            @RequestBody MailOriginalDownloadRequest request, HttpServletResponse response)
            throws IOException {
        List<StagedOriginalEmail> staged = downloadService.stage(request.ids());
        response.setContentType("application/zip");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(ZIP_FILENAME).build().toString());
        downloadService.writeZip(staged, response.getOutputStream());
    }
}
