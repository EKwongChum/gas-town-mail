# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Community and governance files: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`,
  `CHANGELOG.md`, issue/PR templates, and `CODEOWNERS`.
- Dependabot configuration for Maven, GitHub Actions, and Docker.
- English top-level documentation (`README.en.md`).
- Release workflow that publishes jars, a CycloneDX SBOM, and GHCR images for version tags.
- Code quality tooling: Spotless (AOSP format) and JaCoCo coverage reports.
- CodeQL and dependency-review workflows.

### Changed

- Project version aligned to `0.1.0` for the first experimental release.
- CI now uploads JaCoCo coverage reports and runs the Spotless format check.
- Docker images run as a non-root user, expose health checks, and pin the MinIO image version.
- S3 access key / secret key no longer have code-level defaults; local development values are only
  applied from `application.yml` / environment variables.
