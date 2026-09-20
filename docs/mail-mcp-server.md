# mail-mcp-server（MCP 查询应用）

[← 返回文档索引](../README.md)

基于 **JDK 17 / Spring Boot 3.4** 的 Spring MCP（Model Context Protocol）服务，
使用 Spring MCP 系列依赖 `io.modelcontextprotocol.sdk:mcp-spring-webmvc` 提供
标准 MCP Streamable HTTP 接口（默认 `POST /mcp`，端口 8082），
让 LLM / MCP 客户端直接查询 Elasticsearch `mail_info` 索引中的归档邮件元数据；
同时提供邮件原件批量下载与发信 / 回复 / 转发 HTTP 接口。

> 设置 `app.security.api-key` 后，`/mcp` 与 `/api/mails/**` 需要携带 `X-API-Key` 或
> `Authorization: Bearer`，详见[邮件发送](mail-sending.md)的鉴权章节。

## 工作流程

```text
MCP 客户端（Claude Desktop / Cursor / 任意 MCP SDK）
        │ 标准 MCP 协议（Streamable HTTP，/mcp）
        ▼
    McpSyncServer（mcp-spring-webmvc）
        │ search_mails / get_mail_by_id / count_mails
        ▼
    EmailQueryService
        │ bool query（term 精确过滤 + keyword 全文匹配 + 时间范围）
        ▼
    Elasticsearch mail_info
```

## 暴露的 MCP 工具

| 工具 | 说明 |
| --- | --- |
| `search_mails` | 分页搜索：keyword（subject/sender/from/to/messageId）、精确地址、Message-Id、时间范围；返回的每封邮件都带 `sha256` |
| `get_mail_by_id` | 按归档 id（MongoDB `_id` / S3 key）查询单封邮件，返回字段含 `sha256` |
| `count_mails` | 统计符合条件的邮件数量 |
| `send_mail` | 通过工具参数中的 SMTP 服务器发送一封邮件（支持 Base64 附件，单个 ≤10 MB、合计 ≤20 MB） |
| `reply_mail` | 回复归档邮件：参数 = `id` + 发送参数，另可选 `replyAll` / `includeOriginalBody` / `includeOriginalAttachments` |
| `forward_mail` | 转发归档邮件：参数 = `id` + 发送参数（`to` 必填），默认携带原邮件附件 |

发送类工具与 HTTP 接口一一对应（除 multipart 分片外参数完全相同），语义见
[邮件发送](mail-sending.md)：`reply_mail` 默认发给原邮件 `Reply-To`/`From`、主题加 `Re:` 前缀、
引用原文并设置 `In-Reply-To`/`References`；`forward_mail` 主题加 `Fwd:` 前缀、嵌入转发块并
默认携带原附件。

> 与 HTTP 接口一样，SMTP 服务器与账号密码由每次调用传入，服务端不保存；工具描述里已明确提示
> 这是**真实投递**（`annotations.readOnlyHint=false`、`openWorldHint=true`），并提示模型不要
> 回显密码。如果希望服务端统一配置发信账号（调用方不必传密码），需要在应用侧增加默认 SMTP
> 配置。

## HTTP 接口

| 接口 | 说明 |
| --- | --- |
| `POST /api/mail-originals/download` | 按归档 id 批量下载邮件原件，打包为 `.zip` |
| `POST /api/mails/send` | 用请求中提供的 SMTP 服务器与账号发送邮件（支持附件） |
| `POST /api/mails/reply` | 回复归档邮件（发送参数 + MCP 查询返回的 `id`） |
| `POST /api/mails/forward` | 转发归档邮件（发送参数 + MCP 查询返回的 `id`） |

发送类接口的完整字段、附件限制（单个 10 MB / 合计 20 MB）、回复与转发语义、错误码与示例见
[邮件发送](mail-sending.md)。

## 接口文档

- MCP 工具参数、客户端配置与 JSON-RPC 调用示例：[mail-mcp-server/README.md](../mail-mcp-server/README.md)；
- 运行时 OpenAPI：`GET /v3/api-docs`，Swagger UI：`GET /swagger-ui.html`（文档化 `/mcp` 端点）。

## 运行

```bash
# 方式一：本地 java -jar（需先 mvn clean package；读取原件需要配置 S3）
java -jar mail-mcp-server/target/mail-mcp-server-2.1.0.jar

# 方式二：docker compose（与基础设施一起，自动连接容器内 Elasticsearch / MinIO）
docker compose --profile app up -d --build mail-mcp-server
```

> MCP 查询只依赖 Elasticsearch；批量 zip 下载还会从 MinIO（S3）读取邮件原件，
> 因此 compose 中该服务已增加对 MinIO 的依赖并注入 `APP_STORAGE_S3_ENDPOINT`。

> 版本说明：Spring MCP SDK 要求 Spring Framework 6.2+，因此父 POM 的 Spring Boot
> 从 3.3.5 升级到 3.4.13（JDK 17 不变，三个应用版本统一）。
