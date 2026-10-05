# New Extreme/PINK WireGuard integration

Target: pinned complete Extreme client with deterministic PINK overlays. The old
Compose product is read-only reference; no old UI is transported.

An installation generates its own WireGuard identity, encrypts the private key
with a dedicated Android Keystore wrapping key and keeps it out of WebView/JS,
backend, logs and corresponding source. IPTV logout preserves that identity.

On first use Android presents its normal VpnService consent. Only the PINK
username/password login is available before account authentication. The existing
session resolver preserves Task050 exact Mega-provided dns_link behavior. Its
short-lived signed PINK session authorizes initial public-key enrollment. No
anonymous peer and no global APK secret is permitted. After enrollment the
installation credential can restore the tunnel before IPTV login on later
starts. A fresh authenticated account renews a maximum 24-hour installation
grant. Refresh never extends that grant. The backend stores only a token digest,
public key, address, mapping reference and lease/revocation timestamps.

Enrollment/revalidation uses a dedicated native HTTPS control path restricted
to the selected PINK backend. No provider request may use that control channel.
Provider traffic is released only after tunnel UP and an in-tunnel gateway probe.
Only the PINK package is included in the Android tunnel; both IP families are
captured. IPv6 is captured and blocked until gateway IPv6 routing is certified.
External player/cast handoff cannot be considered protected by an app-scoped VPN
and must be disabled in the VPN-required build.

Backend operations use a bounded Unix socket with no shell invocation. A separate
VPS task must implement root-owned peer management, authenticated local callers,
public-key/address validation, persistent server identity and additive network
rollback. Each gateway peer authorization lasts at most five minutes and is
renewed by client refresh; a gateway control failure never reports enrollment
success. Revocation is durable before gateway removal; an unavailable control
channel returns 503, with the gateway short lease as the expiry bound.

The interface remains established during handshake/network failure, so the
required IPTV route does not switch to direct access. Android service revocation
or replacement by another VPN requires immediate application traffic shutdown.
System-wide Android lockdown is user-controlled and must never be silently set
or claimed. Real service-loss, reconnect, IPv6, native/Rust/WebView traffic and
handshake/leak behavior require independent automated device evidence before
certifying the final APK. This document describes architecture, not PASS evidence.

Task051 implements only backend enrollment. Gateway/runtime deployment and the
new Android host integration are separately bounded tasks with exact CI and
transition records. Public launch remains outside this staging continuation.
