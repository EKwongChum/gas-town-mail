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
| `mail-mcp-server` | Spring MCP server (Streamable HTTP on `/mcp`) exposing Elasticsearch queries as MCP tools |
| `mail-common` | Shared parsing, storage, and configuration code |

All dependency versions are managed once in the root [pom.xml](pom.xml).

## Architecture

```text
SMTP mail ──► journal-archiver ──► S3 (raw bytes)
                  │                 MongoDB (metadata)
                  └──► RocketMQ ──► mail-cleaner ──► Elasticsearch ──► mail-mcp-server ──► MCP clients
```

Archive identifiers are `Base64(sender)_Base64(Message-Id)` and are shared by the MongoDB document
`_id`, the S3 object key, the RocketMQ message key, and the Elasticsearch document id.

See the Chinese [README](README.md) for the full configuration reference, workflow diagrams, and
reliability notes.

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

The project ships with 106 unit tests across all modules.

### Configuration

Local development values live in each module's `application.yml`. All secrets should be provided via
environment variables:

| Variable | Purpose | Default (development only) |
| --- | --- | --- |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | S3/MinIO credentials | `minioadmin` / `minioadmin` |
| `APP_STORAGE_S3_ENDPOINT` | S3-compatible endpoint | `http://localhost:9000` |
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

## Documentation

- Full Chinese documentation: [README.md](README.md)
- MCP server interface: [mail-mcp-server/README.md](mail-mcp-server/README.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security: [SECURITY.md](SECURITY.md)

## License

[Apache License 2.0](LICENSE).
