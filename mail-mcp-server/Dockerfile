# Copyright 2026 ekwongchum
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Multi-stage build for mail-mcp-server.
#
# Build context must be the repository root (so the Maven reactor sees the
# parent pom and mail-common), e.g.:
#   docker build -f mail-mcp-server/Dockerfile -t mail-mcp-server .

ARG VERSION=1.1.0
ARG REVISION=unknown

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl mail-mcp-server -am package -DskipTests \
    && cp mail-mcp-server/target/mail-mcp-server-*.jar /workspace/app.jar

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
ARG VERSION
ARG REVISION
LABEL org.opencontainers.image.title="mail-mcp-server" \
      org.opencontainers.image.description="Spring MCP server exposing Elasticsearch mail queries and original-email zip downloads" \
      org.opencontainers.image.source="https://github.com/EKwongChum/gas-town-mail" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/data \
    && chown -R 10001:10001 /app
USER 10001:10001
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
