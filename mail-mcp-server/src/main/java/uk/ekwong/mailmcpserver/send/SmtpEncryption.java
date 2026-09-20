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

/**
 * How the SMTP connection is encrypted.
 *
 * <p>{@link #AUTO} is used when the request does not select a mode: implicit TLS is used for port
 * 465, STARTTLS is offered (but not required) on every other port. Requests may explicitly ask for
 * {@code none} / {@code starttls} / {@code ssl}, where an explicit STARTTLS is required.
 */
public enum SmtpEncryption {
    AUTO,
    NONE,
    STARTTLS,
    SSL
}
