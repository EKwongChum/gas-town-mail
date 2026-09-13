# mail-mcp-server（MCP 查询应用）

[← 返回文档索引](../README.md)

基于 **JDK 17 / Spring Boot 3.4** 的 Spring MCP（Model Context Protocol）服务，
使用 Spring MCP 系列依赖 `io.modelcontextprotocol.sdk:mcp-spring-webmvc` 提供
标准 MCP Streamable HTTP 接口（默认 `POST /mcp`，端口 8082），
让 LLM / MCP 客户端直接查询 Elasticsearch `mail_info` 索引中的归档邮件元数据。

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
| `search_mails` | 分页搜索：keyword（subject/sender/from/to/messageId）、精确地址、Message-Id、时间范围 |
| `get_mail_by_id` | 按归档 id（MongoDB `_id` / S3 key）查询单封邮件 |
| `count_mails` | 统计符合条件的邮件数量 |

## 接口文档

- MCP 工具参数、客户端配置与 JSON-RPC 调用示例：[mail-mcp-server/README.md](../mail-mcp-server/README.md)；
- 运行时 OpenAPI：`GET /v3/api-docs`，Swagger UI：`GET /swagger-ui.html`（文档化 `/mcp` 端点）。

## 运行

```bash
# 方式一：本地 java -jar（需先 mvn clean package；读取原件需要配置 S3）
java -jar mail-mcp-server/target/mail-mcp-server-1.2.0.jar

# 方式二：docker compose（与基础设施一起，自动连接容器内 Elasticsearch / MinIO）
docker compose --profile app up -d --build mail-mcp-server
```

> MCP 查询只依赖 Elasticsearch；批量 zip 下载还会从 MinIO（S3）读取邮件原件，
> 因此 compose 中该服务已增加对 MinIO 的依赖并注入 `APP_STORAGE_S3_ENDPOINT`。

> 版本说明：Spring MCP SDK 要求 Spring Framework 6.2+，因此父 POM 的 Spring Boot
> 从 3.3.5 升级到 3.4.13（JDK 17 不变，三个应用版本统一）。
