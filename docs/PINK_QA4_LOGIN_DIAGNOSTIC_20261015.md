# PINK QA4 login-startup diagnostic — 2026-10-15

Exact-head Android CI [38740422631](https://github.com/martaxi-boss/pink-iptv/actions/runs/38740422631) compiled the corrected VOD routes and passed the browser page smoke, original-package Android emulator VPN/Keystore and native video tests, privacy scan and lint. The independently packaged QA4 passed native identity and WebMessage instrumentation but failed the **mandatory no-account login navigation gate** of `PinkCatalogWorkerChecks`: `Offline WebView did not finish its empty-account redirect`. That test must remain blocking; no APK may be handed off.

To distinguish slow renderer load from a missing first-run transition or native bridge failure, only when the 15-second redirect gate fails the instrumentation now emits *local-only, privacy-safe* booleans: WebView progress, local URL path (not origin), document readiness, presence of the Pink native bridge and vault, and existence of selectedId (not its value). Credentials, provider URLs, tokens, response bodies and account IDs are never collected.

The next exact-SHA emulator run must be used to determine the actual cause. Do NOT merely skip the failed test or force a green release. The installed owner original and QA2/QA3 apps remain untouched.
