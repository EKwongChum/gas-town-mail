# Gas Town Mail（JDK 17 / Spring Boot 多模块工程）

[![License](https://img.shields.io/github/license/EKwongChum/gas-town-mail)](LICENSE)
![JDK](https://img.shields.io/badge/JDK-17-orange)
[![CI](https://github.com/EKwongChum/gas-town-mail/actions/workflows/ci.yml/badge.svg)](.github/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/EKwongChum/gas-town-mail)](CHANGELOG.md)

> 中文文档 | [English README](README.en.md)

邮件归档与清洗平台：SMTP 收信 → S3/MinIO + MongoDB → RocketMQ → Elasticsearch → MCP 检索；
另外支持按 MCP 查询结果通过 HTTP 接口发送、回复与转发邮件。
三个应用基于 Spring Boot 3.4（JDK 17），共享 mail-common 模块。

```bash
docker compose up -d && ./mvnw clean package
```

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [快速开始](docs/getting-started.md) | 依赖、构建运行、测试邮件 |
| [架构与设计](docs/architecture.md) | 模块结构、流程图、id 格式、采集过滤 |
| [journal-archiver](docs/journal-archiver.md) | 配置项、MongoDB 字段、可靠性、重发接口 |
| [mail-cleaner](docs/mail-cleaner.md) | ES 字段、配置、删除与原件下载接口 |
| [mail-mcp-server](docs/mail-mcp-server.md) | MCP 工具与运行方式 |
| [邮件发送](docs/mail-sending.md) | 发信 / 回复 / 转发接口、附件限制与错误码 |
| [运维与可观测性](docs/operations.md) | 健康检查、指标、traceId、注意事项 |
| [开发与测试](docs/development.md) | 测试分布与贡献方式 |

## 邮箱系统接入

当前 Coremail、Exchange 等邮箱系统均支持以 **journal 格式**进行邮件归档，只需把 journal
投递目标指向 journal-archiver 的 SMTP 服务（默认 `2525` 端口，见
[journal-archiver 配置项](docs/journal-archiver.md)）。

**Coremail 系统**

1. 修改 `programs.cf` 配置的 `[deliveragent/transport]` 段，配置虚拟域名指向 journal-archiver 服务；
2. 修改 `mail_journal.cf`，配置需要归档的范围；
3. 重启所有 deliveragent。

**Exchange 邮箱系统**

请在控制台配置 journal 归档日志投递。

代码完全由 DeepSeek V4 生成，非人工编写；基于 [Apache License 2.0](LICENSE) 开源。
