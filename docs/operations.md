# 运维与可观测性

[← 返回文档索引](../README.md)

## 可观测性

- **指标**：三个应用均暴露 `/actuator/health`、`/actuator/prometheus`、`/actuator/metrics`、
  `/actuator/loggers` 及 liveness/readiness 探针；指标带 `application=<应用名>` 标签。
- **日志关联**：HTTP 请求的 `X-Request-Id` 会写入 `traceId` MDC；SMTP 收信时为每封邮件生成
  `traceId`；归档通知通过 RocketMQ 消息用户属性 `traceId` 传给 mail-cleaner，跨三个应用的日志
  可用同一 traceId 串起来。异步归档/重发任务通过 MDC 装饰器继承调用方 traceId。
- **结构化日志**：本地默认输出可读文本；以 `json` profile 启动（`SPRING_PROFILES_ACTIVE=json`，
  docker compose 默认已开启）时输出 Logstash 格式 JSON，MDC 字段（`traceId` 等）自动进入每条日志。
- **健康检查**：mail-mcp-server 的健康状态同时包含 Elasticsearch 与对象存储 bucket。

三个应用的健康检查与探活端口：journal-archiver `8080`、mail-cleaner `8081`、
mail-mcp-server `8082`，路径均为 `GET /actuator/health`。

## 注意事项

- 收信与归档是异步的：SMTP 立刻应答 `250`，归档在线程池中执行；失败会记录 ERROR 日志，不会影响 SMTP 服务。
- 归档顺序为 S3 → MongoDB → RocketMQ 通知，任何一步失败都不会进入后续步骤；S3 成功而 MongoDB
  失败时保留已写入的对象与 spool，由 inbox relay 自动重试（删除对象可能毁掉唯一原件）。
- 本机没有 RocketMQ 时应用仍可启动，但发送通知会超时并记录 ERROR（配置 `app.notify.rocketmq.enabled: false` 可关闭）。
- mail-cleaner 在 RocketMQ / Elasticsearch 不可用时会分别记录消费启动失败与处理失败日志，消息由
  RocketMQ 重投；健康检查 `GET /actuator/health`（8080/8081/8082）可分别探活三个应用。
- HTTP 接口未加鉴权，生产环境请置于内网或增加认证/白名单。
- 发信 / 回复 / 转发接口（mail-mcp-server `8082`）需要能访问请求方指定的 SMTP 服务器
  （默认 25 / 465 / 587 出站），且请求方提供的账号密码即代表发信身份，按需收紧网络出口。
  MCP 客户端通过 `send_mail` / `reply_mail` / `forward_mail` 三个工具触发同样的真实投递，
  调用会计入 `mail.mcp.tool.calls` / `mail.mcp.tool.duration`（`tool` 标签对应工具名）。
  发信能力的三层保护（`app.security.api-key`、`app.send.allowed-smtp-hosts`、
  `app.send.rate-limit.*`）与 TLS 相关开关见[邮件发送](mail-sending.md)的「鉴权、白名单与限流」
  与「传输安全与投递身份」；未配置时启动日志会给出 WARN 提醒。
- 默认端口 `2525` 无需 root；生产环境请按需改为 `25` 并配置 TLS、鉴权和网络白名单。
- 本机已有 S3 兼容服务时，应用会直接使用其 endpoint；否则请先启动 MinIO。

更完整的可靠性机制（收信落盘、通知 outbox、重试与死信）见
[journal-archiver 可靠性设计](journal-archiver.md#可靠性设计)。
