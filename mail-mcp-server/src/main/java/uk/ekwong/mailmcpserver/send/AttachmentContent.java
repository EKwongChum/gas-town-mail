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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The bytes of one attachment, opened on demand. A multipart upload is streamed straight from the
 * temporary file of the servlet container ({@code MultipartFile::getInputStream}) and Base64
 * content comes from memory, so an outgoing mail never holds a second copy of a large attachment.
 *
 * <p>{@link #open()} may be called more than once and always returns a fresh stream.
 */
@FunctionalInterface
public interface AttachmentContent {

    InputStream open() throws IOException;

    /** Content that is already in memory. */
    static AttachmentContent ofBytes(byte[] content) {
        byte[] bytes = content == null ? new byte[0] : content;
        return () -> new ByteArrayInputStream(bytes);
    }
}
