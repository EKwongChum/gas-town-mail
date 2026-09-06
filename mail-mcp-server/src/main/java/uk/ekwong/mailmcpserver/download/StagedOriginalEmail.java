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

package uk.ekwong.mailmcpserver.download;

import java.nio.file.Path;

/**
 * A staged original email file waiting to be packed into the zip archive.
 *
 * @param archiveId archive id of the email (MongoDB id / object storage key)
 * @param file temporary {@code .eml} file on the server
 * @param zipEntryName entry name used inside the resulting zip archive
 */
public record StagedOriginalEmail(String archiveId, Path file, String zipEntryName) {}
