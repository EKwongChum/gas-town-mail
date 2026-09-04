# Contributing to gas-town-mail

Thanks for your interest in contributing! Issues, documentation fixes, and pull requests are all
welcome. Please read this guide and the [Code of Conduct](CODE_OF_CONDUCT.md) first.

## Getting started

Requirements:

- JDK 17 (the Maven wrapper and all modules target Java 17)
- Docker + Docker Compose (only needed for integration / local stack runs)

Clone the repository and build it from the root:

```bash
git clone git@github.com:EKwongChum/gas-town-mail.git
cd gas-town-mail
./mvnw -B test
```

To run the full local stack (MongoDB, MinIO, RocketMQ, Elasticsearch and the three applications):

```bash
docker compose up -d                       # infrastructure only
docker compose --profile app up -d --build # full stack
```

## Code style

The project uses [Spotless](https://github.com/diffplug/spotless) with the AOSP Java format.
Run the formatter before submitting changes:

```bash
./mvnw -B spotless:apply
```

The check is also bound to `verify`, so CI rejects unformatted code. All Java and POM files carry the
Apache-2.0 license header; keep the header on new files.

## Commit guidelines

- Keep commits focused on one logical change.
- Use conventional commit prefixes where useful: `feat:`, `fix:`, `docs:`, `refactor:`,
  `test:`, `chore:`, `ci:`.
- Reference related issues in the commit or PR description.
- Add a `CHANGELOG.md` entry for user-visible changes.

## Pull request checklist

- [ ] Tests pass locally: `./mvnw -B test`
- [ ] Code is formatted: `./mvnw -B spotless:apply` (or `spotless:check`)
- [ ] Documentation and `CHANGELOG.md` updated when behaviour changed
- [ ] No secrets or local configuration files are committed
- [ ] License headers are present on new Java and POM files

## Reporting issues

Please search the [existing issues](https://github.com/EKwongChum/gas-town-mail/issues) before opening a
new one. Bug reports should include the version, environment, reproduction steps, and relevant logs.
For security vulnerabilities, use the process described in [SECURITY.md](SECURITY.md) instead of a
public issue.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
