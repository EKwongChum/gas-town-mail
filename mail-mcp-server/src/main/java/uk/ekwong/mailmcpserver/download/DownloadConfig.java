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

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** Creates the temporary file store used by the batch original-email download endpoint. */
@Configuration
public class DownloadConfig {

    @Bean(destroyMethod = "close")
    public TemporaryEmailFileStore temporaryEmailFileStore(DownloadProperties properties)
            throws IOException {
        Path directory = resolveTempDirectory(properties);
        return new TemporaryEmailFileStore(directory, properties.getFileTtl());
    }

    private Path resolveTempDirectory(DownloadProperties properties) {
        if (StringUtils.hasText(properties.getTempDirectory())) {
            return Path.of(properties.getTempDirectory());
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "mail-mcp-server", "originals");
    }
}
