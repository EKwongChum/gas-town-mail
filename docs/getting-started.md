# 快速开始

[← 返回文档索引](../README.md)

## 1. 启动本地依赖（MongoDB + MinIO + RocketMQ + Elasticsearch）

```bash
docker compose up -d
```

> 应用会在启动时尝试自动创建 S3 bucket（默认 `journal-emails`），无需手动建桶。
> RocketMQ 的 topic `mail_meta_topic` 无需提前创建，生产者发送时会自动创建。
> compose 使用了 profile 隔离：**默认 `docker compose up -d` 只启动基础设施**
> （MongoDB / MinIO / RocketMQ / Elasticsearch），三个应用位于 `app` profile 下，需要时再启动。
> 三个应用都随 compose 提供 Dockerfile（多阶段 JDK 17 构建），容器内各服务地址（MongoDB、MinIO、
> RocketMQ、Elasticsearch）已自动指向 compose 中的基础设施服务，无需改配置文件：

> MinIO 默认账号 `minioadmin/minioadmin` 仅用于本地开发，生产环境务必通过
> `S3_ACCESS_KEY` / `S3_SECRET_KEY` 环境变量覆盖。

```bash
docker compose up -d                          # 仅基础设施
docker compose --profile app up -d --build    # 完整栈（基础设施 + 三个应用）
```

> 完整栈端口：SMTP 2525 / journal-archiver 8080 / mail-cleaner 8081 / mail-mcp-server 8082；
> 也可单独启动某个应用，如 `docker compose --profile app up -d --build mail-mcp-server`。

## 2. 构建与启动应用

需要 JDK 17，在项目根目录构建：

```bash
export JAVA_HOME=/path/to/jdk17
./mvnw clean package       # 构建全部模块并运行测试（首次会自动下载 Maven）
```

归档应用（HTTP 8080 / SMTP 2525）：

```bash
java -jar journal-archiver/target/journal-archiver-2.0.0.jar
# 或先安装共享模块到本地仓库，再用 spring-boot:run 调试：
# ./mvnw install -DskipTests
# ./mvnw -pl journal-archiver spring-boot:run
```

清洗应用（HTTP 8081 / 消费 mail_meta_topic）：

```bash
java -jar mail-cleaner/target/mail-cleaner-2.0.0.jar
```

MCP 查询应用（HTTP 8082 / MCP 端点 `/mcp`）：

```bash
java -jar mail-mcp-server/target/mail-mcp-server-2.0.0.jar
```

> MCP 工具的完整参数与调用示例见
> [mail-mcp-server/README.md](../mail-mcp-server/README.md)（接口文档）。

三个应用都集成了 springdoc（OpenAPI / Swagger UI），接口地址：

| 应用 | OpenAPI JSON | Swagger UI |
| --- | --- | --- |
| journal-archiver（8080） | `http://localhost:8080/v3/api-docs` | `http://localhost:8080/swagger-ui.html` |
| mail-cleaner（8081） | `http://localhost:8081/v3/api-docs` | `http://localhost:8081/swagger-ui.html` |
| mail-mcp-server（8082） | `http://localhost:8082/v3/api-docs` | `http://localhost:8082/swagger-ui.html` |

Swagger UI 可直接在页面上调试 REST 接口（journal-archiver 的重发接口、mail-cleaner 的批量删除
与原邮件下载接口、mail-mcp-server 的邮件原件批量 zip 下载；MCP 端点 `/mcp` 也文档化在 OpenAPI 中）。

> 注意：所有应用都依赖 `mail-common`，请始终从根目录执行 `mvn clean package`（reactor 构建），
> 或用 `java -jar` 直接运行打包产物；不要直接对单个模块执行 `mvn spring-boot:run`（未安装共享模块会解析失败）。

启动日志应包含：

```text
SMTP server started on 0.0.0.0:2525
Created object storage bucket 'journal-emails'
```

## 3. 发送一封测试邮件

使用 [swaks](https://github.com/jetmore/swaks)（或任意 SMTP 客户端）向 `2525` 端口发送
[journal-archiver/src/main/resources/samples/journal-sample.eml](../journal-archiver/src/main/resources/samples/journal-sample.eml)：

```bash
swaks --server 127.0.0.1:2525 \
      --from postmaster@corp.local \
      --to journal@archive.local \
      --data journal-archiver/src/main/resources/samples/journal-sample.eml
```

归档成功后日志示例：

```text
Received message via SMTP: client=/127.0.0.1:xxxxx, envelope sender=postmaster@corp.local, ...
Extracted embedded original email (313 bytes)
Saved journal email metadata to MongoDB: id=YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=
Stored email object s3://journal-emails/YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=
```

## 下一步

- 配置项与可靠性设计：[journal-archiver](journal-archiver.md)
- 清洗与索引：[mail-cleaner](mail-cleaner.md)
- 查询与下载：[mail-mcp-server](mail-mcp-server.md)
- 架构、id 格式与采集过滤：[架构与设计](architecture.md)
- 观测、告警与注意事项：[运维与可观测性](operations.md)
