# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Access control for the outbound mail endpoints: an optional shared API key
  (`app.security.api-key`, sent as `X-API-Key` or `Authorization: Bearer`) on
  `app.security.protected-paths` (default `/api/mails/**` and `/mcp`), an SMTP host allow list
  (`app.send.allowed-smtp-hosts`, `*.` wildcard, rejected with `403`) and a per-client rate limit
  (`app.send.rate-limit.requests-per-minute`, answered with `429` and `Retry-After`).
- Delivery hardening: TLS server identity verification is enabled by default
  (`app.send.verify-server-identity`), credentials are refused on unencrypted connections unless
  `app.send.allow-plaintext-credentials` is switched on, and the envelope sender (SMTP `MAIL FROM`)
  defaults to the authenticated SMTP account instead of the From header.

- Outbound mail endpoints on mail-mcp-server: `POST /api/mails/send` sends a mail through an SMTP
  server supplied in the request (host, port, account, password, subject, body, recipients and
  Base64 attachments, with a 10 MB per-attachment and 20 MB total limit);
  `POST /api/mails/reply` and `POST /api/mails/forward` compose the mail from an archived original
  identified by the archive id returned by the MCP query tools, following the usual mail client
  semantics (reply-to sender, `Re:`/`Fwd:` subject prefixes, quoted or forwarded original body,
  `In-Reply-To`/`References` threading for replies, original attachments carried over on forward).
- `docs/mail-sending.md` documents the three endpoints, the attachment limits, the reply/forward
  rules, the error responses and the `app.send.*` configuration.
- The send / reply / forward endpoints also accept `multipart/form-data`, where the JSON body
  travels in the `request` part and each attachment is uploaded as its own `attachments` file part
  (no Base64 encoding needed). Uploaded files share the 10 MB / 20 MB limits, file names are
  sanitized, and the servlet multipart limits default to 12 MB / 30 MB so that an oversized
  attachment still receives the documented JSON `400` response.
- MCP tools `send_mail`, `reply_mail` and `forward_mail` expose the same outbound mail
  capabilities to MCP clients, with the SMTP server and account supplied per call (never stored).
  They are marked as side-effecting (`readOnlyHint=false`, `openWorldHint=true`), report SMTP and
  validation failures as tool errors and are covered by the shared
  `mail.mcp.tool.calls` / `mail.mcp.tool.duration` metrics.

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
