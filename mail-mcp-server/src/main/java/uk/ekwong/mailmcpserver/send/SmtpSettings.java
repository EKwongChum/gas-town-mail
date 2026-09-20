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

package uk.ekwong.mailmcpserver.send;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * SMTP server coordinates supplied with a single send request. Credentials are never stored or
 * logged; a blank {@code username} means the server is used without authentication.
 */
public record SmtpSettings(
        String host, int port, String username, String password, SmtpEncryption encryption) {

    public SmtpSettings {
        encryption = encryption == null ? SmtpEncryption.AUTO : encryption;
    }

    public boolean authenticated() {
        return StringUtils.hasText(username);
    }

    /** Only for logging: never exposes the password. */
    @Override
    public String toString() {
        return host + ":" + port + " (" + encryption.name().toLowerCase(Locale.ROOT) + ")";
    }
}
