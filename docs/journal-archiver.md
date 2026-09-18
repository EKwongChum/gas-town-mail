# journal-archiver（归档应用）

[← 返回文档索引](../README.md)

内嵌 SMTP 服务接收来自任何来源的邮件，识别 Exchange journal report 后把原邮件基础信息写入
MongoDB、原始字节写入 S3，两者成功后向 RocketMQ 发送通知，并提供按 `_id` 或时间范围重发通知的
HTTP 接口。

## 工作流程

```text
任意来源 --SMTP--> 内嵌 SMTP 服务 (subethasmtp)
                      │ 捕获 MAIL FROM / RCPT TO / DATA 原始字节
                      ▼
              JournalProcessingService（异步线程池）
                      │ 1. 解析 MIME
                      │ 2. 判断是否 journal 格式
                      │ 3. 提取原邮件（优先取 message/rfc822 内嵌部分）
                      │ 4. 提取基础信息并生成 id
                      ▼
        ┌─────────────┬─────────────┐
        ▼             ▼             ▼
      S3:         MongoDB:      RocketMQ:
  journal-     journal_emails  mail_meta_topic
  emails/<id>   { _id: ... }    (两者成功后通知)
        │             │
        └─────┬───────┘
        全部成功后才发送通知；任一步失败会重试，
        最终失败则写入本地死信目录
```

