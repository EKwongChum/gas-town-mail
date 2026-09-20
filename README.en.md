# Gas Town Mail

[![License](https://img.shields.io/github/license/EKwongChum/gas-town-mail)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17-orange)]()
[![CI](https://github.com/EKwongChum/gas-town-mail/actions/workflows/ci.yml/badge.svg)]()
[![Release](https://img.shields.io/github/v/release/EKwongChum/gas-town-mail)]()


Gas Town Mail is a journal email archiving and search platform built with **JDK 17 / Spring Boot
3.4**. It is organized as one Maven reactor with three runnable applications and a shared module:

| Module | Role |
| --- | --- |
| `journal-archiver` | Embedded SMTP server that receives Exchange journal reports, stores metadata in MongoDB, stores the raw email in S3-compatible object storage, then publishes a notification to RocketMQ |
| `mail-cleaner` | RocketMQ consumer that reads each email from object storage, extracts searchable metadata, and indexes it into Elasticsearch (`mail_info`) |
| `mail-mcp-server` | Spring MCP server (Streamable HTTP on `/mcp`) exposing Elasticsearch queries as MCP tools, plus HTTP endpoints that download selected original `.eml` files as a `.zip` archive and send, reply to or forward mail through a caller-supplied SMTP server |
| `mail-common` | Shared parsing, storage, and configuration code |

All dependency versions are managed once in the root [pom.xml](pom.xml).

## Architecture

```text
SMTP mail ──► journal-archiver ──► S3 (raw bytes)
                  │                 MongoDB (metadata)
                  └──► RocketMQ ──► mail-cleaner ──► Elasticsearch ──► mail-mcp-server ──► MCP clients
```

Archive identifiers are `Base64(sender address)_Base64(Message-Id)` and are shared by the MongoDB
document `_id`, the S3 object key, the RocketMQ message key, and the Elasticsearch document id.

The full configuration reference, workflow diagrams, and reliability notes live in the Chinese
[documentation index](README.md).

## Journal archiving in mail systems

Coremail, Exchange and other mail systems support archiving mail in **journal format**. Point the
journal delivery target at the journal-archiver SMTP service (port `2525` by default, see the
[journal-archiver configuration](docs/journal-archiver.md)).

**Coremail**

1. Edit the `[deliveragent/transport]` section of `programs.cf` and configure a virtual domain that
   points to the journal-archiver service.
2. Edit `mail_journal.cf` to configure the scope of the mail to archive.
3. Restart all deliveragents.

**Exchange**

Configure journal log delivery in the Exchange admin console.

## Quick start

Requirements: JDK 17 and Docker with Compose.

```bash
# 1. Start only the infrastructure (MongoDB / MinIO / RocketMQ / Elasticsearch)
docker compose up -d

# 2. Or start everything (infrastructure + the three applications)
docker compose --profile app up -d --build
```

Ports: SMTP `2525`, journal-archiver HTTP `8080`, mail-cleaner HTTP `8081`, mail-mcp-server HTTP
`8082`. Health checks are available on `/actuator/health` for each application.

To build and test locally:

```bash
./mvnw -B clean verify
```

The project ships with 142 unit tests across all modules.

Observability: HTTP `X-Request-Id` values are logged under the `traceId` MDC key, the archiver
propagates the same key to mail-cleaner as a RocketMQ message user property, and metrics are exposed
at `/actuator/prometheus`. Set `SPRING_PROFILES_ACTIVE=json` for Logstash-style JSON logs (already
enabled in the provided docker compose app profile).

### Configuration

Local development values live in each module's `application.yml`. All secrets should be provided via
environment variables:

| Variable | Purpose | Default (development only) |
| --- | --- | --- |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | S3/MinIO credentials | `minioadmin` / `minioadmin` |
| `APP_STORAGE_S3_ENDPOINT` | S3-compatible endpoint (archiver, cleaner and mcp download) | `http://localhost:9000` |
| `APP_NOTIFY_ROCKETMQ_NAMESERVER` | RocketMQ name server (archiver) | `127.0.0.1:9876` |
| `APP_ROCKETMQ_NAMESERVER` | RocketMQ name server (cleaner) | `127.0.0.1:9876` |
| `SPRING_DATA_MONGODB_URI` | MongoDB connection string | local default |
| `SPRING_ELASTICSEARCH_URIS` | Elasticsearch endpoint | `http://localhost:9200` |

## MCP usage

`mail-mcp-server` exposes standard MCP tools over Streamable HTTP:

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'
```

Tools: `search_mails`, `get_mail_by_id`, `count_mails`. Full tool parameters and client examples are
in [mail-mcp-server/README.md](mail-mcp-server/README.md).

`mail-mcp-server` also serves `POST /api/mail-originals/download` (`{"ids": ["id-1", "id-2"]}`),
which reads the requested original emails from S3 into a temporary directory (each file is cleaned
up 30 minutes later by default) and returns them as a `.zip` archive.

## Documentation

The Chinese documentation is split into topic-specific files, indexed by [README.md](README.md):

- Getting started: [docs/getting-started.md](docs/getting-started.md)
- Architecture and design: [docs/architecture.md](docs/architecture.md)
- Archiver: [docs/journal-archiver.md](docs/journal-archiver.md)
- Cleaner: [docs/mail-cleaner.md](docs/mail-cleaner.md)
- MCP server: [docs/mail-mcp-server.md](docs/mail-mcp-server.md)
- Operations and observability: [docs/operations.md](docs/operations.md)
- Development and testing: [docs/development.md](docs/development.md)

Other references:

- MCP server interface: [mail-mcp-server/README.md](mail-mcp-server/README.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security: [SECURITY.md](SECURITY.md)

## License

[Apache License 2.0](LICENSE).
