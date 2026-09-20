# 邮件发送（mail-mcp-server）

[← 返回文档索引](../README.md)

mail-mcp-server 除了查询归档邮件（MCP）与下载原件，还提供三个出站 HTTP 接口：
直接发信、回复归档邮件、转发归档邮件。三者都由请求方在请求体中提供 SMTP 服务器地址、
端口、账号与密码，服务端不保存任何 SMTP 凭据，因此可以用任意邮箱作为发件人。

| 接口 | 说明 |
| --- | --- |
| `POST /api/mails/send` | 使用请求中的 SMTP 账号直接发送一封邮件 |
| `POST /api/mails/reply` | 回复归档邮件（参数 = 发送参数 + MCP 查询返回的归档 id） |
| `POST /api/mails/forward` | 转发归档邮件（参数 = 发送参数 + MCP 查询返回的归档 id） |

回复与转发用的归档 id 就是 MCP 工具 `search_mails` / `get_mail_by_id` 返回的 `id`
（MongoDB `_id` / S3 对象 key）；服务端据此从对象存储读取归档原件 `.eml`，解析出主题、
收发件人、正文与附件。

## 通用发送参数

三个接口共用以下字段（reply / forward 额外多一个 `id`）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `smtpHost` | string | 是 | SMTP 服务器地址 |
| `smtpPort` | integer | 是 | SMTP 端口（1–65535），如 25 / 465 / 587 |
| `smtpUsername` | string | 否 | SMTP 账号；留空表示不对该服务器做认证 |
| `smtpPassword` | string | 条件 | 提供 `smtpUsername` 时必填 |
| `smtpEncryption` | string | 否 | `none` / `starttls` / `ssl`；缺省时端口 465 用隐式 TLS，其他端口尝试 STARTTLS（不强制） |
| `from` | string | 否 | 发件人，可带显示名（`Alice <alice@example.com>`）；缺省用 `smtpUsername` |
| `to` | string[] | 是 | 收件人，每项可写一个地址，也可写逗号分隔的多个地址 |
| `cc` | string[] | 否 | 抄送人 |
| `subject` | string | 否 | 主题，UTF-8，支持中文 |
| `content` | string | 否 | 正文，UTF-8 |
| `html` | boolean | 否 | 正文是否为 HTML，默认 `false`（纯文本） |
| `attachments` | array | 否 | 附件列表，见下 |

附件对象：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `filename` | string | 附件文件名（必填），支持中文 |
| `contentType` | string | MIME 类型，缺省 `application/octet-stream` |
| `contentBase64` | string | 附件内容的 Base64，可带换行 |

**附件限制**：单个附件不超过 **10 MB**，单封邮件所有附件合计不超过 **20 MB**（收发件人与
转发携带的原附件一起计算）。超限返回 `400`。限制可通过配置调整：

