# mail-mcp-server 接口文档

基于 **JDK 17 / Spring Boot 3.4** 的 Spring MCP（Model Context Protocol）服务，
使用 Spring MCP 系列依赖 `io.modelcontextprotocol.sdk:mcp-spring-webmvc` 提供
**标准 MCP Streamable HTTP 接口**，供 LLM / MCP 客户端查询 Elasticsearch
`mail_info` 索引中的归档邮件元数据；同时提供普通 HTTP 接口，将多封邮件原件
（`.eml`）从对象存储取出后打包为 `.zip` 下载。

## 一、服务端点

| 端点 | 方法 | 说明 |
| --- | --- | --- |
| `/mcp` | POST | MCP Streamable HTTP 协议端点（标准 MCP 服务器接口） |
| `/api/mail-originals/download` | POST | 按归档 id 批量下载邮件原件，返回 `.zip` |
| `/v3/api-docs` | GET | OpenAPI JSON 文档 |
| `/swagger-ui.html` | GET | Swagger UI 可视化文档 |
| `/actuator/health` | GET | 健康检查 |

默认端口：`8082`（`server.port` 可配置）。

## 二、MCP 工具（Tools）

服务启动后自动向 MCP 客户端注册以下工具：

### 1. `search_mails` — 分页搜索归档邮件

所有过滤条件可选，条件之间为 AND 关系；`keyword` 会同时匹配
subject / sender / from / to / messageId（至少命中一个）。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 全文关键词（subject / sender / from / to / messageId） |
| `sender` | string | 否 | 精确匹配 sender |
| `from` | string | 否 | 精确匹配 from |
| `to` | string | 否 | 精确匹配 to |
| `cc` | string | 否 | 精确匹配 cc |
| `messageId` | string | 否 | 精确匹配 Message-Id |
| `receivedTimeGe` | string | 否 | 收件时间 >=，ISO-8601，如 `2026-08-23T08:00:00Z` |
| `receivedTimeLt` | string | 否 | 收件时间 <，ISO-8601 |
| `page` | integer | 否 | 页码，从 0 开始，默认 0 |
| `size` | integer | 否 | 每页条数，默认 20，最大 100 |

返回：JSON 对象，包含 `total`（命中总数）和 `documents`（当前页文档数组）。
文档字段：`id`、`sender`、`from`、`to`、`cc`、`messageId`、`receivedTime`、
`subject`、`contentType`、`attachmentNames`。

### 2. `get_mail_by_id` — 按归档 id 查询单封邮件

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 归档 id（MongoDB `_id` / 对象存储 key） |

返回：该文档的 JSON；不存在时返回 `isError=true` 的错误文本。

### 3. `count_mails` — 统计符合条件的邮件数量

参数与 `search_mails` 相同（忽略分页参数）。

返回：JSON 对象 `{"count": N}`。

## 三、MCP 客户端接入

### 通用配置（Claude Desktop / Cursor / 其他 MCP 客户端）

```json
{
  "mcpServers": {
    "mail-mcp-server": {
      "type": "http",
      "url": "http://localhost:8082/mcp"
    }
  }
}
```

### 用 curl 手工调用（JSON-RPC over Streamable HTTP）

初始化会话：

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-03-26",
      "capabilities": {},
      "clientInfo": {"name": "curl", "version": "1.0"}
    }
  }'
```

列出工具：

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'
```

调用 `search_mails`：

```bash
curl -X POST http://localhost:8082/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "search_mails",
      "arguments": {
        "keyword": "report",
        "receivedTimeGe": "2026-08-01T00:00:00Z",
        "page": 0,
        "size": 10
      }
    }
  }'
```

> 提示：Streamable HTTP 服务端返回 `Mcp-Session-Id` 响应头，后续同会话请求需要带上该头
> （`-H 'Mcp-Session-Id: <id>'`）。生产环境建议在网关层为该端点配置鉴权。

## 四、HTTP 批量下载接口（.eml → .zip）

`POST /api/mail-originals/download`，请求体为 JSON：

```json
{
  "ids": ["id-1", "id-2", "id-3"]
}
```

流程：

1. 按归档 id（MongoDB `_id` / S3 对象 key）逐个从对象存储读取邮件原件；
2. 写入服务器临时目录（默认 `<java.io.tmpdir>/mail-mcp-server/originals`），
   每个文件从写入起单独计时，最多保留 30 分钟（可配置）后自动删除；
3. 将所有 `.eml` 流式压缩为 `mail-originals.zip` 返回（`Content-Type: application/zip`）。

规则与错误：

- 空白 id 会被忽略、重复 id 会去重；请求为空或超过 1000 个 id 返回 `400`；
- 任一 id 在对象存储中不存在时返回 `404`，错误信息中列出缺失的 id；
- 请求体不是合法 JSON 时返回 `400`。

调用示例：

```bash
curl -OJ -X POST http://localhost:8082/api/mail-originals/download \
  -H 'Content-Type: application/json' \
  -d '{"ids":["id-1","id-2"]}'
```

## 五、构建与运行

```bash
# 在项目根目录构建（会同时构建 mail-common）
mvn clean package

# 启动（默认端口 8082，ES 默认 localhost:9200）
java -jar mail-mcp-server/target/mail-mcp-server-1.0.0.jar
```

### Docker（docker compose）

仓库根目录的 `docker-compose.yml` 已包含 `mail-mcp-server` 服务（多阶段
`mail-mcp-server/Dockerfile`，JDK 17 构建与运行）。三个应用位于 `app` profile 下，
启动时需带上 `--profile app`；容器内 Elasticsearch 地址自动指向 compose 的
`elasticsearch` 服务：

```bash
docker compose --profile app up -d --build mail-mcp-server
```

> 默认的 `docker compose up -d` 只启动基础设施（MongoDB / MinIO / RocketMQ / Elasticsearch）。

若只想构建镜像：

```bash
docker build -f mail-mcp-server/Dockerfile -t mail-mcp-server .
```

配置项（`application.yml`）：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8082` | HTTP 端口 |
| `spring.elasticsearch.uris` | `http://localhost:9200` | Elasticsearch 地址 |
| `app.mcp.endpoint` | `/mcp` | MCP 协议端点路径 |
| `app.download.temp-dir` | JVM 临时目录下的 `mail-mcp-server/originals` | 暂存 `.eml` 的目录（compose 中可用 `MAIL_DOWNLOAD_TEMP_DIR` 覆盖） |
| `app.download.file-ttl` | `30m` | 单个临时文件保留时长，到期自动删除（可用 `MAIL_DOWNLOAD_FILE_TTL` 覆盖） |
| `app.storage.s3.*` | 同归档应用 | 读取邮件原件所需的共享 S3 配置（mail-common） |

## 六、依赖说明

- `io.modelcontextprotocol.sdk:mcp-spring-webmvc`：Spring MCP WebMVC 传输实现
  （Streamable HTTP / SSE），版本统一在根 POM `dependencyManagement` 管理；
- `spring-boot-starter-data-elasticsearch`：读取 `mail_info` 索引；
- `mail-common`：共享的 S3 配置与 `ObjectStorageService`（读取邮件原件）；
- `springdoc-openapi-starter-webmvc-ui`：生成 OpenAPI / Swagger 文档。

> 版本兼容性：Spring MCP SDK 要求 Spring Framework 6.2+，因此本项目父 POM 的
> Spring Boot 已从 3.3.5 升级到 3.4.13（JDK 17 不变）。
