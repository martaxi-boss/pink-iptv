# Physical UI Recovery059: initial Android service retirement

The accepted055/056 catalog proof used native HTTP and did not exercise WebView rendering. Source059 adds real form submission, Tauri Live TV rows, category-control/main-thread roundtrip and fixed privacy-safe flags before the native decoded AV/recovery proof.

Android13 actual UI proof060 R3 (run37436483182, original diagnostic sourceb9e87cf06c9ae7b1cc76f98156016e8cfa3815e2) reached successful fixed-control DNS, session resolve and enrollment, then failed at activate_tunnel/bind_authorized_route with live=false, bound=false, admitted=false and an encrypted saved grant. The UI checkpoints were not reached. Own disposable peer cleanup and protected before/after audits passed.

The exact official GoBackend1.0.20260102 setState(UP,newConfig) requests stopSelf for the old service, then can reuse its already-completed service future. Its later onDestroy clears whichever tunnel the owner currently holds and reports DOWN. This lifecycle race is consistent with UP succeeding followed by the observed lost authorized route. Exact SDK source: https://github.com/WireGuard/wireguard-android/blob/1.0.20260102/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java

Before initial admission only, explicitly retire the offline backend and await its service shutdown. Android29+ uses the official SDK's public service-future status query; Android26-28 use the documented own-service compatibility query. Retain the old/dead captured process binding throughout the wait; retain the existing initial binding transition immediately before new UP. Timeout fails without admitting any provider flow. After admission, the existing no-cycle/fail-closed lifecycle is unchanged.

Instrumentation verifies old-service retirement before successful native authorization and verifies WebView reauthentication does not cause another peer replacement. All prior native player/root/cold/roaming proof remains required. The physical post-login loading problem is not yet certified fixed: fresh sourceCI, exact Android13 real UI/native proof and the same verified APK are required before a replacement physical handoff.

Incremental hygiene preserves earlier certifying refs, records and active work. No credentials, keys, provider origins, app-wide permissions, server mappings or architecture changes are introduced.

R4 run37440856219 on b029969 passed old-service retirement, authoritative login/catalog through WireGuard and actual WebView username/password login. It failed in the later WebView phase before native AV. The original harness did not identify which JavaScript operation failed; this is not proof that catalog rendering itself failed. Stop awaiting a JavaScript callback from a document being navigated away, and verify the destination checkpoint instead. Add fixed checkpoint/host-WebView-callback-decode/category and native runtime flags on every UI exception; retain message/URL/credential suppression. A fresh exact proof is required to distinguish a harness navigation issue from an app catalog/main-thread defect. Own-peer cleanup/protected before/after audits passed.

R6 run37444313085 on6e053763 had online=true and a responsive WebView; the login form remained after its90second checkpoint. Native old-service retirement and authoritative catalog had passed. Fixed checkpoint login_home/js=decode/ASSERTION plus native stage=session_resolve/failure=SocketException/live=true/bound=true/admitted=true identify a later native fixed-control request failure, not a lost tunnel or proven catalog-render stall. Preserve own-peer cleanup and protected audits PASS. Add fixed control operation boundary, errno, allowlisted socket category, HTTP status and physical-network transport/validation/default/address-family booleans; never print the original message, URLs or account data. Routing, TLS validation, authentication and fail closure remain unchanged until the newly classified evidence establishes the precise cause. A new exact real proof is diagnostic and cannot certify the physical symptom without all UI/AV/recovery checks.

Historical R7 run37448232878 isolated a UI-session DNS failure while the admitted VPN/catalog remained healthy. That experiment routed admitted control through the captured VPN, but later owner-device evidence showed the resulting control path could become circular and stall login recovery. It is retained only as diagnosis history, not current routing authority. The current bounded correction keeps fixed PINK HTTPS control endpoints on an explicitly selected validated non-VPN Android Network so they can create/repair WireGuard, while provider/catalog/media traffic remains process-bound to the captured VPN with no direct fallback. Public hostname, automatic DNS, TLS, credentials, peer identity and fail closure remain unchanged. Official network/binding semantics: https://developer.android.com/reference/android/net/ConnectivityManager#bindProcessToNetwork(android.net.Network) and https://developer.android.com/reference/android/net/Network#openConnection(java.net.URL)


Owner physical evidence on 2026-10-06 after repeated login with multiple valid usernames shows the managed Mega DNS/subdomain resolution is functioning: login succeeds and Live TV catalog/EPG populate. Two distinct client-side symptoms remain visible: the upstream WebView banner reports "offline" while Android still shows the VPN and protected content is present, and live playback can fail with an Unauthorized HTTP status. This narrows the current defect away from Mega DNS resolution.

