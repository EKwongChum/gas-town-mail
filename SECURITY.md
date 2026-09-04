# Security Policy

## Supported versions

Only the latest release is actively supported with security fixes. Older releases should be upgraded
promptly.

| Version | Supported |
| --- | --- |
| latest release (0.1.x) | Yes |
| older versions | No |

## Reporting a vulnerability

Please do **not** open a public issue for security problems. Use GitHub private vulnerability
reporting instead:

- https://github.com/EKwongChum/gas-town-mail/security/advisories/new

Alternatively, you can e-mail the maintainer at ekwongchum@gmail.com. Please include:

- affected version / commit
- a description of the vulnerability and its impact
- reproduction steps or a proof of concept

You will receive an acknowledgement within a few business days and updates as the issue is triaged
and fixed.

## Known operational boundaries

gas-town-mail is an experimental, self-hosted mail archive platform. Before deploying it where
untrusted traffic is possible, note the following:

- The HTTP APIs of all three applications and the MCP endpoint (`/mcp`) have **no built-in
  authentication**. Put them on a trusted network, use a reverse proxy with authentication/authorization,
  or add a security layer of your own.
- The embedded SMTP server binds to `0.0.0.0` by default and accepts mail from any source for
  archiving. Configure TLS and network restrictions for production.
- The MinIO credentials `minioadmin` / `minioadmin` are local development defaults only. Always
  override them with `S3_ACCESS_KEY` / `S3_SECRET_KEY` in any shared or production environment.
- `mail_info` and archived email metadata can contain personal data. Apply retention and access
  controls appropriate to your jurisdiction before deployment.
