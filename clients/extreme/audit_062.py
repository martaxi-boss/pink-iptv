"""Bounded E1 recovery audit for owner-device login / VPN consent regression."""
from pathlib import Path
import subprocess

BASE = "f5550e4e5bccfb9cc300e728cbca7c6190abd683"
ALLOWED = {
    ".github/workflows/pink-extreme-042.yml",
    "clients/extreme/audit_062.py",
    "clients/extreme/overlay/PinkWebBridge.kt",
    "clients/extreme/overlay/pink-bridge.js",
    "clients/extreme/overlay/pink-bridge.test.ts",
    "clients/extreme/overlay/pink-session.js",
    "clients/extreme/overlay/pink-session.test.ts",
    "clients/extreme/overlay/vpn/PinkConnection.kt",
    "clients/extreme/vpn_overlay.py",
    "docs/DEVICE_LOGIN_RECOVERY_062.md",
}
HISTORICAL = [
    ".project-leader/tasks/",
    ".project-leader/results/",
    ".project-leader/recovery-events/",
    ".project-leader/transitions/",
]


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], check=True)
    names = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
    assert names, "No recovery implementation"
    assert names <= ALLOWED, sorted(names - ALLOWED)
    assert not git("diff", "--name-only", BASE, "HEAD", "--", *HISTORICAL)
    native = Path("clients/extreme/overlay/vpn/PinkConnection.kt").read_text()
    bridge = Path("clients/extreme/overlay/PinkWebBridge.kt").read_text()
    web = Path("clients/extreme/overlay/pink-bridge.js").read_text()
    session = Path("clients/extreme/overlay/pink-session.js").read_text()
    assert "VpnService.prepare(app)" in native
    assert "fun retryPermission(" in native and "permission = CountDownLatch(1)" in native
    assert '"vpnPermissionRetry"' in bridge and "context as? MainActivity" in bridge
    assert 'sourceOrigin.host != "tauri.localhost"' in bridge and "!mainFrame" in bridge
    assert "safeLoginCodes.has(reply.code)" in web
    assert "await native.requestVpnConsent?.()" in session
    assert "no browser/provider fallback" in session
    print("PINK_062_BOUNDED_LOGIN_CONSENT_PRIVACY_AND_HISTORY=PASS")


if __name__ == "__main__":
    main()
