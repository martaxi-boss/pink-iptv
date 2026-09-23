# System Architecture

## Components

1. Android client
2. Windows client
3. PINK Backend API
4. PostgreSQL
5. Mega OTT adapter
6. Xtream client layer
7. WireGuard control plane
8. WireGuard gateway
9. GitHub repository and CI/CD

## Runtime flow

Client
  -> PINK Backend over HTTPS
     -> resolves local subscription mapping
     -> optionally refreshes metadata from Mega OTT by subscription id
     -> returns signed client configuration

Client
  -> WireGuard Gateway when VPN is enabled
  -> assigned Xtream dns_link
  -> player_api.php/catalog/EPG/stream endpoints

The PINK Backend must never relay video streams.

## Subscription mapping

Canonical PINK record:
- id
- mega_subscription_id
- username
- dns_link
- dns_link_samsung_lg optional
- expiring_at
- active/status cache
- last_synced_at
- created_at/updated_at

Do not store the Xtream password in the backend unless a later audited requirement proves it necessary.

For remembered login, client credentials must use Android Keystore / Windows Credential Locker equivalents.

## Proposed backend endpoints

POST /v1/session/resolve
- input: username + password + installation_id
- resolves username -> subscription mapping
- refreshes Mega metadata by known subscription id when appropriate
- validates the supplied credentials against the assigned Xtream endpoint
- returns short-lived signed session/config

GET /v1/app-config
- minimum supported version
- feature flags
- VPN policy
- support/maintenance message

POST /v1/vpn/enroll
- input: authenticated installation + WireGuard public key
- returns peer assignment and gateway config
- never receives client WireGuard private key

POST /v1/vpn/heartbeat
- optional health/telemetry without viewing-history payloads

POST /v1/session/logout
- revokes server session; does not delete provider line

## Client modules

Auth
Catalog
LiveTV
VOD
Series
EPG
Search
Favorites
History
Player
VPN
Settings
Telemetry/Error reporting

## Platform implementation

Android:
- Kotlin
- Jetpack Compose
- Media3 / ExoPlayer
- Android VpnService + audited WireGuard integration
- Room/DataStore for local state

Windows:
- .NET 8
- WinUI 3
- robust media engine such as LibVLCSharp, subject to builder validation
- Windows Credential Locker
- official/audited WireGuard Windows integration
- SQLite/local settings

Shared behavior is defined by API contracts and UX specs rather than forcing one UI runtime across both platforms.
