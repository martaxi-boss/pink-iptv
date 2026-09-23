# Infrastructure Plan

## Current DigitalOcean baseline

Audited 2026-09-23:
- Ubuntu 24.04 LTS
- London region
- 1 vCPU
- 1 GB RAM
- 25 GB plan disk
- about 13 GB used / 11 GB free on root filesystem
- 4 GB swap configured
- existing Nginx, PostgreSQL, Gunicorn, Python and Node processes
- current load low
- no DigitalOcean backups enabled at audit time

## Use of current Droplet

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

PINK Backend Droplet:
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
