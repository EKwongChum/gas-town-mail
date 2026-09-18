# 开发与测试

[← 返回文档索引](../README.md)

## 测试

```bash
./mvnw test     # 在项目根目录运行，构建全部模块
```

共 142 个测试，按模块分布：

- **mail-common（21）**：id 生成、journal 识别、原邮件提取、邮件详情解析（含中文主题解码、
  ReceivedTime、content-type、附件名）、原邮件字节的 SHA-256 摘要计算，以及 HTTP 请求
  `X-Request-Id` → `traceId` MDC 过滤器（客户端 id 透传、缺失时自动生成、非法 id 自动替换、
  MDC 清理）；
- **journal-archiver（60）**：SMTP 收信落盘 spool（含 fsync/恢复/隔离损坏文件/清理临时文件）、RocketMQ 通知
  （含 producer 并发首次启动、traceId 作为消息用户属性传递）、Mongo outbox 自动重发与 FAILED 上限、归档流水线
  （S3→Mongo→通知顺序、原子 `$inc`、objectKey 落库、S3 失败重试、Mongo 失败补偿删除、
  缺 Message-Id 自动生成/内容摘要兜底、sha256 落库、死信落盘）、重发服务（含缺失 id 统计与
  null body 400）、
  采集过滤（sender/from/to/cc 白名单、与关系、大小写与显示名匹配）、HTTP 接口、真实 SMTP
  握手端到端测试，以及 SMTP 健康指示器、统一异常处理、`AppProperties`
  配置绑定（含 `name-server` kebab 属性）、RocketMQ producer 装配、SMTP 消息捕获与超限拒绝、
  异步 MDC 传播、OpenAPI 元数据；
- **mail-cleaner（30）**：消费成功/失败重投、批量消息部分失败、消息 traceId 恢复与清理、
  S3 读取 + 邮件解析 + 写入
  `mail_info` 文档，以及 RocketMQ 健康指示器、`CleanerProperties` 绑定、清洗服务
  （无效载荷、objectKey 读取、索引复用、sha256 取自通知或回退计算）、批量删除（存在/缺失 id、
  去重、空请求与数量上限、HTTP 400/null body 处理）、原件下载（读取、未找到 404、空 id 400、
  HTTP 响应头）、OpenAPI 元数据等用例；
- **mail-mcp-server（31）**：ES 查询服务（分页搜索、页大小上限、计数、按 id 查询）、
  MCP 工具（参数解析、默认分页、按 id 查询、计数、调用成功/失败指标）、对象存储健康指示器
  （bucket 可达 / 不存在 / 端点不可达）、MCP Server 装配、OpenAPI 文档生成，
  以及邮件原件批量 zip 下载（S3 读取→临时文件→zip 内容、缺失 id 404、空请求/超上限 400、
  HTTP 响应头、临时文件按文件 TTL 自动清理、读取期间延迟删除、启动时清理残留文件）。

### 在容器内运行测试

```bash
docker run --rm \
  -v "$(pwd)":/workspace -w /workspace \
  -v "$HOME/.m2":/root/.m2 \
  maven:3.9-eclipse-temurin-17 \
  mvn -B test
```

> 挂载 `~/.m2` 复用本地 Maven 缓存，避免在容器内重复下载依赖。

## 贡献

欢迎通过 [GitHub Issues](https://github.com/EKwongChum/gas-town-mail/issues) 报告问题或提出功能建议，
也欢迎提交 [Pull Request](https://github.com/EKwongChum/gas-town-mail/pulls)。
提交前请阅读 [CONTRIBUTING.md](../CONTRIBUTING.md)，并确保在项目根目录运行 `./mvnw test` 通过全部测试。

## 许可证

本项目基于 [Apache License 2.0](../LICENSE) 开源。
