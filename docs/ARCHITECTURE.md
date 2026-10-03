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

Current merged implementation: Android client, backend API, PostgreSQL mapping, Mega adapter and Xtream catalog/playback layer. Windows and server-side WireGuard remain future phases. Order 001 was the backend-only foundation; its boundaries below describe that completed order, not the current product scope.

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

Android networking is implemented and preserves the backend-resolved provider origin. Production cleartext/TLS policy remains a validation gate before production exposure.

## Sessions

Successful resolution may return a signed PINK session token with a maximum five-minute TTL. The token subject references only the internal mapping id. It contains no Mega token, customer password, Mega subscription id, or other provider secret.

## Non-responsibility

The PINK Backend never relays IPTV video streams. Android, Windows, player and VPN implementation are outside Order 001.

## Global client architecture retained

The full approved architecture remains broader than Order 001:

- Android client;
- Windows client;
- PINK Backend API;
- PostgreSQL;
- Mega OTT adapter;
- Xtream client layer;
- WireGuard control plane in a later phase;
- WireGuard gateway in a later phase;
- shared logical contracts expressed through backend API contracts and UX specifications.

### Client modules

Auth, Catalog, Live TV, VOD, Series, EPG, Search, Favorites, History, Player, VPN, Settings, and operational error reporting remain the logical product modules. Order 001 implements only the backend authentication/mapping proof and does not construct these client modules.

### Android stack

The implemented Android stack uses Kotlin, Jetpack Compose, Media3 1.11.1, Android Keystore/DataStore and Room schema 1. API 24 is the current minimum. WireGuard 004A is merged; identity/permission/read-only adapter work in PR #13 remains unmerged. No real tunnel is certified.

### Windows stack

The approved later Windows stack remains .NET 8, WinUI 3, Windows Credential Locker, a validated media engine, local settings/storage where appropriate, and separately audited WireGuard Windows integration.

## R1 outbound connection architecture

`dns_link` is an origin only: empty path or `/` is accepted and PINK alone appends `/player_api.php`. Scheme, hostname, and valid port are preserved.

DNS resolution produces the public IP set for the attempt. The outbound network backend then connects to a validated IP literal instead of resolving the hostname again. The connected peer is verified against that pinned public IP before HTTP bytes are sent. HTTPS still uses the original hostname for SNI and certificate verification, while HTTP retains the original `Host` header. Redirects remain disabled.
