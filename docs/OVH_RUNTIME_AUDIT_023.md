# Current OVH runtime audit and continuation 023

## Provenance

Control contract: Project Leader05225695684b54ce5becbb89cf0e5b6dc88dcafa (skill0.6.2). PINK starting main:0bc2b751c77da5702d8cfb24996d046e5f1829fc. This audit reconciles live GitHub-return evidence from separately bounded VPS tasks; it does not mutate VPS under a PINK authorization.

| Task | Exact implementation | GitHub Actions evidence |
| --- | --- | --- |
| Runtime observation021 | de6b6bd2545bab7407497d86b6894367eac6a68c | [run37174951897](https://github.com/martaxi-boss/VPS/actions/runs/37174951897), job111355541793, SUCCESS |
| Runtime safety022 | b8a577e48dc495769f847592897aec4db6bec45b | [run37175238583](https://github.com/martaxi-boss/VPS/actions/runs/37175238583), job111356388399, SUCCESS |
| Staging privacy repair024 | 9dc6405e93aeb07037e48ec3412a30705415d474 | [run37175467182](https://github.com/martaxi-boss/VPS/actions/runs/37175467182), job111357052999, SUCCESS |

Sanitized observations and exact transition receipts reside on the respective isolated VPS task branches. Temporary workflows were not merged to VPS main. Authorization integrity, scope, exact-SHA CI and evidence descendants are audited using canonical validators and the public GitHub API; transport omits bearer authentication for public reads without changing validation rules.

## Access recovery

The original task018 check annotation explicitly attributed pre-step rejection to failed account payments or a spending limit. Those runs never attempted SSH. The Owner changed VPS repository visibility to public; fresh task021 then started, authenticated using the existing authorized repository secret name and returned fixed read-only host observations. No billing activation was performed.

Tasks018/019/020 retain their terminal historical records. Their earlier browser/manual-access conclusions are superseded for current continuation. Native repository-return Actions is now an executable access path. Missing direct session SSH or workflow-dispatch is not a reason to ask for terminal copy/paste. Each future VPS mutation requires its own bounded operations task and exact transition authority.

## Observed runtime and preservation

Observation021 UTC:2026-10-04T03:45:16Z. SSH, Nginx and pink-iptv-backend are active/running. PostgreSQL's umbrella service is active/exited and the actual loopback5432 readiness query accepts connections. Backend loopback8010 OpenAPI returns200. These are service/API readiness facts, not provider or playback certification.

Installed Git HEAD is ccccaf1eb25ca80936763f7d6a17e4dd00347e8a, a historical Android Shell device-proof commit. Task022 found tracked backend source clean at that revision. Comparison with approved PINK main shows backend changes limited to the validation handler and two test files; application Git HEAD is not current main.

The transient proof unit is a headless Android QEMU emulator, with a netsimd child, active since2026-09-25. Its cgroup MemoryCurrent was6020632576 bytes; the main process RSS was2932636KiB. These are different accounting measures. Executable identity does not establish that the emulator is obsolete or safe to remove; it remains running.

WireGuard interface count is0. IPv4 and IPv6 forwarding are0. Default IPv4 egress is ens3. Filter policies remain INPUT/FORWARD DROP and OUTPUT ACCEPT, with67 IPv4 and107 IPv6 rules; both NAT inventories have0 rules. UFW is active. Native nft has3 tables,70 chains and175 rules; compatibility views overlap and must not be added together. Task022 counted32 named IPv4 ufw chains and0 in the queried IPv6 filter view; IPv6 manager ownership remains unresolved.

Candidate10.66.0.0/24 had no overlap with host IPv4 addresses/routes (8 entries) at2026-10-04T03:51:09Z. Candidate UDP51820 had0 listeners, and the proposed PINK-specific configuration path did not exist. Neither observation reserves a port/range, proves provider ingress or certifies IPv6/client routing.

## Corrected staging privacy defect

Task022 sent only a synthetic malformed request containing a public diagnostic marker. The old installed handler returned422 while echoing that marker and omitting Cache-Control:no-store. No customer/provider credential was used or disclosed.

Task024 applied only backend/app/main.py from already approved main0bc2b751c77da5702d8cfb24996d046e5f1829fc. Before replacement it required the exact original hash and runtime/service target, preserved original source/mode/owner in a root-only task backup and rehearsed the file inverse by hash. The fixed workflow atomically replaced the source, restarted only pink-iptv-backend, and bound a failure trap to restoration of that exact original file.

At2026-10-04T03:55:39Z, the synthetic response was422 with generic detail, marker echo:NO and no-store:YES. OpenAPI remained200, PostgreSQL was ready, and the audited existing services remained active. No database, configuration, credentials, networking, peer or tunnel changed.

| Source state | SHA-256 |
| --- | --- |
| Protected original backend/app/main.py | db106b81703c681fbdc6f8ee030ce9a4d122945dbc8355702059450ead3aa09d |
| Deployed approved privacy overlay | 3e060d0bb583c56515a9074eece55f16ad3ec2b8e8b6e82d8de40c462954ca44 |

Runtime Git HEAD deliberately remains ccccaf1; source-overlay provenance is separate. This is not a full-main deployment. Protected source backup/file-inverse rehearsal does not certify database restore, live rollback or future network rollback.

## Next bounded work

Continue the remaining network/rollback audit through the recovered native operations path: identify actual IPv4/IPv6 firewall management, bind exact additive PINK changes and their inverse, preserve a restricted baseline for only affected files/values, and rehearse in isolation before activating one test peer. Preserve global DROP policies, unrelated services and concurrent rules; never flush/reset or replay whole-firewall snapshots.

Real provider Series/EPG/Catch Up/playback, physical-TV validation, Android user VPN permission, real tunnel lifecycle/routing/DNS/IPv6 safety and measured capacity remain separate evidence requirements. No connected/production/readiness claim follows from this audit. Windows remains later; public launch:NO.
