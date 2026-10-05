# Native VPN overlay in the new Extreme/PINK host

Only `clients/extreme` and its Android workflow change. The old Android/Compose
product remains read-only. The installation cipher/store are reused from 004B;
new runtime code owns official GoBackend, authenticated native HTTPS enrollment,
first-use Android consent and process-wide automatic startup/refresh.

The private key and installation grant remain Keystore-encrypted, with backups
disabled. Neither is exposed by a JavascriptInterface. The only connection bridge
method accepts PINK username/password, performs the existing authoritative backend
login, enrolls the installation, confirms the application-only full-route tunnel
and gateway probe, then releases the authoritative Xtream origin. No VPN button,
configuration page, endpoint/key display or manual server selection exists.

First-ever enrollment follows account authentication; later starts restore the
encrypted installation grant before returning the saved account. A physical
network-bound HTTPS channel is restricted to three fixed PINK control endpoints;
provider/catalog/playback traffic never uses it. Both IP families are captured.
GoBackend keeps the interface established across transport failure/network
changes, with bounded backoff. On actual service loss the admitted process is
terminated to stop all native/Rust/WebView traffic together. This application
guard is not Android system-wide lockdown and must be tested independently.

External player and cross-device cast handoffs are disabled because they leave
the included PINK package. Internal Extreme WebView/ExoPlayer playback remains.
Native player/screensaver entry requires a ready tunnel. Existing upstream
Android logs are restricted to generic messages, and the upstream Rust Android
log plugin is not installed so it cannot export credential-bearing URLs.

Android's mandatory consent/foreground/system VPN indicators remain visible.
Always-on boot/lockdown is not silently enabled. No production capacity/public
launch is certified. Exact Android build, instrumentation and real peer proof
must be audited before a final physical APK handoff.
