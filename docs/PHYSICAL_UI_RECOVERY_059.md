# Physical UI Recovery059: initial Android service retirement

The accepted055/056 catalog proof used native HTTP and did not exercise WebView rendering. Source059 adds real form submission, Tauri Live TV rows, category-control/main-thread roundtrip and fixed privacy-safe flags before the native decoded AV/recovery proof.

Android13 actual UI proof060 R3 (run37436483182, original diagnostic sourceb9e87cf06c9ae7b1cc76f98156016e8cfa3815e2) reached successful fixed-control DNS, session resolve and enrollment, then failed at activate_tunnel/bind_authorized_route with live=false, bound=false, admitted=false and an encrypted saved grant. The UI checkpoints were not reached. Own disposable peer cleanup and protected before/after audits passed.

The exact official GoBackend1.0.20260102 setState(UP,newConfig) requests stopSelf for the old service, then can reuse its already-completed service future. Its later onDestroy clears whichever tunnel the owner currently holds and reports DOWN. This lifecycle race is consistent with UP succeeding followed by the observed lost authorized route. Exact SDK source: https://github.com/WireGuard/wireguard-android/blob/1.0.20260102/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java

Before initial admission only, explicitly retire the offline backend and await its service shutdown. Android29+ uses the official SDK's public service-future status query; Android26-28 use the documented own-service compatibility query. Retain the old/dead captured process binding throughout the wait; retain the existing initial binding transition immediately before new UP. Timeout fails without admitting any provider flow. After admission, the existing no-cycle/fail-closed lifecycle is unchanged.

Instrumentation verifies old-service retirement before successful native authorization and verifies WebView reauthentication does not cause another peer replacement. All prior native player/root/cold/roaming proof remains required. The physical post-login loading problem is not yet certified fixed: fresh sourceCI, exact Android13 real UI/native proof and the same verified APK are required before a replacement physical handoff.

Incremental hygiene preserves earlier certifying refs, records and active work. No credentials, keys, provider origins, app-wide permissions, server mappings or architecture changes are introduced.