The PINK overlay had two concrete mismatches with that evidence. First, the complete Extreme client synthesized the nominal Xtream live route (/live/<user>/<pass>/<id>.<ext>). Historical bounded provider diagnostics already proved that this panel accepts catalog authentication but returns HTTP 401 for that synthesized media route, while the authoritative get.php playlist supplies the same-origin legacy /<user>/<pass>/<id> source and that source returns HTTP 200 with MPEG-TS bytes. The current physical Unauthorized screenshot is consistent with that preserved evidence. Second, the generic upstream connectivity banner trusted navigator.onLine, which is not authoritative once PINK owns the app route through Android VpnService/WireGuard.

The bounded correction keeps architecture unchanged: the shared PINK live URL builder now uses the provider-certified extensionless legacy source shape for all existing Extreme live-player/cast callers, while managed account metadata continues to describe the actual TS payload; and the native PINK build suppresses only the generic browser-offline toast when the restricted PinkNative bridge is present. Protected WireGuard readiness/fail-closure, username/password login and authoritative DNS/subDNS remain unchanged. Fresh exact source CI plus Android/UI/native playback proof are required before certification.


## 2026-10-06 physical login-flow recovery

The replacement APK built from e60a724 removed the earlier synthesized live-stream route, but owner-device evidence exposed a separate login/lifecycle defect. After normal Android VPN consent the upstream first-run card still required an unnecessary extra tap. A successful protected login could then navigate away and immediately lose the in-process validated account because the encrypted vault state was revalidated from a fresh route bundle. A subsequent login could wait on the admitted tunnel's own control path and end as the generic temporary-service error.

Recovery keeps the existing security boundary but removes the circular dependency. The fixed PINK HTTPS control plane (/v1/session/resolve, /v1/vpn/enroll, /v1/vpn/refresh) now uses an explicitly selected non-VPN Android Network so it can create or repair the WireGuard data plane even while the process itself is bound to WireGuard. Provider/catalog/media traffic remains process-bound to the captured VPN and retains fail-closed behavior; there is no direct provider/media fallback. Switching IPTV accounts keeps the same device identity while renewing the existing installation lease for the newly authenticated account; the tunnel is not replaced.

The encrypted account remains the only persistent credential store. A just-validated account is additionally retained only in native process memory, shared across PinkVault/PinkWebBridge recreation and Astro route-module reloads, preventing a successful login from immediately causing a second backend/enrollment round trip. No credential copy is written to localStorage. Cold process start still revalidates the encrypted account before granting access.

The obsolete first-run WelcomeCard is replaced by an automatic redirect to the PINK login route. The non-sensitive selectedId metadata already mirrored locally is used only to avoid falsely presenting first-run UI when an encrypted account exists. No username, password or provider origin is mirrored to localStorage.

## Home redirect correction after owner video

The owner video shows a successful submission navigating briefly to Home and then returning to an empty login form. Inspection of source e5badb588333545c1beb599800cebc8b71bba16b found an unconditional inline `location.replace("/login")` in WelcomeCard. Astro includes that component in Home even when its parent section is hidden; hiding the component does not prevent its script from executing. Thus the page redirected independently of the validated account, including after a successful protected login.

Remove that inline script entirely. The existing Home reconciliation now redirects only after `getEntries()` has finished reading/validating the protected account and returns an empty list. An authenticated account remains on Home; a fresh or unvalidated account still opens login automatically. The selectedId mirror remains presentation metadata only. Native routing, encrypted storage, and cold-start validation are unchanged. Generated-page regression tests cover authenticated Home, empty-account redirect, and delayed protected validation, and are included in Android CI. Exact fresh CI and live UI/playback validation are still required; source inspection does not certify the owner device.

### Exact Android13 proof after Home correction

Source `7e8ad3aa` passed Android042 and Backend CI. Operational run
`37479264378` passed authoritative VPN login/catalog and retained protected
runtime/source identity before and after disposable peer removal. Actual WebView
checkpoint `login_home` failed with the form still present; native runtime was
ready with no recorded failure. This does not certify actual UI or native AV.

The next bounded diagnostic exposes only fixed login transaction phases
(authenticating, loading_account, saving_account, navigating) and fixed route
categories. It never returns form values, status/error text, URLs, account blobs
or credentials. Six transaction tests cover save-before-navigation, each failure
phase, duplicate submission and disposed form completion. The 90-second actual
UI checkpoint stays unchanged; no same-source proof rerun is authorized.

