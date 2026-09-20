# Test TLS material

`test-smtp.p12` is a throw-away PKCS12 keystore with a self-signed certificate for
`CN=localhost` (`SAN=dns:localhost`), password `changeit`. It exists only so
`SmtpTlsEndToEndTest` can run a real TLS handshake against the embedded SMTP server; the test JVM
trusts it through the `javax.net.ssl.trustStore*` properties configured in the surefire
configuration of this module.

It is **test-only** material: never use it for a real service, and do not add it to a production
trust store.

Regenerate with:

```bash
"$JAVA_HOME/bin/keytool" -genkeypair -alias test-smtp -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=localhost,OU=mail-mcp-server-tests,O=gas-town-mail" -ext "SAN=dns:localhost" \
  -keystore mail-mcp-server/src/test/resources/tls/test-smtp.p12 -storetype PKCS12 \
  -storepass changeit -keypass changeit
```
