# mail-cleaner（清洗应用）

[← 返回文档索引](../README.md)

订阅 RocketMQ `mail_meta_topic`，从对象存储读取邮件本体，解析出可检索的元数据并写入
Elasticsearch 的 `mail_info` 索引，同时提供批量删除与邮件原件下载接口。

## 工作流程

```text
RocketMQ mail_meta_topic ──► MailMetaConsumer（DefaultMQPushConsumer）
                                  │ 反序列化 MailMetaMessage（payload + 消息 key）
                                  ▼
                          MailCleaningService
                                  │ 1. 从 S3 读取邮件本体（objectKey = MongoDB id）
                                  │ 2. 解析 MIME：sender/from/to/cc/Message-Id/
                                  │    ReceivedTime/subject/content-type/附件名称
                                  │ 3. 写入 Elasticsearch mail_info（文档 id = objectKey）
                                  ▼
                          成功返回 CONSUME_SUCCESS；
                          失败返回 RECONSUME_LATER（RocketMQ 重投）
```

## Elasticsearch 文档（`mail_info`）

| 字段 | 说明 |
| --- | --- |
| `id` | 归档 id（MongoDB `_id` / S3 对象 key），重复消费同一消息会覆盖同一文档 |
| `sender` / `from` / `to` / `cc` | 地址字段 |
| `messageId` | 原邮件 `Message-Id` |
| `sha256` | 原邮件 `.eml` 的 SHA-256 摘要（小写 hex），来自归档通知；通知未携带该字段时由清洗服务读取对象后自行计算；与对象实际内容不一致时记录 WARN 日志 |
| `receivedTime` | 原邮件 `Date` 头解析出的时间（ISO-8601） |
| `subject` | 主题（RFC 2047 解码） |
| `contentType` | MIME `Content-Type` |
| `attachmentNames` | 所有附件文件名（含嵌套 multipart） |

## 配置（mail-cleaner/src/main/resources/application.yml）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8081` | HTTP/健康检查端口 |
| `spring.elasticsearch.uris` | `http://localhost:9200` | Elasticsearch 地址 |
| `app.rocketmq.name-server` | `127.0.0.1:9876` | RocketMQ NameServer |
| `app.rocketmq.consumer-group` | `mail-cleaner-consumer` | 消费者组 |
| `app.rocketmq.topic` | `mail_meta_topic` | 订阅 topic |
| `app.rocketmq.tag` | `*` | 订阅 tag |
| `app.rocketmq.start-retry-interval-ms` | `30000` | 消费者启动失败后的自动重试间隔 |
| `app.storage.s3.*` | 同归档应用 | 共享的 S3 配置（mail-common） |

应用启动时不依赖 Elasticsearch / RocketMQ 在线（客户端懒连接、消费者启动失败会按固定间隔自动重试，
无需重启）；`/actuator/health` 包含 Elasticsearch 与 RocketMQ 消费者两个健康指示器。
处理失败的消息由 RocketMQ 按消费者组重投，索引缺失时会在下一条消息时自动补建。
`mail_info` 已显式定义映射：地址类字段、`messageId`、`sha256` 与附件名为 keyword（精确匹配），
主题为 text（全文检索），时间为 date。已有索引不会自动补建新字段的显式映射，动态映射仍会
写入该字段；需要严格 keyword 映射时可删除索引后由下一条消息重建。

## HTTP 批量删除接口

`POST /api/mail-info/delete`，请求体为 JSON：

```json
{
  "ids": ["id-1", "id-2", "id-3"]
}
```

按 Elasticsearch 文档 id（即归档 id / MongoDB `_id` / S3 对象 key）批量删除 `mail_info` 中的数据。
空值会被忽略、重复 id 会去重；空列表或超过 1000 个 id 时返回 `400`。响应示例：

```json
{
  "requested": 3,
  "deleted": 2,
  "notFoundIds": ["id-3"]
}
```

`deleted` 为实际删除的文档数，`notFoundIds` 为索引中不存在的 id（不计入 `deleted`）。

调用示例：

```bash
curl -X POST http://localhost:8081/api/mail-info/delete \
  -H "Content-Type: application/json" \
  -d '{"ids":["id-1","id-2"]}'
```

## HTTP 原件下载接口

`GET /api/mail-info/original?id=<archive-id>`，按归档 id（即 MongoDB `_id` / S3 对象 key / ES
文档 id）返回 S3 中的邮件原始字节，响应 `Content-Type: message/rfc822`，并通过
`Content-Disposition: attachment` 以 `.eml` 文件名触发下载；对象不存在时返回 `404`。

> 归档 id 使用标准 Base64，可能包含 `+`、`/`、`=` 等保留字符，拼接 URL 时请对 id 做
> URL 编码（例如 `+` 应编码为 `%2B`）。

调用示例：

```bash
curl -OJ "http://localhost:8081/api/mail-info/original?id=YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4="
```
