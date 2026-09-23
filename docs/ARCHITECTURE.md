# System Architecture

## Components

1. Android client
2. Windows client
3. PINK Backend API
4. PostgreSQL
5. Mega OTT adapter
6. Xtream client layer
7. WireGuard control plane (later phase)
8. WireGuard gateway (later phase)
9. GitHub repository and CI/CD

Order 001 implements only items 3-6 plus CI.

## Order 001 runtime path

```text
known mega_subscription_id
  -> Mega GET /v1/subscriptions/{id}
  -> validate returned id and type=M3U
  -> extract exact username/dns_link/dns_link_for_samsung_lg/expiring_at
  -> persist local mapping (never password)

client username + password
  -> local mapping lookup by exact username
  -> exact stored dns_link
  -> outbound destination safety checks
  -> <dns_link>/player_api.php
  -> Xtream authentication
  -> classified PINK response
```

The backend does not search Mega by username. No such search endpoint is assumed or implemented.

## Subscription mapping

Canonical Order 001 record:

- id
- mega_subscription_id (unique, required)
- username (unique, required and preserved exactly)
- dns_link (required, authoritative)
- dns_link_samsung_lg (optional)
- expiring_at (optional, timezone-aware)
- last_synced_at
- created_at
- updated_at

There is deliberately no password, Mega token, WireGuard key, or invented active/status field.

## Session resolve

`POST /v1/session/resolve` accepts only username and password. Unknown username and wrong password are intentionally indistinguishable to the caller.

Functional codes:

- `SUCCESS`: Xtream authenticated.
- `INVALID_CREDENTIALS`: no local mapping, wrong password, or `auth=0` without a more specific proven state.
- `EXPIRED`: authoritative local expiry has passed or Xtream explicitly reports expiry.
- `DISABLED`: only an explicit upstream disabled/deactivated/banned/blocked state.
- `DNS_UNREACHABLE`: real host/DNS resolution failure.
- `UPSTREAM_ERROR`: timeout, TLS/connection failure, unexpected HTTP response, malformed JSON/schema, or other unclassified upstream failure.

The response never returns raw exceptions and has `Cache-Control: no-store`.

## Outbound URL safety

Before Xtream authentication, `dns_link` must use `http` or `https`, contain a valid hostname, and contain no embedded credentials, query, or fragment. Localhost, loopback, link-local, private/reserved literal addresses, local hostnames, and hostnames resolving to non-public addresses are rejected. Redirects are not followed.

The only generated path is `player_api.php`; the provider host is never guessed or replaced.

## HTTP/TLS gate

Mega documentation can return an HTTP `dns_link`. Order 001 preserves the exact scheme received. It does not upgrade HTTP to HTTPS automatically, even if a separate credential-free TLS probe would succeed.

Production cleartext/TLS policy remains a Supervisor gate before Android networking is implemented.

## Sessions

Successful resolution may return a signed PINK session token with a maximum five-minute TTL. The token subject references only the internal mapping id. It contains no Mega token, customer password, Mega subscription id, or other provider secret.

## Non-responsibility

The PINK Backend never relays IPTV video streams. Android, Windows, player and VPN implementation are outside Order 001.
