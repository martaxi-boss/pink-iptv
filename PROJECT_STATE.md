# PINK IPTV - PROJECT STATE

Date: 2026-09-23
Phase: Architecture / pre-build
Public launch: NO

## Governance

OWNER: project owner.
SUPERVISOR: reviews architecture, defines implementation orders, audits results.
BUILDER: implements only approved orders and reports evidence.

## Current decisions

- Product name: PINK IPTV.
- Original pink/magenta visual identity.
- Functional navigation equivalent to the common IPTV-player pattern, with no copied source code/assets.
- Client authentication UX: Username + Password only.
- Client protocol scope: Xtream-style API only.
- Mega OTT reseller API secrets stay server-side.
- Per-line dns_link is resolved by PINK backend data, never guessed from a base domain.
- WireGuard is the preferred VPN technology.
- VPN should auto-connect as part of the app experience after initial OS permission/setup.
- Android first; Windows follows the same backend/contracts.
- GitHub is source of truth for code and documentation.
- DigitalOcean is runtime infrastructure, not the source of truth.

## Infrastructure observed

Existing DigitalOcean Droplet:
- Ubuntu 24.04 LTS
- region: London
- 1 vCPU
- 1 GB RAM
- 25 GB plan disk, about 11 GB free at audit
- existing Nginx/PostgreSQL/Gunicorn/Python/Node workloads
- suitable for development/POC only
- production VPN should later move to a dedicated gateway
- backups were not enabled at the time of audit

## Open technical gates

1. Confirm the production workflow that populates username -> Mega subscription id -> dns_link for every line.
2. Prove one real Xtream login through the assigned dns_link.
3. Prove one WireGuard client through the existing Droplet without disrupting current services.
4. Supervisor approval of the original PINK design system and screen map.
5. Define production bandwidth plan before onboarding real VPN users.

Implementation status: NOT STARTED.