> 附件也可以用 multipart 文件分片上传（无需 Base64），三个接口都支持，见
> [multipart 上传附件](#multipart-上传附件send--reply--forward-通用)。

```yaml
app:
  send:
    max-attachment-size: 10MB
    max-total-attachment-size: 20MB
    connect-timeout: 10s
    read-timeout: 30s
    write-timeout: 60s
```

## 发送邮件

`POST /api/mails/send`

```bash
curl -X POST http://localhost:8082/api/mails/send \
  -H 'Content-Type: application/json' \
  -d '{
    "smtpHost": "smtp.example.com",
    "smtpPort": 587,
    "smtpUsername": "alice@example.com",
    "smtpPassword": "secret",
    "from": "Alice <alice@example.com>",
    "to": ["bob@example.com"],
    "cc": ["carol@example.com"],
    "subject": "季度报告",
    "content": "见附件。",
    "attachments": [
      {"filename": "report.pdf", "contentType": "application/pdf", "contentBase64": "JVBERi0xLjcK"}
    ]
  }'
```

响应（HTTP 200）：

```json
{
  "messageId": "<2f1c...@example.com>",
  "from": "Alice <alice@example.com>",
  "to": ["bob@example.com"],
  "cc": ["carol@example.com"],
  "subject": "季度报告",
  "attachmentCount": 1,
  "attachmentBytes": 9,
  "sentAt": "2026-09-20T14:30:00Z"
}
```

`messageId` 是实际写入 `Message-ID` 头的值，可用于后续关联。

## 回复邮件

`POST /api/mails/reply`：发送参数 + `id`，另有两个可选开关：

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `id` | string | — | 归档 id（MCP 查询返回的 `id`），必填 |
| `replyAll` | boolean | `false` | 同时抄送原邮件 To/Cc 中的其他收件人 |
| `includeOriginalBody` | boolean | `true` | 是否在正文中引用原邮件正文 |
| `includeOriginalAttachments` | boolean | `false` | 是否把原邮件附件一并带上（回复通常不带） |

行为（对齐常见邮件客户端）：

- **收件人**：未传 `to` 时取原邮件的 `Reply-To`，没有 `Reply-To` 时取 `From`；
  `replyAll=true` 时把原邮件的 To/Cc 追加到抄送，并排除发件人自己与新邮件已包含的地址；
- **主题**：未传 `subject` 时用 `Re: 原主题`（原主题已带 `Re:` 前缀则不重复添加）；
- **正文**：`用户正文` + 空行 + `On <原邮件时间>, <原发件人> wrote:` + 以 `> ` 前缀引用的原正文；
  `html=true` 时改为 `<blockquote>` 引用的 HTML；
- **会话串联**：设置 `In-Reply-To: <原 Message-Id>` 与 `References: <原 References...> <原 Message-Id>`，
  使回复落在原邮件会话中；
- **附件**：默认只发送请求里 `attachments` 指定的附件。

```bash
curl -X POST http://localhost:8082/api/mails/reply \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=",
    "smtpHost": "smtp.example.com",
    "smtpPort": 587,
    "smtpUsername": "alice@example.com",
    "smtpPassword": "secret",
    "content": "已经收到，下午回复详细意见。",
    "replyAll": true
  }'
```

上例中 `to` / `subject` 均由归档原件推导，响应里的 `to` / `subject` 是实际使用的值。

## 转发邮件

`POST /api/mails/forward`：发送参数 + `id`，另有两个可选开关：

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `id` | string | — | 归档 id（MCP 查询返回的 `id`），必填 |
| `includeOriginalBody` | boolean | `true` | 是否在正文中嵌入原邮件正文 |
| `includeOriginalAttachments` | boolean | `true` | 是否携带原邮件附件 |

行为：

- **收件人**：必须由请求方指定，`to` 为空返回 `400`（转发不会自动沿用原收件人）；
- **主题**：未传 `subject` 时用 `Fwd: 原主题`（已带 `Fwd:` / `Fw:` 前缀则不重复添加）；
- **正文**：`用户正文` + `---------- Forwarded message ---------` 头（From / Date / Subject /
  To / Cc）+ 原正文；`html=true` 时用 HTML 版本；
- **附件**：默认携带原邮件附件，与请求中的 `attachments` 合并后统一受 10 MB / 20 MB 限制；
  原附件超限时返回 `400`，可改用 `includeOriginalAttachments=false` 只发新附件；
- **会话**：不设置 `In-Reply-To` / `References`，转发开启新会话。

```bash
curl -X POST http://localhost:8082/api/mails/forward \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=",
    "smtpHost": "smtp.example.com",
    "smtpPort": 587,
    "smtpUsername": "alice@example.com",
    "smtpPassword": "secret",
    "to": ["dave@example.com"],
    "content": "请参考下面的邮件。"
  }'
```

## multipart 上传附件（send / reply / forward 通用）

除 JSON（附件用 Base64）外，三个接口也接受 `multipart/form-data`：

- `request` 分片：与 JSON 接口**完全相同的请求体**，其 Content-Type 必须是
  `application/json`；
- `attachments` 分片：一个或多个文件分片，可重复；文件名与 Content-Type 取自分片本身。

用 curl 发信：

```bash
curl -X POST http://localhost:8082/api/mails/send \
  -F 'request={"smtpHost":"smtp.example.com","smtpPort":587,
"smtpUsername":"alice@example.com","smtpPassword":"secret",
"to":["bob@example.com"],"subject":"季度报告","content":"见附件"};type=application/json' \
  -F 'attachments=@report.pdf' \
  -F 'attachments=@photo.jpg'
```

回复与转发同理（URL 换成 `/api/mails/reply` / `/api/mails/forward`），`id`、`replyAll`、
`includeOriginalAttachments` 等字段仍然写在 `request` 分片里：

```bash
curl -X POST http://localhost:8082/api/mails/forward \
  -F 'request={"id":"<archive id>","smtpHost":"smtp.example.com","smtpPort":587,
"smtpUsername":"alice@example.com","smtpPassword":"secret",
"to":["dave@example.com"],"content":"请参考下面的邮件。"};type=application/json' \
  -F 'attachments=@extra.txt'
```

规则：

- 上传文件与 JSON 附件受同一套限制（`app.send.max-attachment-size` 10MB /
  `max-total-attachment-size` 20MB），超限返回 `400`；
- 文件名只取最后一段（浏览器可能带上完整路径，会被去掉），名称中的控制字符会被移除，
  空文件（0 字节）返回 `400`；
- multipart 模式下 `request` 分片里不要再写 `attachments`（Base64），否则返回 `400`
  提示改用文件分片；
- 多个附件分片按上传顺序依次作为附件发送，并与转发携带的原附件一起计算体积；
- servlet 层另有一层上传上限 `spring.servlet.multipart.max-file-size` /
  `max-request-size`（默认 `12MB` / `30MB`，见 `application.yml`）。它**刻意高于**业务上限：
  业务超限时返回与其他接口一致的 JSON `400`，只有超过 servlet 上限时才由容器直接返回
  `413`（无响应体）。调整 `app.send.*` 上限时请同步调整这两项。

## MCP 工具

除了 HTTP 接口，mail-mcp-server 还通过 MCP 暴露三个等价的工具（参数与 HTTP JSON 请求体一致）：

| 工具 | 对应能力 |
| --- | --- |
| `send_mail` | 直接发送（`to` 必填） |
| `reply_mail` | 回复归档邮件：`id` + 发送参数，可选 `replyAll` / `includeOriginalBody` / `includeOriginalAttachments` |
| `forward_mail` | 转发归档邮件：`id` + 发送参数（`to` 必填），默认携带原附件 |

其中 `id` 就是 `search_mails` / `get_mail_by_id` 返回的归档 id，因此模型可以「先查询、再回复/转发」。
MCP 调用示例（JSON-RPC，真实投递）：

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "reply_mail",
    "arguments": {
      "id": "YWxpY2VAZXhhbXBsZS5jb20=_PG9yaWdpbmFsLTEyM0BleGFtcGxlLmNvbT4=",
      "smtpHost": "smtp.example.com",
      "smtpPort": 587,
      "smtpUsername": "alice@example.com",
      "smtpPassword": "secret",
      "content": "已经收到，下午回复详细意见。",
      "replyAll": true
    }
  }
}
```

与 HTTP 接口的差异：

- 附件只能以 Base64 传入（MCP 工具参数是 JSON，没有文件上传）；
- SMTP 凭据同样由每次调用传入、服务端不保存；工具描述已提示这是真实投递（
  `readOnlyHint=false`、`openWorldHint=true`）并提示不要回显密码；
- 参数校验失败、归档原件缺失、SMTP 不可达/被拒收都返回 `isError=true` 的错误文本，
  不抛协议级异常。

## 错误响应
错误统一返回 JSON：`{"error": "..."}`。

| 状态码 | 场景 |
| --- | --- |
| `400` | 缺少 `smtpHost` / `smtpPort`、地址格式非法、附件缺文件名或非 Base64、附件超限、转发未传 `to`、请求体不是合法 JSON |
| `404` | reply / forward 的 `id` 在对象存储中不存在（可能尚未归档或已被清理） |
| `413` | multipart 上传超过 servlet 层 `spring.servlet.multipart.*` 上限（多数容器直接返回，此时无响应体） |
| `415` | 请求的 Content-Type 既不是 `application/json` 也不是 `multipart/form-data` |
| `502` | SMTP 服务器不可达、认证失败或被拒收（错误信息包含 SMTP 返回的原因） |
| `500` | 其他未预期错误（详情只记日志） |

## 安全与注意事项

- SMTP 账号密码只用于当次发送，不落库、不写日志；日志只记录目标服务器、收件人数量与附件数量。
- 这三个接口没有鉴权，且能以任意凭据对外发信，生产环境请部署在内网或加认证/白名单。
- 发信是同步的：请求会等到 SMTP 服务器接受邮件后才返回，超时时间由 `app.send.*-timeout` 控制。
- 归档原件的正文引用统一转换为纯文本（HTML 邮件会去标签后引用），避免在转发内容中引入原始 HTML。
