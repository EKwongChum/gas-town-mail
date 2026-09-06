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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the batch original-email download endpoint.
 *
 * <p>{@code tempDirectory} defaults to a sub-directory of the JVM temporary directory when left
 * blank; {@code fileTtl} controls how long a staged {@code .eml} file may stay on disk before it is
 * removed by its own cleanup timer.
 */
@ConfigurationProperties(prefix = "app.download")
public class DownloadProperties {

    private String tempDirectory = "";
    private Duration fileTtl = Duration.ofMinutes(30);

    public String getTempDirectory() {
        return tempDirectory;
    }

    public void setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public Duration getFileTtl() {
        return fileTtl;
    }

    public void setFileTtl(Duration fileTtl) {
        this.fileTtl = fileTtl;
    }
}
