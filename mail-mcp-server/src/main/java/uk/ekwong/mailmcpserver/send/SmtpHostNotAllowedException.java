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

import java.util.List;

/**
 * Thrown when a request asks to deliver through an SMTP server that is not covered by {@code
 * app.send.allowed-smtp-hosts} (mapped to HTTP {@code 403}).
 */
public class SmtpHostNotAllowedException extends IllegalArgumentException {

    public SmtpHostNotAllowedException(String host, List<String> allowedHosts) {
        super(
                "SMTP host '"
                        + host
                        + "' is not allowed; permitted hosts are: "
                        + String.join(", ", allowedHosts));
    }
}
