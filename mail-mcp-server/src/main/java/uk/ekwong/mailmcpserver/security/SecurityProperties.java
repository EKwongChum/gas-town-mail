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

package uk.ekwong.mailmcpserver.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API key protection for the endpoints that can trigger a real delivery.
 *
 * <p>The key is optional: while {@code api-key} is blank the endpoints keep working without
 * authentication (local development) and a warning is logged at startup. As soon as it is set,
 * every request to one of {@code protected-paths} has to present it.
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** Shared secret required on the protected paths; blank disables the check. */
    private String apiKey = "";

    /** Header the key is read from; {@code Authorization: Bearer <key>} is accepted as well. */
    private String headerName = "X-API-Key";

    /** Ant patterns of the paths that require the key. */
    private List<String> protectedPaths = List.of("/api/mails/**", "/mcp");

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }
}
