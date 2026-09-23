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
