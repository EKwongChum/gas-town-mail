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

# Multi-stage build for journal-archiver.
#
# Build context must be the repository root (so the Maven reactor sees the
# parent pom and mail-common), e.g.:
#   docker build -f journal-archiver/Dockerfile -t journal-archiver .

# Release workflow passes VERSION (tag without "v") and REVISION (commit sha).
ARG VERSION=0.1.0
ARG REVISION=unknown

# ---- build stage ----
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl journal-archiver -am package -DskipTests \
    && cp journal-archiver/target/journal-archiver-*.jar /workspace/app.jar

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
ARG VERSION
ARG REVISION
LABEL org.opencontainers.image.title="journal-archiver" \
      org.opencontainers.image.description="SMTP ingest + MongoDB/S3 archive + RocketMQ notifications" \
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
EXPOSE 8080 2525
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