### Proof R10 navigation race remediation

Run37483877077 reached native authenticated VPN/catalog PASS but timed out at
login_home. Its new fixed flags show route `/login`, phase `idle`, no failure and
submit enabled, with native stage ready and failure none. The final document
had not retained the submission. Protected host audits and own-peer cleanup PASS.

The harness navigated asynchronously to `/login` and accepted any existing form,
including one from the previous login document before navigation committed.
Mark the outgoing document with a transient, non-sensitive flag and wait for the
replacement document at the intended route with that flag absent, before filling
or submitting its form. Do not await a callback from the disposed document.
Generated Kotlin-script regression tests reject the previous same-route document
and require the destination path. This fixes a concrete test race; fresh exact
actual UI/AV/recovery proof remains required to certify the app behavior.

### Immutable certification061 continuation

Canonical audit found malformed historical059 recovery event2: its predecessor
hash is the raw-byte SHA256 instead of the canonical JSON digest, and its
FAILURE_OBSERVED event increments strategy generation without REPLAN. Preserve
the original task and both event files unchanged. They cannot certify059 success.
Record059 as STALE_EXECUTION_PACKET superseded by independently authorized061.

061 adopts corrected app/harness sourceea1770a9, whose65 tests and exact Android
run37485188411/Backend37485188172 passed. The new authorization-only record
precedes061 audit/closure implementation. Source CI verifies061 record immutability,
scope and unchanged059 history. Fresh exact source CI and separate060 actual UI,
native AV and recovery evidence remain required before certification/handoff.
No historical event is repaired retrospectively and no control is weakened.

061 also makes the actual proof assert that submitting the replacement form
immediately enters the existing authenticating/busy state. Any later UI failure
reports only native account-cache category VALID/EMPTY/MALFORMED and encrypted
account presence, alongside existing fixed phase/route flags. No account values,
credentials, origin, raw errors or blob are returned.

## Direct technical continuation: protected native Live TV catalog

The source at `03cdfdd` retained repeated `/livetv`, complete-document,
`pulling_next` failures while the Android main queue and admitted WireGuard
route remained responsive. Its latest operational proof failed earlier at the
native authoritative catalog and did not identify HTTP status, empty data,
parsing or transport failure. The bounded test now reports those fixed
categories without URLs, credentials, response contents or exception messages.

The functional correction uses the existing origin-restricted native bridge for
only `get_live_categories` and `get_live_streams`. Native code derives the
provider origin and credentials from the process-validated encrypted account;
the page submits only the fixed action and selected account ID. Reads explicitly
use the current owned VPN Network, reject redirects and non-array responses,
and retain bounded size and read deadlines. No physical/provider fallback is
introduced. Catalog I/O uses its own serial worker so it cannot queue readiness,
vault or control requests behind a body read. Response serialization happens
outside the Android main thread. Extreme still renders categories/channels and
handles the actual channel click and playback.

The ineffective Vite HTTP body IPC observer and its diagnostic-only tests are
removed with the substituted Live TV transport. Product admission, account,
origin, UI, presented-video/audio, cold-start, network-recovery and own-peer
cleanup assertions remain. Source CI and the direct exact-artifact functional
proof must pass before this correction can be described as certified.

### Current direct execution: physical HTTPS selection

The exact 81728bde proof reached system consent but failed on the selected cellular control network at response headers (SocketException/ABORT); the current real account returned SUCCESS from the same fixed HTTPS endpoint on the server. DNS and Android VALIDATED alone therefore do not qualify a physical control route. Candidate selection now requires a credential-free HEAD on the POST-only session endpoint to return 405 with normal TLS verification, within fixed four-second connect/read limits, before sending a control POST. This probe is restricted to PINK control; provider/catalog/media remain on the owned VPN network.

The Live TV catalog now arrives through the native vault/VPN bridge. Removed the superseded binary chunk decoder and its decode-size/pull observers and observer-only tests; retained actual catalogue, login, row-click, presented-video and audio assertions. The local frontend/regression suite has 75 passing tests. Functional certification remains pending a new exact-artifact proof.

The c69c5616 exact proof passed session resolve and enrollment but timed out at protected health with the correct owned capture address. Comparing 77793b0 through current source showed no changed health deadline or predicate. Health now opens on the explicitly owned VPN Network, including before admission, instead of relying on a default HTTP network factory across offline-TUN replacement. Failure-only aggregate transfer booleans answer whether protected traffic returned at all if this route correction fails; no observer, key, endpoint or raw statistics are exposed. The four-second health deadlines and admission predicate remain unchanged.
