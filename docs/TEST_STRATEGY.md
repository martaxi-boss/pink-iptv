# Test Strategy

## Automated CI proof

CI uses Python 3.12 and a real PostgreSQL service. From a fresh database it runs:

1. dependency installation from pinned project requirements;
2. Ruff lint;
3. Ruff format check;
4. `alembic upgrade head`;
5. schema assertions against PostgreSQL;
6. automated pytest suite with no live Mega/Xtream calls;
7. secret scan over repository content.

SQLite is used only for isolated unit tests where PostgreSQL behavior is not the subject of the test.

## Mega adapter

Mocked tests verify Bearer authorization, retrieve-by-ID, id mismatch fail-closed behavior, M3U type validation, malformed responses, 4xx/5xx, timeouts, and password/token log redaction.

## Mapping

Tests cover valid import, idempotency, unique Mega id/username constraints, timezone-aware expiry contract, `dns_link` refresh, and the absence of any password column.

## Outbound URL safety

Tests cover public hostnames, malformed URLs, unsupported schemes, embedded credentials, localhost, loopback, private IP, link-local IP, DNS resolution to non-public space, and redirect refusal.

## Session resolve

Tests cover `SUCCESS`, unknown username, wrong password, authoritative expiry, explicit disabled state, DNS failure, timeout, invalid JSON and 5xx classification. Unknown username and wrong password responses are compared for equality.

## Secrets and session token

Tests assert that customer passwords, Mega tokens, Authorization values and Xtream auth URLs do not appear in logs. Signed session claims are inspected to prove the short lifetime and absence of customer/provider credentials and Mega subscription id.

## Live proof — separate from CI

Live proof is never executed in CI and requires an Owner-authorized test line plus secrets injected only into a secure execution environment.

Required live sequence:

1. retrieve the known Mega subscription by id;
2. import the mapping;
3. verify id plus SHA-256 evidence for username and `dns_link` and record only the `http`/`https` scheme;
4. prove the Mega response password was not persisted;
5. call `/v1/session/resolve` with the authorized username/password;
6. require Xtream `SUCCESS` using exactly the mapped `dns_link`;
7. retry with a wrong password and require `INVALID_CREDENTIALS`.

Evidence may include the Mega subscription id, masked/hash username evidence, SHA-256 `dns_link` evidence, scheme, mapping hash match, and the result `Xtream auth: SUCCESS`. Passwords, tokens and credential-bearing URLs are forbidden from evidence.

Order 001 live proof is complete and passed before merge. Automated CI remains separate from live-provider proof and must never use production credentials.

## Android Shell 002 automated proof

Android CI uses JDK 17 and the committed Gradle wrapper. It runs `lintDebug`, all debug JVM unit tests, `assembleDebug`, compilation/assembly of Android instrumentation-test sources, and repository secret scanning without production signing or credentials.

Order 002 JVM tests cover exact username/password request serialization, backend response-code mapping, login success/failure/temporary states, startup decisions with and without stored credentials, failed-login no-save behavior, invalid stored-credential clearing, logout clearing, HTTPS backend URL policy, and the encrypted credential-store contract.

Compose instrumentation sources cover the Login control contract, Home navigation, and D-pad focus movement. They must compile in CI. Device execution is reported separately and is never invented when an emulator/device is unavailable.

### Order 002 device matrix

- phone/tablet: launch, Splash, Login, touch navigation and adaptive Home layout;
- Android TV / TV Box: launch, D-pad-only Home navigation, strong focus state, OK activation, Back behavior and Login with remote plus system keyboard.

No Order 002 device proof may use hidden production fake-login behavior. Test-only composition/fakes are allowed only in test sources.

## Future client/platform matrices retained

Order 001 backend proof does not remove the test plan for later phases.

### Android phone/tablet

- phone and tablet form factors;
- Wi-Fi/mobile network transitions;
- background/foreground and process restart;
- secure credential persistence;
- player/network recovery where implemented.

### Android TV / TV Box

- D-pad-only navigation;
- deterministic focus movement and visible focus state;
- resume/reconnect behavior after network changes;
- playback behavior on supported TV hardware.

### Windows

- Windows 11;
- mouse/keyboard navigation;
- install/uninstall and upgrade behavior;
- secure credential storage;
- network transitions and media protocol/codec behavior.

### VPN / WireGuard

When the separately supervised VPN phase opens, test throughput, packet loss, reconnect, DNS behavior, provider access through the gateway, peer revocation, network transitions, and multiple simultaneous test peers. Capacity must be measured before production estimates.

## R1 outbound binding proof

Automated tests additionally prove that a validated public DNS result is pinned into the real transport connection, a subsequent/private peer swap is rejected before request bytes are written, provider paths are rejected, HTTP `Host` is preserved, and HTTPS SNI plus certificate verification remain enabled.
