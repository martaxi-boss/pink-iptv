# Security Baseline

## Never commit

- Mega OTT API token
- Xtream customer passwords
- production DATABASE_URL credentials
- JWT/session signing keys
- WireGuard private keys
- SSH private keys
- keystores/signing passwords
- Google/Windows store secrets

## Server secrets

Use environment variables or a dedicated secret store with least privilege.
.env files are ignored by Git.

## Client secrets

Mega reseller token never enters Android/Windows packages.
WireGuard private key is generated on device and remains in protected local storage.
Remembered Xtream credentials use platform secure storage.

## Network

Backend is HTTPS only.
Validate TLS.
Rate-limit authentication and enrollment endpoints.
Do not log passwords or Authorization headers.
Redact dns_link/usernames from unnecessary analytics.

## Sessions

Backend issues short-lived signed sessions/configurations after validation.
A backend session is not a Mega reseller token.
Logout/revocation must not silently mutate the provider subscription.

## Supply chain

Pin dependencies.
Enable dependency/security scanning in CI when implementation starts.
Review licenses of media and WireGuard embedding libraries before release.

## Privacy

Collect only operational telemetry needed to diagnose failures.
Do not collect viewing history by default.
Any future analytics require supervisor review and user-facing disclosure.
