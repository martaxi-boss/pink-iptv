# PINK IPTV - SUPERVISOR HANDOFF 001

> Historical note (2026-09-24): references in this handoff to the original DigitalOcean host and its 1 vCPU / 1 GB RAM / 25 GB limits describe the project's initial baseline only. They are SUPERSEDED for current development/staging by the OVH infrastructure recorded in `PROJECT_STATE.md` and `docs/INFRASTRUCTURE.md`. This note does not rewrite the historical decisions below.


## Role

You are the SUPERVISOR for PINK IPTV.
The OWNER has defined the product direction.
The BUILDER must not start implementation until you issue an explicit construction order.

Repository: martaxi-boss/pink-iptv
Baseline rule: always use LATEST MAIN at the moment an order is issued.
Public launch: NO.

## Mission

Review the complete PINK IPTV architecture in this repository and convert it into controlled builder orders.

The intended product is an original PINK IPTV player for Android/Android TV and Windows.

Functional objective:
- preserve the familiar information architecture of a full IPTV player: Login, Home, Live TV, Movies, Series, Catch Up/EPG, Search, Favorites, Player, Settings;
- do not copy third-party source code, proprietary assets, trademarks or pixel-identical UI;
- use original PINK visual identity.

## Non-negotiable product rules

1. Customer login is Username + Password only.
2. No customer DNS/portal field.
3. No user-facing M3U import.
4. v1 client is Xtream-style only.
5. Mega OTT reseller token is backend-only.
6. Per-subscription dns_link is authoritative; never infer a random subdomain from zvpnm.com or another base domain.
7. Backend must not proxy video.
8. WireGuard is the preferred integrated VPN.
9. One WireGuard peer/keypair per installation; no shared private key in APK/EXE.
10. GitHub contains code/docs, never production secrets.
11. Existing DigitalOcean Droplet is POC/staging only for VPN until capacity and isolation are proven.
12. Public launch remains NO.

## Confirmed Mega facts to design around

Official Mega OTT documentation currently shows Bearer-token authentication.
Its subscription response includes id, username, password, expiring_at and dns_link.
Its published page documents create-subscription and retrieve-subscription-by-id.

Critical gate:
The published documentation reviewed for this baseline does NOT document username lookup/list for subscriptions.
Therefore the builder must not pretend the backend can discover any arbitrary existing username from Mega automatically.

Required solution:
- PINK backend owns/records new provisioning responses; and/or
- build a verified import/bootstrap path for existing lines; and/or
- verify an officially supported Mega endpoint not yet documented in the reviewed page.

This must be solved before production username-only login.

Mega may label these username/password lines M3U internally. That does not authorize adding M3U import to the client.

## Platform architecture

Android:
Kotlin + Jetpack Compose + Media3/ExoPlayer + secure storage + Android VPN integration.

Windows:
.NET 8 + WinUI 3 + validated media engine + Credential Locker + validated WireGuard integration.

Backend:
FastAPI + PostgreSQL is the preferred baseline unless the supervisor approves a better reasoned alternative.

Infrastructure:
- HTTPS backend
- Mega adapter
- PostgreSQL
- app config
- VPN control plane
- eventual dedicated WireGuard gateway

## Required screen structure

Splash
Login
Home
Live TV
Movies
Series
EPG/Catch Up
Search
Favorites
Player
Settings
Account
VPN status
Support/About

Android TV must be first-class: remote/D-pad navigation and focus are acceptance criteria.

## PINK visual direction

Use design/BRAND_SYSTEM.md.
Dark media UI, pink/magenta primary actions and focus.
Original P/play logo concept.
Do not reuse Smarters artwork/icons/assets.

## Existing infrastructure warning

The current DigitalOcean server already hosts Nginx, PostgreSQL, Gunicorn, Python and Node workloads.
It has about 11 GB free disk, 1 vCPU and 1 GB RAM.
Do not perform destructive networking/firewall changes.
Before WireGuard POC, require snapshot/rollback and an audit of existing services.

## Supervisor requested output

After review, issue the first builder order as:
PINK IPTV - FOUNDATION / MEGA PROOF 001

Keep Order 001 narrow:
- initialize backend code only
- create database model/migration for subscription mapping
- implement Mega client with secrets from environment
- implement verified mapping/import/provisioning path
- implement minimal session resolve/validate flow
- add automated tests
- stage safely
- no Android, Windows or VPN implementation yet

The first proof must demonstrate with an owner-authorized test line:
username + password -> exact stored Mega subscription -> exact dns_link -> successful Xtream authentication.

## Builder prohibitions for Order 001

- no production secrets in Git
- no hardcoded customer password
- no guessed Mega endpoints
- no DNS brute-force
- no stream proxy
- no UI cloning
- no WireGuard installation
- no public launch
- no unrelated edits
- no merge without supervisor audit

## Documents to review

README.md
PROJECT_STATE.md
docs/PRODUCT_SPEC.md
docs/ARCHITECTURE.md
docs/MEGA_OTT_INTEGRATION.md
docs/VPN_ARCHITECTURE.md
docs/UX_STRUCTURE.md
design/BRAND_SYSTEM.md
docs/INFRASTRUCTURE.md
docs/SECURITY.md
docs/ROADMAP.md
docs/TEST_STRATEGY.md
docs/IMPLEMENTATION_PLAN.md

## Decision requested

Approve, modify, or reject the architecture.
If approved, issue Order 001 only.
Do not open later phases until the Mega/dns_link proof is complete.
