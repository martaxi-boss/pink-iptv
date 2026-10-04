# HTTPS staging plan and convergence preflight 027

## Current evidence

PINK starting main:0bc2b751c77da5702d8cfb24996d046e5f1829fc. Canonical control:05225695684b54ce5becbb89cf0e5b6dc88dcafa, Project Leader0.6.2. Task027 prepared and certified this document on an isolated PINK branch (implementation2c3dd17b82e00c05acf372c08ccd724109780ccf, Backend CI37176508272 SUCCESS). Task028 integrates the reviewed plan with current observations; no VPS write follows from this document.

Native Actions SSH access is recovered. Task024 fixed the existing staging validation-input echo using only the approved source handler, a protected original-file backup and exact inverse. PINK reconciliation PR30/task023 was merged at cb842e86aa1f547d721231e366fbcb0fc50f7ad1 after its exact-SHA Android/Backend CI and transition audit.

| Latest bounded observation | Evidence and limit |
| --- | --- |
| WireGuard handshake/inverse025 | [run37175796253](https://github.com/martaxi-boss/VPS/actions/runs/37175796253), implementation374ef9254758400ee6db191d5d180afb2fdbb2eb, SUCCESS at2026-10-04T04:02:16Z. Two isolated namespaces exchanged tunnel traffic; task namespaces/interfaces/ephemeral keys removed; host firewall/forwarding and service readiness unchanged. Not Android/external-ingress/production proof. |
| UFW and HTTPS exposure026 | [run37176105887](https://github.com/martaxi-boss/VPS/actions/runs/37176105887), implementation612ffb628d1d3ffb266c482b77cce2cfd72e5835, SUCCESS at2026-10-04T04:08:11Z. Both frontends use nf_tables;32 ufw IPv4 chains,31 ufw6 IPv6 chains; UFW IPV6=yes. Earlier zero count excluded ufw6 names and did not establish absent IPv6 ownership. |
| PINK Nginx path026 | Config test PASS;0 known literal loopback8010 proxy blocks and0 matching upstream aliases. No PINK backend hostname/TLS path discovered. Parser does not prove absence of every dynamic/rewrite proxy. Raw configs and other-project hostnames were not returned. |
| Android endpoint | Build default remains https://pink-api.invalid. CI without a real endpoint does not produce a usable customer/backend-connected build. BackendSessionClient requires HTTPS and valid TLS. |

Runtime credentials and provider dns_link are unrelated to the public PINK backend identity. Neither may be used as a guessed backend domain. Customer UI remains username/password only; no DNS/portal field is added.

## Concrete next execution plan

The unresolved input is an Owner-controlled hostname for the PINK staging backend and its existing DNS control path. No hostname acquisition or unrelated-project hostname reuse is authorized or discovered. Use the existing OVH host; no new server/subscription is required.

After identity resolution, create a separate bounded VPS task and revalidate exact service/config/network state:

1. Confirm DNS points to the authorized host. Preserve restricted baseline and ownership metadata for only the proposed PINK Nginx site, enablement symlink, selected certificate material and exact task-owned ingress additions. Fail on an existing conflicting path; never overwrite another site or shared certificate. Backend stays loopback127.0.0.1:8010.
2. Obtain a publicly trusted certificate through an authorized DNS/ACME path. Resolve any account/terms/credential requirements before execution. Keep private keys on the host with restricted permissions; no self-signed fallback or disabled client verification.
3. Validate the separate PINK site before enablement. Use the reviewed template below with the exact selected hostname and certificate paths. Preserve all other sites. HTTP redirects to HTTPS except a bounded certificate challenge path if that validation method is chosen.
4. Run nginx configuration validation before reload. Persist exact transition authority; enable/reload only after passing controls, then verify original services and local/public HTTPS readiness. Probe only synthetic invalid requests and require generic422/no-store/non-echo. No customer/provider credential is needed.
5. If ingress changes are necessary, use only exact PINK-owned UFW additions under the audited IPv4/IPv6 manager with an exact inverse. Never flush/reset, alter global policies or replay whole-firewall snapshots. Do not add AAAA without intentional IPv6 reachability/TLS verification.
6. Build a separate staging APK embedding this HTTPS origin through PINK_API_BASE_URL, record source/hash/test evidence and prepare a bounded device-test handoff. No provider URL/customer credential/server key is embedded. No production promotion, release signing or public launch is implied.

Reviewed template (unresolved placeholders; not deployed):

```nginx
server {
    listen 443 ssl;
    server_name __OWNER_CONTROLLED_PINK_HOSTNAME__;
    ssl_certificate /etc/letsencrypt/live/__OWNER_CONTROLLED_PINK_HOSTNAME__/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/__OWNER_CONTROLLED_PINK_HOSTNAME__/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 16k;
    access_log off;
    location / {
        proxy_pass http://127.0.0.1:8010;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
        proxy_redirect off;
    }
}
```

Existing provider behavior is preserved. Operational logs must not include bodies or credentials; return only sanitized diagnostics.

## Exact inverse and limits

On failed candidate validation, do not enable/reload it. After enablement, remove only recorded task-owned site/symlink/ingress additions, validate nginx, reload the restored baseline and verify original services/API. Certificate/account/key retention/removal must be bound to the selected ACME method; never delete shared state. This prepared inverse is not executed rollback evidence.

Task024 source backup is an exact-file privacy inverse. Task025 proves namespace-object cleanup. Neither certifies host gateway NAT/forwarding or database restoration. Real peer enrollment, Android tunnel/lifecycle/DNS/IPv6/capacity and physical-TV/provider tests remain separately bounded.

## Human Gate closure

FORCED_OPERATIONAL_ACCESS_DISCOVERY covered direct/native inventory, target/adjacent operations and historical evidence. Native Actions SSH works; no browser/manual-terminal gate is asserted. Native OVH/DNS management capability is absent. PINK build history contains the invalid placeholder, with no selected public backend hostname; PINK-only Nginx audit returned no origin. Prior-decision retrieval did not establish a hostname/TLS/DNS grant. No private conversation is copied here.

Choosing/acquiring/reassigning the public backend hostname and DNS ownership is NEW_UNCOVERED_MATERIAL_DECISION. The concrete Owner input is the intended domain/subdomain and existing DNS management path, without credential values. Standing authority does not cover guessing a provider domain, reusing another project's identity, adding a paid service or introducing a third-repository proxy.

Once that input is resolved, continue the bounded HTTPS task automatically. Physical-device testing and Android system VPN consent remain exclusive human interactions only when they become the next irreducible validation action. This gate concerns external identity/DNS authority, not command execution.
