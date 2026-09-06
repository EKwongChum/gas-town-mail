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

package uk.ekwong.mailcleaner.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void providesOpenApiMetadata() {
        OpenAPI api = new OpenApiConfig().mailCleanerOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("mail-cleaner API");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.1.0");
        assertThat(api.getTags()).extracting(tag -> tag.getName()).contains("Mail info management");
    }
}
