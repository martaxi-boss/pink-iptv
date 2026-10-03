# Security Baseline

## Never commit or persist

- Mega OTT API token
- Xtream customer passwords
- production database credentials
- session signing keys
- WireGuard private keys
- SSH private keys
- signing keystores/passwords

Order 001's database schema contains no password/token/key columns.

## Environment-only secrets

Backend configuration is environment-driven. `.env` files remain ignored. The application does not print complete settings or dump the environment. Outside tests, `SESSION_SIGNING_KEY=change-me` is rejected; Mega import fails explicitly when its token is unavailable/default.

## Mega transport

Mega calls require HTTPS and Bearer authorization. HTTP timeouts are explicit, redirects are disabled, and neither Authorization headers nor raw Mega responses are logged.

## Xtream credentials in query parameters

Xtream `player_api.php` commonly carries username and password as query parameters. Consequently full request URLs are sensitive data and must never be logged. `httpx` and `httpcore` request logging are suppressed, exceptions are translated to sanitized internal errors, and tests assert that passwords/auth query strings do not reach logs.

## Outbound dns_link validation

The Xtream destination comes only from the locally stored mapping populated by authorized Mega import. Before use:

- only `http`/`https` are allowed;
- a valid hostname is required;
- embedded URL credentials, query and fragment are rejected;
- localhost/local hostnames are rejected;
- loopback, link-local, private/reserved literal IPs are rejected;
- resolved non-public addresses are rejected;
- automatic redirects are disabled.

This validation is specific to the authoritative mapping path and is not exposed as a generic user-supplied proxy/SSRF primitive.

## HTTP/TLS

An HTTP `dns_link` from Mega is preserved for this proof; it is not silently rewritten. Production cleartext policy is a later Supervisor gate.

## Sessions

Signed PINK sessions live for at most five minutes in Order 001. The token subject identifies only the internal mapping record. No Mega id/token, provider password, or customer password is present. Refresh tokens and persistent revocation are outside this order.

## Privacy and logging

`/v1/session/resolve` returns `Cache-Control: no-store`. Unknown usernames and wrong passwords share the same public result. Raw upstream exceptions are never returned to clients.

Request-validation failures retain HTTP 422 but return only `{"detail":"Invalid request"}` with `Cache-Control: no-store`. Validation inputs, extra field names, parser context and request bodies are not echoed or logged by the handler. Invalid requests do not construct an Xtream client. Regression tests cover malformed JSON, missing fields, invalid credential types, extra values and sensitive extra field names.

## Backend transport and abuse controls

The public PINK Backend is HTTPS-only. TLS certificate validation must remain enabled for backend and provider HTTPS connections.

Authentication and enrollment endpoints require rate limiting as an architectural security control before production exposure. Order 001/R1 does not implement enrollment or a production rate-limiter; it preserves this requirement for the phase that exposes those surfaces.

## Outbound connection binding

Order 001 accepts a provider `dns_link` only as an origin: `http://hostname[:port]` or `https://hostname[:port]`, with either an empty path or `/`. Any provider path, query, fragment, or embedded credentials are rejected.

For hostname destinations, DNS is resolved once for the outbound attempt and every returned address must be public. The Xtream transport then dials only a validated IP literal. Before HTTP bytes are written, the connected peer address is checked against that pinned public IP. A peer mismatch, loopback, private, link-local, reserved, or otherwise non-public peer fails closed.

For HTTPS, the original provider hostname remains the TLS SNI and certificate-verification hostname; TLS verification is never disabled. For HTTP, the original `Host` header is preserved. DNS rebinding therefore cannot redirect the authenticated request to a second independently resolved private destination.

## Supply chain

Dependencies remain pinned/reproducible for the implemented backend. Dependency/security scanning is required as implementation expands. Licenses for media, VPN/WireGuard integration, and other embedded libraries must be reviewed before release.

## Privacy and telemetry

Collect only operational telemetry needed to diagnose failures and run the service. Viewing history is not collected by default. Any future analytics require Supervisor review, a defined data-minimization purpose, and appropriate user-facing disclosure.
