# Infrastructure Plan

## Current OVH baseline

Audited after the controlled migration on 2026-09-23:
- OVHcloud VPS in Gravelines, France
- Ubuntu 24.04 LTS
- 4 vCores
- 8 GB RAM
- about 72 GB root filesystem
- OVH daily backup available on the VPS plan
- Nginx, PostgreSQL, Python, PM2 and Remote Desktop Commander installed
- UFW and Fail2ban enabled
- PINK backend staging runs only on `127.0.0.1:8010`
- PINK public launch remains unauthorized
- DigitalOcean is no longer an application runtime dependency

## Use of current OVH VPS

Allowed for:
- backend development/staging
- Mega API adapter POC
- one-client WireGuard POC
- integration tests

Not approved yet for:
- production VPN for multiple paying users
- restreaming
- high-bandwidth proxy
- destructive firewall changes
- replacing existing workloads

## Target production topology

PINK Backend VPS:
- HTTPS API
- PostgreSQL
- Mega adapter
- app config
- VPN control plane

PINK VPN Gateway:
- WireGuard
- peer management
- NAT/forwarding
- monitoring
- bandwidth sizing

They may begin co-located for POC, but production separation is the design target.

## Deployment

GitHub is source of truth.
Deploy by tagged/approved commit.
Secrets remain in server environment/secret storage.
Use systemd services and Nginx reverse proxy unless supervisor approves a different standard.

## Backups

Before infrastructure mutation, create a recoverable snapshot/backup plan.
Database backups must be automated before production launch.
