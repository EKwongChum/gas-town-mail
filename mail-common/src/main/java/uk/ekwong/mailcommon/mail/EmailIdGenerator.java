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

package uk.ekwong.mailcommon.mail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Generates the archive id: {@code Base64(sender)_Base64(Message-Id)}.
 * The same id is used as the MongoDB document id and the object storage key.
 */
public final class EmailIdGenerator {

    private EmailIdGenerator() {
    }

    public static String generate(String sender, String messageId) {
        return base64(normalize(sender)) + "_" + base64(normalize(messageId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
