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

package uk.ekwong.mailmcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot application that exposes the archived email metadata (the Elasticsearch {@code
 * mail_info} index) through a standard MCP (Model Context Protocol) server interface.
 */
// scanBasePackages also picks up the shared beans in uk.ekwong.mailcommon
@SpringBootApplication(scanBasePackages = "uk.ekwong")
@ConfigurationPropertiesScan
public class MailMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailMcpServerApplication.class, args);
    }
}
