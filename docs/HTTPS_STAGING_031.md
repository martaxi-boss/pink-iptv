# HTTPS staging and Android test build031

## Accepted server evidence

Selected origin: https://pink-iptv.duckdns.org. DNS A146.59.145.3, noAAAA; no DuckDNS token was needed because the existing record already points to the authorized VPS.

Readiness029: implementation99cd1b36a610995441284cdebb58af10d1fd0bff, [run37190404920](https://github.com/martaxi-boss/VPS/actions/runs/37190404920), final52ceb4312a9030c1cf93095b271d5f7ce5863493.

HTTPS030: implementationb28b5930280a37d09d2474c83b6772b4ed933dd7, [run37191347129](https://github.com/martaxi-boss/VPS/actions/runs/37191347129), final176dc5cf0761e905cfd6a519a0b90f4c25663e18. Canonical immutable authorization, append-only recovery ancestry, exact run/same-SHA consistency, result scope and final evidence-only ancestry were independently verified. Earlier failed attempts remain historical; no old-SHA rerun was certified.

Actual accepted host state: isolated `/etc/nginx/sites-available/pink-iptv-https.conf` and matching enabled symlink; HTTP challenge/HTTPS redirect; TLS1.2/1.3 HTTPS proxy to127.0.0.1:8010; access logs disabled for this site;16k request body bound. Certificate is held under `/etc/letsencrypt/live/pink-iptv.duckdns.org`; private key never returned. Existing ACME account and active Certbot timer reused; selected-lineage deploy hook validates Nginx then reloads it. No account/terms/DNS/firewall/backend-source/customer-data/other-site mutation occurred.

The accepted attempt's restricted baseline/candidate receipt is `/var/backups/pink-iptv/task030-https-r4`. Previous protected attempts are retained separately. HTTP site addition/removal/reload restored the exact effective Nginx configuration hash; stable iptables/ip6tables filter/nat/mangle/raw `-S` configuration hash and original service/PG readiness passed. These hashes exclude traffic counters/timestamps. This is site inverse proof, not a full database/network restore or a production certificate-deletion rehearsal.

## Android build and device handoff

Task031 changes only the default PINK_API_BASE_URL to the selected HTTPS origin. Gradle's explicit property override remains available to controlled builds. Customers still enter username/password only; provider per-line dns_link remains backend-resolved and unchanged. The PINK hostname is not the provider DNS.

Existing Android CI lint, JVM tests, build, WireGuard native payload, instrumentation compilation, secret scan and PR emulator checks are preserved. After the secret scan, CI returns `app-debug.apk`, its SHA256 and staging provenance (source head and tested checkout). The artifact is a DEBUG staging build retained for7 days, not release signing, a store publication or public launch. Use the exact certified run/artifact indexed in Task031's final Worker Result; a build must not be accepted solely from this document.

Physical installation/login/catalog/playback/TV/provider checks remain unexecuted here. No customer credentials are embedded, and automated readiness used synthetic invalid input only. No real VPN peer/configuration/tunnel was added. Stage004B identity/permission/read-only adapter remains the implemented VPN boundary; host gateway, enrollment and real tunnel/lifecycle/IPv6/capacity are separately bounded continuation work.

Public launch: NO.
