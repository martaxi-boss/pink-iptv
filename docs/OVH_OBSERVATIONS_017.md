# OVH preflight observations and rollback preparation 017

## Provenance and acceptance boundary

Source: three Owner-supplied terminal screenshots from the existing authorized OVH host. This is transcribed Owner-observed evidence, not direct agent SSH inspection. The first collector identifies observation UTC as 2026-10-03T23:13:08Z; the second read prints 2026-10-03 23:17:24 UTC. The final screenshot was supplied afterwards without a fresh UTC marker, so these readings are not one atomic snapshot. The collector was pinned to accepted implementation 358f0e88237d4b7721135ef9b4d1a6befc35a335. No screenshot, SSH login address, IP, credentials, raw config, private keys, logs or process arguments are persisted here.

Canonical target main at reconciliation:173e0e5293057c5540233b19253f1cfded1baf80. Control source:a061a17cb9a70e015672188eef10a5e0a73fba8b. A GitHub code revision is not evidence of the deployed runtime revision.

## Observed facts

| Surface | Screenshot observation | Limit |
| --- | --- | --- |
| Kernel | 6.8.0-136-generic | Login banner also requested a restart; no upgrade/reboot is ordered or performed |
| Services | SSH, Nginx, PostgreSQL and pink-iptv-backend.service active | Active alone is not end-to-end provider readiness |
| Alternate unit | pink-iptv.service inactive | The effective named backend unit is active; this does not independently show a failure |
| Backend | TCP8010 loopback only; GET /openapi.json returns HTTP200 | API surface response, not authentication/playback or DB transaction proof |
| PostgreSQL | Loopback5432 accepting connections | No database records or credentials read; no restore proof |
| Proof unit | pink-tv-proof-final-isolated.service active/running, transient, entered active2026-09-25T00:53:19Z | Purpose, ownership, executable and relevance are not established; not certified residue |
| Proof resource | MemoryCurrent6033256448 bytes (approximately5.6GiB) | systemd cgroup accounting includes descendants/cache; not necessarily process RSS or a capacity test |
| Backend resource | MemoryCurrent59076608 bytes | Point-in-time cgroup reading only |
| Backend path | /srv/pink-iptv/backend | Plain git revision read returned UNKNOWN; permission/ownership/repository layout may explain it |
| IPv4 filter | INPUT DROP, FORWARD DROP, OUTPUT ACCEPT;67 rules | Only policies/counts were collected; individual allow rules not audited |
| IPv6 filter | INPUT DROP, FORWARD DROP, OUTPUT ACCEPT;107 rules | Same structural-only limit |
| IPv4 NAT | Default policies ACCEPT;0 rules | Not proof of all IPv6/native-nft/provider network state |
| nftables |3 tables,70 chains,175 rules | May overlap iptables compatibility/UFW views; do not add counts as independent rules |
| UFW | active | Preserve current manager and rules; no flush/reset or mixing unmanaged rules by assumption |
| Default IPv4 egress | ens3 | Routing selection can change; must be revalidated before use |
| Forwarding | IPv4=0, IPv6=0 | No forwarding changes performed |
| Candidate UDP |51820 has no listener observed | Neither reservation nor provider ingress/reachability proof |
| Tools | wg, nft and iptables present; TUN device present | No configured interface, peers, handshake or tunnel certification follows |
| Resources | Root free45147488KB; MemAvailable4282892KB in first reading | Distinct snapshot from later proof cgroup memory; no concurrency/throughput claim |

## Supervisor outcome

Partial service and structural network preflight accepted with the above provenance. WireGuard network activation remains blocked on concrete prerequisites, not a request for generic project permission. Do not remove the transient proof unit based on age/name alone. Preserve all existing service and network behavior.

## Bounded rollback design

Before any network write:
1. Identify backend runtime revision without dumping environment/ExecStart; use a permitted privileged Git read if the directory is a trusted repository. If Git metadata is absent, use an explicit deployed-artifact manifest comparison. UNKNOWN must not be replaced by main.
2. Identify the proof unit using process executable names and cgroup/service metadata without credential-bearing arguments; establish whether it remains needed before any separate cleanup task.
3. Establish who owns IPv4/IPv6 firewall state (active UFW plus native/compatibility nft structure), routing/address ranges and provider ingress. Counts alone cannot bind exact inverse rules.
4. Preserve a restricted runtime restore baseline on the host for exactly the files/rules/forwarding values to be touched, plus an existing-service verification baseline. Secret-containing material remains in the VPS store, never public GitHub or chat. GitHub stores code, non-secret reproducible instructions and sanitized evidence. No paid snapshot is enabled.
5. Define additive PINK-specific changes with exact interface/port/address ranges and identifiers; never flush shared tables, reset UFW, change global DROP policies or overwrite an existing WireGuard configuration.
6. Rollback removes only those exact task-owned additions and restores only values changed by that task to their audited baseline. Do not replay an old whole-firewall snapshot over unrelated concurrent changes.
7. Review and rehearse the inverse path in isolation, then revalidate host services/loopback API/PostgreSQL/SSH and network baseline. No successful rollback is asserted until this evidence exists.
8. Only after these controls can a separate bounded task bind one test peer and actual Android tunnel work. Windows, production onboarding and public launch are unaffected.

## Next execution boundary

The remaining immediate reads are a privileged trusted runtime revision query, sanitized process identity for the proof service, and WireGuard interface inventory (no keys/peers). Required privilege/access or absent deployment metadata remains explicit. The agent still lacks authenticated SSH/dispatch capability; Owner terminal execution is the current channel. All actual runtime/network/configuration mutations require a separately bounded task and exact applicable transition controls.
