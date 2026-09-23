# Mega OTT Integration

Official references reviewed for this architecture:

- https://megaott.net/docs/authentication
- https://megaott.net/docs/subscriptions

## Confirmed behavior used by Order 001

Mega documents Bearer-token authentication and `GET /v1/subscriptions/{id}`. The documented M3U subscription response includes `type`, `id`, `username`, `password`, `expiring_at`, `dns_link`, and `dns_link_for_samsung_lg`.

Order 001 uses only retrieve-by-ID in production code.

## Existing-line bootstrap

An internal CLI receives a known `mega_subscription_id`, calls `GET /v1/subscriptions/{id}`, requires the returned id to match, requires `type=M3U`, and persists only:

- id
- username
- dns_link
- dns_link_for_samsung_lg
- expiring_at
- sync timestamps

Import is idempotent and refreshes authoritative metadata for the same subscription id.

## No username lookup assumption

The reviewed public documentation does not establish a subscription search/list endpoint by username. The backend therefore does not invent or call one. Username-only customer login works only after a line has a local mapping populated by the known-ID bootstrap (or a separately approved future provisioning mechanism).

## Password handling

The Mega response may contain a password. It is parsed only transiently so the documented response can be validated, represented as a secret type, and then discarded. It is never stored, printed, logged, included in fixtures with real values, or returned to clients.

The customer-supplied password for Xtream authentication is also request-scoped and is never persisted or cached.

## Authoritative dns_link

`dns_link` is used exactly as returned and persisted. The implementation does not derive a base domain, invent a prefix/subdomain, substitute `zvpnm.com`, change hosts, brute-force DNS, or rewrite HTTP to HTTPS.

Only safe path normalization is performed to form `<dns_link>/player_api.php`.

## Operations excluded from Order 001

No live code path creates, extends, deactivates, or activates subscriptions. Those documented Mega endpoints are not used by this order.
