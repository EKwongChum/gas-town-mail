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

package uk.ekwong.journalarchiver.spool;

import java.nio.file.Path;
import java.util.List;

/**
 * One accepted mail message claimed from the durable inbox. The backing JSON file stays in the
 * {@code work} directory until processing succeeds, so a crash only delays retry and never loses
 * the accepted bytes.
 */
public record SpooledMessage(
        Path file,
        byte[] raw,
        String envelopeSender,
        List<String> recipients,
        String clientAddress,
        String traceId) {}
