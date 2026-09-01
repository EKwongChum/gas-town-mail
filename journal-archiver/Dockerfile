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

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl journal-archiver -am package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/journal-archiver/target/journal-archiver-1.0.0.jar app.jar
EXPOSE 8080 2525
ENTRYPOINT ["java", "-jar", "app.jar"]
