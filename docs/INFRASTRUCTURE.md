# Infrastructure Plan

## Current development/staging baseline

Audited 2026-09-24:

- Provider: OVHcloud
- Host: `vps-32bea5b6`
- Ubuntu 24.04 LTS
- 4 vCores
- approximately 8 GB RAM
- approximately 72 GB root filesystem
- KVM available for Android emulator validation
- PINK backend bound to `127.0.0.1:8010`

The previous DigitalOcean 1 vCPU / 1 GB RAM / 25 GB host is historical only and is no longer an operational dependency or resource constraint for PINK IPTV development.

## Allowed current use

The OVH host is approved for:

- backend development/staging;
- Android build and emulator validation;
- Mega integration development/testing under existing security controls;
- integration tests;
- future POC work only when separately authorized.

Public launch remains NO.

## Production architecture

PINK Backend / control plane:

- HTTPS API;
- PostgreSQL;
- Mega adapter;
- app configuration;
- future VPN control-plane functions when authorized.

A dedicated VPN gateway remains the production design target when real capacity/isolation evidence justifies separation. Development co-location does not prove production capacity.

Production VPN capacity, throughput and user/stream limits must be measured before any production claim. The current 4 vCore / 8 GB OVH host must not be translated into an assumed number of concurrent streams without benchmark evidence.

## Build and operational access

Builds and CI use GitHub-hosted runners. The previously available OVH KVM capability is historical infrastructure evidence, not a requirement to run a self-hosted runner or Remote Desktop Commander. Infrastructure access is through the authorized GitHub workflow/terminal path.

Code and reproducible backups belong in GitHub. Runtime credentials remain in the VPS secret store and must never enter repository backups. This task does not activate paid provider backups or assert a fresh VPS audit.

## Deployment

GitHub remains source of truth. Deploy only approved commits. Secrets remain in runtime environment/secret storage. The current backend bind, firewall, routing, Nginx and systemd configuration must not be changed without the corresponding authorized order.

## Backups and infrastructure mutation

Before material infrastructure/network mutation, establish a recoverable snapshot/rollback plan. Database backups must be automated before production launch. WireGuard, firewall forwarding/NAT and production VPN configuration remain outside Android Shell 002.
