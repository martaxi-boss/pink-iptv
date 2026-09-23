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

## General integration rules retained

Mega reseller authentication is backend-only. The Mega API token never enters Android, Windows, client configuration, analytics, screenshots, logs, or Git history.

Mega may call username/password subscriptions M3U internally. That provider naming does not authorize a user-facing M3U import flow: the PINK client remains username/password Xtream-style only.

The assigned `dns_link` is authoritative. PINK never infers a DNS host, derives random subdomains, substitutes another base domain, or brute-forces DNS.

Beyond authentication, future Xtream functionality must feature-detect provider behavior rather than assume unsupported capabilities. Candidate later capabilities include Live categories/streams, VOD, Series, and EPG where the provider actually supports them.

## Platform DNS selection

Each Mega subscription may provide two distinct authoritative destinations:

- `dns_link`
- `dns_link_for_samsung_lg`

PINK IPTV platform selection is normative:

### Use `dns_link`

Use `dns_link` for:

- Android phone;
- Android tablet;
- Android TV;
- TV Box;
- Windows.

Android TV is not treated as a Samsung/LG native application.

### Use `dns_link_for_samsung_lg`

Use `dns_link_for_samsung_lg` exclusively for future native applications for:

- Samsung Smart TV / Tizen;
- LG Smart TV / webOS.

The backend mapping preserves `dns_link` and `dns_link_for_samsung_lg` separately. PINK never automatically substitutes one for the other and never derives either destination from the other.

PINK never hardcodes `zvpnm.com` or `daizsmart.com`. The hostname/subdomain returned by Mega for each subscription is authoritative for its corresponding field.

Samsung/LG native applications remain outside the current v1 and require a separate future implementation order. This platform-selection contract does not change the behavior implemented in Order 001.

## Live proof remediation record

The completed live proof observed that the real Xtream upstream reset the connection when presented with HTTPX's default User-Agent. The same authorized endpoint responded correctly when the client used the stable User-Agent `PINK-IPTV/0.1`.

The final remediation therefore fixes a stable Xtream User-Agent. It does not change the stored `dns_link`, outbound SSRF protections, pinned-peer behavior, redirect policy, or authentication semantics.

No provider hostname, username, password, Mega token, session token, raw provider response, or credential-bearing URL is recorded here.
