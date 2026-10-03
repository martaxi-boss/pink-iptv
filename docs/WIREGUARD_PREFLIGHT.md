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

## Current capability gap

The active session can read GitHub repositories/actions and create bounded repository changes, but has no authenticated SSH execution capability or GitHub workflow-dispatch tool. Shell GitHub writes are unauthenticated; connector writes work. The existing VPS workflows expose no generic command input and their dispatch is not available through the current connector. The Owner subsequently executed the accepted read-only collector and two follow-up reads on OVH, supplying terminal screenshots. Sanitized observations and their limits are recorded in [OVH observations017](OVH_OBSERVATIONS_017.md). Service/listener/firewall-inventory evidence is now Owner-observed; runtime revision, restorable baseline, restore rehearsal and real tunnel evidence remain unverified. Direct agent SSH/dispatch access remains unavailable. Do not route around project isolation by editing VPS or creating a new infrastructure/secret trust path.

The next irreducible runtime step is execution of this exact read-only collector plus the sanitized privileged network/rollback audit through an available authorized OVH execution channel. A human/capability handoff must name that concrete requirement, not ask generically for permission to continue the project. Physical-TV and real-provider proof also remain separate environment-dependent validation items.

## Historical issue classification

Open issue1 preserves the old Foundation architecture-review request; merged Foundation/Android/004A and accepted004B are live implementation evidence, so that old request is not an instruction to rebuild them. Open issue21 preserves the earlier premature-CI-gate observation in the Project Leader control plane; current canonical runtime requires terminal exact-implementation CI and this audit waited for Android37153153758 before integration. Neither issue creates a new PINK implementation order or overrides current task authority. Their original bodies remain intact.