journal 格式识别、“原邮件”提取与采集过滤的规则见
[架构与设计](architecture.md#关键设计说明)。

## 配置（application.yml）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `app.smtp.bind-address` | `0.0.0.0` | 监听地址，`0.0.0.0` 表示所有网卡 |
| `app.smtp.port` | `2525` | SMTP 监听端口 |
| `app.smtp.hostname` | `journal-archiver.local` | EHLO 应答中的主机名 |
| `app.smtp.max-message-size` | `20971520` | 单封邮件大小上限（字节） |
| `app.smtp.max-connections` / `max-recipients` / `connection-timeout-seconds` | 200 / 100 / 60 | 连接与收件人限制 |
| `app.smtp.require-tls` | `false` | 是否强制 TLS |
| `app.smtp.disable-received-headers` | `true` | 为 `true` 时按原样保存收到的字节（不注入 `Received:` 头） |
| `app.journal.detect-by-header` | `true` | 通过 `X-MS-Journal-Report` / `X-MS-Exchange-Organization-Journal-Report` 识别 journal |
| `app.journal.detect-by-rfc822-attachment` | `true` | 通过内嵌 `message/rfc822` 附件识别 journal |
| `app.journal.extract-original-attachment` | `true` | 归档内嵌的原邮件，而不是 journal 外壳 |
| `app.journal.sender-resolution` | `header` | `sender` 字段取值：`header`（Sender 头，缺省回退 From）、`envelope`（SMTP MAIL FROM）、`from`（始终用 From） |
| `app.journal.generate-message-id-if-missing` | `true` | 原邮件缺少 Message-Id 时生成一个；为 `false` 时不生成 Message-Id，归档 id 改用正文内容摘要，避免同 sender 的无 Message-Id 邮件互相覆盖 |
| `app.journal.filter.sender-emails` | `[]`（空） | 只采集 sender 命中列表中任一邮箱的邮件；空列表表示不过滤 sender |
| `app.journal.filter.from-emails` | `[]`（空） | 只采集 from 中包含列表中任一邮箱的邮件；空列表表示不过滤 from |
| `app.journal.filter.to-emails` | `[]`（空） | 只采集 to 中包含列表中任一邮箱的邮件；空列表表示不过滤 to |
| `app.journal.filter.cc-emails` | `[]`（空） | 只采集 cc 中包含列表中任一邮箱的邮件；空列表表示不过滤 cc |
| `app.processing.core/max/queue` | 2 / 8 / 500 | 归档处理的线程池参数 |
| `app.processing.retry-max-attempts` | `3` | 归档失败时的最大尝试次数 |
| `app.processing.retry-backoff-ms` | `1000` | 重试间隔（按尝试次数递增） |
| `app.processing.dead-letter-dir` | `./data/dead-letter` | 最终失败邮件的本地落盘目录 |
| `app.processing.spool-dir` | `./data/spool` | SMTP 落盘 spool 根目录（inbox/work/quarantine） |
| `app.processing.spool-poll-interval-ms` | `1000` | spool 后台消费轮询间隔 |
| `app.processing.spool-retry-delay-ms` | `30000` | spool 处理失败后的重试延迟 |
| `app.processing.spool-max-batch` | `16` | 单次轮询最多领取的 spool 文件数 |
| `app.resend.max-ids` | `1000` | 重发接口单次允许的最大 ids 数量 |
| `app.resend.parallelism` / `queue-capacity` | 4 / 1000 | 重发任务的线程池参数 |
| `app.notify.rocketmq.enabled` | `true` | 是否发送归档完成通知 |
| `app.notify.rocketmq.name-server` | `127.0.0.1:9876` | RocketMQ NameServer 地址 |
| `app.notify.rocketmq.producer-group` | `journal-archiver-producer` | RocketMQ 生产者组 |
| `app.notify.rocketmq.topic` | `mail_meta_topic` | 通知 topic |
| `app.notify.rocketmq.tag` | `mail-meta` | 通知 tag（置空则只发 topic） |
| `app.notify.rocketmq.send-timeout-ms` | `3000` | 同步发送超时 |
| `app.notify.rocketmq.retry-times-when-send-failed` | `2` | 发送失败时 RocketMQ 客户端内置重试次数 |
| `app.notify.outbox.scan-interval-ms` | `30000` | Mongo outbox 扫描间隔 |
| `app.notify.outbox.grace-ms` | `60000` | 新记录在立即发布完成前的宽限期 |
| `app.notify.outbox.retry-backoff-ms` | `30000` | outbox 重试退避 |
| `app.notify.outbox.max-attempts` | `10` | outbox 最大自动重试次数（之后标记 FAILED） |
| `server.port` | `8080` | HTTP 接口端口 |
| `spring.data.mongodb.uri` | `mongodb://.../journal_archiver?serverSelectionTimeoutMS=10000&connectTimeoutMS=5000` | MongoDB 连接串（含超时配置） |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus,loggers` | 健康检查、应用信息与监控指标端点（供 Prometheus 抓取） |
| `app.storage.s3.endpoint` / `region` / `access-key` / `secret-key` / `bucket` / `path-style` | MinIO 本地默认值 | S3 兼容对象存储配置 |
| `app.storage.s3.create-bucket-if-missing` | `true` | 启动时自动创建 bucket |

## MongoDB 文档字段

除基础信息（`sender`/`from`/`to`/`cc`/`subject`/`messageId`/`envelopeSender` 等）外，
每次保存都会写入原邮件摘要与审计字段：

| 字段 | 说明 |
| --- | --- |
| `sha256` | 原邮件 `.eml` 字节（即 S3 中保存的内容）的 SHA-256 摘要，小写十六进制；内容相同则摘要相同，可用于校验与去重 |
| `createdAt` | 数据创建时间（时间戳），首次归档时写入，重复归档同一 id 时保持不变 |
| `updatedAt` | 数据最后修改时间（时间戳），每次保存更新 |
| `modificationCount` | 数据修改次数，首次归档为 `1`，同一 id 再次归档时 +1（Mongo `$inc` 原子自增，无并发计数丢失） |
| `notificationStatus` | 通知 outbox 状态：`PENDING` / `SENT` / `FAILED` |
| `notificationAttemptCount` / `notificationAttemptAt` | outbox 已尝试次数与最近一次尝试时间 |
| `notifiedAt` | 最近一次成功发送通知的时间 |

## 可靠性设计

- **收信先落盘再 ACK**：SMTP 收到完整邮件后先 fsync 写入 `spool-dir`，成功后才回 `250`；
  落盘失败回 `451`，让发送方稍后重试。后台 inbox relay 从 spool 消费，进程崩溃/重启后自动恢复未处理文件。
- **通知 outbox**：MongoDB 文档写入时状态为 `PENDING`，立即发布成功更新为 `SENT`；
  outbox 后台扫描自动重发 PENDING 记录，超过 `max-attempts` 后标记 `FAILED`（可走重发接口）。
- **写入顺序**：S3（幂等，同 key 覆盖）→ MongoDB（原子 upsert）→ RocketMQ 通知。
- **Mongo 结果不确定不删对象**：S3 写入成功但 MongoDB 写入失败/超时时，不再尝试删除 S3（删除可能毁掉唯一原件）；
  对象与 spool 都被保留，由 inbox relay 自动重试，直到 Mongo 元数据写入成功。
- **重试**：整个归档流程最多重试 `retry-max-attempts` 次（默认 3），间隔按尝试次数递增。
- **死信**：重试仍失败时，原始邮件字节和接收上下文（信封发件人、收件人、客户端地址、错误）写入
  `app.processing.dead-letter-dir`（默认 `./data/dead-letter/`，`*.eml` + `*.json`），可手工重放。
- **背压**：归档线程池队列满时使用 `CallerRunsPolicy`（在 SMTP 线程内同步处理），不会粗暴断开连接；
  邮件超过大小上限时返回 SMTP `552`，让发送方知道是被拒绝而不是断连。
- **持久化**：compose 中 journal-archiver 的 `/app/data`（spool + 死信）挂载到命名卷。

## 健康检查

`GET /actuator/health`：包含 MongoDB 健康（默认）和自定义的 SMTP 健康指示器
（[SmtpHealthIndicator.java](../journal-archiver/src/main/java/uk/ekwong/journalarchiver/actuator/SmtpHealthIndicator.java)）。
MongoDB 不可用时整体状态为 `DOWN`（HTTP 503），便于探活与告警。

## HTTP 重发接口

`POST /api/journal-emails/resend`，请求体为 JSON：

```json
{
  "timeGe": "2026-08-16T00:00:00Z",
  "timeLt": "2026-08-17T00:00:00Z",
  "ids": ["id-1", "id-2"]
}
```

参数语义：

- `timeGe`（可选）：数据创建时间 **大于等于** 该值（`createdAt >= timeGe`）；
- `timeLt`（可选）：数据创建时间 **小于** 该值（`createdAt < timeLt`）；
- `ids`（可选）：待重发的 MongoDB `_id` 数组，**优先于**时间参数（提供了 `ids` 就直接用它们，不再按时间查询）。

`ids` 与时间参数都不传时返回 `400`。响应示例：

```json
{
  "total": 2,
  "succeeded": 2,
  "failed": 0,
  "ids": ["id-1", "id-2"],
  "notFoundIds": []
}
```

`ids` 中不存在的文档会进入 `notFoundIds`（同时计入 `failed`），便于调用方区分“发送失败”和“文档不存在”。

调用示例：

```bash
# 按指定 ids 重发
curl -X POST http://localhost:8080/api/journal-emails/resend \
  -H "Content-Type: application/json" \
  -d '{"ids":["id-1","id-2"]}'

# 按创建时间范围重发（左闭右开：[timeGe, timeLt)）
curl -X POST http://localhost:8080/api/journal-emails/resend \
  -H "Content-Type: application/json" \
  -d '{"timeGe":"2026-08-16T00:00:00Z","timeLt":"2026-08-17T00:00:00Z"}'
```

重发时从 MongoDB 读取对应文档，重新生成 `mail_meta_topic` 消息（消息 key 仍为该文档的 `_id`）。
不存在的 id 或发送失败计入 `failed`。

## RocketMQ 通知

归档流水线严格按以下顺序执行：

1. `objectStorageService.store(id, rawEmail)` 写入 S3（key = 同一个 id，
   `Content-Type: message/rfc822`）；
2. `mongoTemplate.upsert(...)` 写入 MongoDB（`_id` = `Base64(sender 邮箱地址)_Base64(Message-Id)`，
   并保存 `objectKey` = id 与 `sha256` = 原邮件字节摘要）；
3. 两者都成功后，`RocketMailMetaPublisher` 向 `mail_meta_topic:mail-meta` 同步发送 JSON 消息。

消息的 **key（RocketMQ 消息 id）设为 MongoDB 文档 `_id`**（与 S3 对象 key 相同），
消费方可以用该 key 直接定位/去重消息；消息体（`MailMetaMessage`）示例：

```json
{
  "id": "YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=",
  "sender": "Alice <alice@example.com>",
  "from": "Alice <alice@example.com>",
  "to": "Bob <bob@example.com>",
  "cc": "Carol <carol@example.com>",
  "subject": "Quarterly report",
  "messageId": "<original-123@example.com>",
  "envelopeSender": "postmaster@corp.local",
  "objectKey": "YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=",
  "sha256": "3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b",
  "receivedAt": "2026-08-16T05:00:00Z"
}
```

其它服务拿到 `id` 后即可用同一个值从 MongoDB 查元数据、从 S3 取原邮件，并用 `sha256`
校验取到的原件是否与归档时一致。

> 通知默认带 Mongo outbox：发布失败时文档保持 `PENDING`，后台扫描自动补发；
> 超过最大重试次数后标记 `FAILED`，仍可用重发接口手工处理。发布动作不会回滚已经完成的
> MongoDB/S3 写入，也不会影响 SMTP 服务。
