# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0] - 2026-09-18

### Added

- SHA-256 digest of the original `.eml` bytes: the archiver computes it per received mail, stores
  it in MongoDB (`sha256`) and publishes it in the `mail_meta_topic` payload; the cleaner indexes
  it in Elasticsearch (`mail_info.sha256`), and the MCP query tools return it.
- The Chinese and English READMEs now document how to enable journal archiving on Coremail
  (`programs.cf` / `mail_journal.cf`) and Exchange (admin console journal delivery).

### Changed

- **Breaking**: archive id uses only the sender mailbox: `Alice <alice@example.com>` now encodes as
  `YWxpY2VAZXhhbXBsZS5jb20=`. Documents archived with the previous full-sender id keep their old
  `_id`; there is no automatic migration.
- The 646-line Chinese `README.md` became a short index; the details moved to `docs/`
  (`architecture`, `getting-started`, one page per application, `operations`, `development`).

## Released in v0.1.0 – v1.2.0

> These releases were tagged without splitting the changelog per version, so their entries are
> collected here instead of under individual version headings.

### Added

- Community and governance files: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`,
  `CHANGELOG.md`, issue/PR templates, and `CODEOWNERS`.
- Dependabot configuration for Maven, GitHub Actions, and Docker.
- English top-level documentation (`README.en.md`).
- Release workflow that publishes jars, a CycloneDX SBOM, and GHCR images for version tags.
- Code quality tooling: Spotless (AOSP format) and JaCoCo coverage reports.
- CodeQL and dependency-review workflows.

### Changed

- CI now uploads JaCoCo coverage reports and runs the Spotless format check.
- Docker images run as a non-root user, expose health checks, and pin the MinIO image version.
- S3 access key / secret key no longer have code-level defaults; local development values are only
  applied from `application.yml` / environment variables.
