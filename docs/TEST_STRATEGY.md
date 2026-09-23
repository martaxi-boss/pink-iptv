# Test Strategy

## Backend

- Mega adapter mocked unit tests
- mapping uniqueness and stale refresh
- authentication rate limits
- no-secret logging tests
- signed-session expiry/revocation
- invalid/expired/deactivated line behavior
- app-config feature flags

## Xtream integration

Test with an owner-authorized line:
- correct dns_link
- wrong password
- expired/deactivated account
- Live categories/streams
- VOD
- Series
- EPG
- stream playback URL handling

Do not place real credentials in test fixtures or CI.

## Android

Matrix:
- phone
- tablet
- Android TV/TV Box
- Wi-Fi/mobile network changes
- D-pad-only navigation
- background/foreground
- process restart
- VPN reconnect

## Windows

- Windows 11
- mouse/keyboard
- install/uninstall
- network transition
- media codecs/protocols
- VPN service permission/reconnect

## VPN

- throughput
- packet loss
- reconnect
- DNS behavior
- provider access through gateway
- kill policy
- peer revocation
- multiple simultaneous test peers

Measure before estimating production capacity.
