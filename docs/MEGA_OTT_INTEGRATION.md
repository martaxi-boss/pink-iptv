# Mega OTT Integration

Official references:
- https://megaott.net/docs/authentication
- https://megaott.net/docs/subscriptions

## Confirmed public API behavior

Mega OTT documents OAuth2-style Bearer token authentication.
The subscription model includes:
- id
- username
- password
- package
- max_connections
- expiring_at
- dns_link
- dns_link_for_samsung_lg
- portal_link

The create-subscription example returns dns_link in the same response.
The API also documents retrieve-by-id, extend, deactivate and activate operations.

## Critical architecture constraint

The currently published subscription documentation exposes retrieve-by-id, not a documented username lookup/list endpoint.

Therefore v1 must NOT assume:
"username -> Mega API search -> dns_link"
unless the supervisor/builder verifies a supported endpoint from the live account.

Reliable strategy:
1. New lines created through a PINK provisioning/admin flow call Mega POST /v1/subscriptions.
2. Store returned mega_subscription_id + username + dns_link in PINK PostgreSQL.
3. Existing manually-created lines require a one-time import/bootstrap mapping before username-only login can work.
4. If Mega later exposes an official search/list/webhook mechanism, add it behind the Mega adapter without changing clients.

## Mega naming versus PINK product scope

Mega calls username/password subscriptions type M3U.
PINK v1 must not expose M3U import or playlist URL entry.
The client uses the returned dns_link as the host for Xtream-style player API calls.

## Xtream adapter expectations

Feature-detect provider behavior; do not hardcode assumptions beyond tested endpoints.
Expected capabilities include:
- authentication/account info
- live categories/streams
- VOD categories/streams/info
- series categories/list/info
- EPG where available

The assigned dns_link is authoritative.
Do not derive random subdomains from a base domain.
Do not hardcode zvpnm.com as a universal host.

## Secrets

MEGA_OTT_API_TOKEN exists only as a backend secret.
Never embed it in Android, Windows, logs, analytics, GitHub, screenshots or client config.
