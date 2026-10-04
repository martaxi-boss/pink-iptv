# WireGuard Phase4 service and rollback preflight

Purpose: collect current OVH facts before server configuration, test-peer enrollment or real Android tunnel activation. This packet does not configure a VPN and does not prove connectivity, throughput or production capacity.

## Execution boundary

Run `bash infra/scripts/wireguard-preflight.sh` from the exact accepted repository revision on the authorized OVH host. The optional `PINK_PREFLIGHT_UDP_PORT` parameter is an inventory candidate only, not a selected/approved server port. The default candidate is51820. The script is read-only, emits stdout and requires no credentials as arguments. It reads no configs, private keys, environment secrets or journal bodies. It does not install packages, obtain root, restart services, alter firewall/NAT/forwarding, create peers, copy backups or delete files.

Collection can be performed through the Owner's authorized workflow/terminal path. Do not put an SSH password in the repository, shell arguments, report or chat. This PINK task cannot edit the separate VPS repository or assume its secret is available in PINK. Existing VPS access-check/residue workflows do not accept arbitrary commands and do not execute this collector.

## Evidence required before network mutation

| Check | Required evidence | Failure route |
| --- | --- | --- |
| Host/time/revision | Exact authorized OVH target, observation UTC and collector Git SHA | Reconstruct the target; reject local/emulator output as OVH proof |
| Existing services | SSH, Nginx, PostgreSQL and actual PINK service names/state | Identify units and resolve degradation before networking changes |
| Backend reachability | Loopback8010 listener plus approved application-level readiness check | A listener alone is not API readiness; obtain actual service proof |
| Existing network | Current forwarding, TUN support, listener inventory and privileged read-only firewall/routing audit | Unknown or unreadable facts remain unverified |
| Candidate UDP port | Current no-listener observation, provider/network compatibility | Free-at-observation does not reserve or select a port |
| Rollback baseline | Restorable configuration/database/runtime baseline in the existing no-paid-backup policy | Never use a Git code backup as proof that runtime state is restorable |
| Rollback rehearsal | Exact restore steps, target revisions and verification of existing service/network recovery | No server mutation until an executable reviewed restore path exists |
| Test peer | One explicit bounded peer/address/revocation plan; device-owned keys | No shared key embedded in APK, no production onboarding |
| Real tunnel | Lifecycle/foreground service, routing/DNS/IPv6/network-change behavior and kill/safety policy | No ON/CONNECTED claim without real tunnel proof |
| Capacity | Representative measured throughput/resources and service impact | Hardware specifications do not prove user/stream limits |

The collector deliberately does not dump firewall rules, routing contents, database records, unit ExecStart, service environment or WireGuard configuration. A later authorized privileged audit must provide sanitized structural facts for the network/rollback review. Current packet cannot certify firewall completeness or rollback merely from resource and listener output.

## Rollback construction requirements

Before any network write, Supervisor must bind the exact existing configuration and restore evidence. Preserve existing service units, Nginx and PostgreSQL, runtime secret store, SSH access, backend bind, routing, firewall and forwarding values. Determine which facts/configuration components actually change; define inverse steps for only those changes. Preserve runtime secrets on the VPS and exclude them from Git backups/reports. Do not activate paid provider snapshots/backups.

The rollback runbook must be executable in the approved environment, restore the identified baseline and include post-restore service/readiness/network checks. No concrete command to restore unknown firewall rules, database content or runtime configs can truthfully be selected before that baseline is inspected. This preparation is not a completed backup or a tested rollback.

## Current operational path

The historical Owner screenshots remain in [OVH observations017](OVH_OBSERVATIONS_017.md). Current native capability discovery and separate VPS tasks021/022 established an executable GitHub Actions SSH bridge using the existing authorized secret name and fixed host. The repository is now public; fresh execution succeeded after earlier account billing/spending pre-step rejection. Direct session SSH and workflow-dispatch remain absent, but do not prevent bounded push-triggered repository-return diagnostics. This PINK task cannot mutate VPS; use a separate operations task with immutable authorization and exact consequential transition records.

Current runtime revision, process identity, WireGuard inventory and safety facts are recorded in [runtime audit023](OVH_RUNTIME_AUDIT_023.md), including the separate task024 staging privacy overlay and protected original source backup. Follow-up tasks025/026 established UFW/ufw6 ownership and passed an isolated namespace handshake/cleanup rehearsal. Remaining work is the exact host gateway additive inverse/baseline, external ingress and real peer/tunnel validation. The public backend identity is now selected and trusted HTTPS staging was provisioned/certified by separate task030 at `https://pink-iptv.duckdns.org`; see [staging031](HTTPS_STAGING_031.md) and [historical plan027](HTTPS_STAGING_PLAN_027.md). Task031 binds the Android endpoint/build; its CI does not certify physical-device/provider or real VPN ingress. Source-file backup is not network/database rollback certification. Continue covered audit/remediation automatically through the recovered path; physical-TV and real-provider proof remain separate environment-dependent requirements.

## Historical issue classification

Open issue1 preserves the old Foundation architecture-review request; merged Foundation/Android/004A and accepted004B are live implementation evidence, so that old request is not an instruction to rebuild them. Open issue21 preserves the earlier premature-CI-gate observation in the Project Leader control plane; current canonical runtime requires terminal exact-implementation CI and this audit waited for Android37153153758 before integration. Neither issue creates a new PINK implementation order or overrides current task authority. Their original bodies remain intact.
