"""PINK task066 immutable scope, real enrollment-cap diagnostics and privacy audit."""
from pathlib import Path
import subprocess


BASE = "e26749ee396585b7335730f9389406b7b5c52a42" # pragma: allowlist secret -- public immutable Git SHA
ALLOWED = {
    ".project-leader/tasks/PINK-IPTV-VPN-QUOTA-RECOVERY-066.json",
    ".project-leader/results/PINK-IPTV-VPN-QUOTA-RECOVERY-066.json",
    ".github/workflows/pink-extreme-042.yml",
    "backend/app/vpn.py",
    "backend/tests/test_vpn.py",
    "clients/extreme/audit_066.py",
    "clients/extreme/overlay/vpn/PinkConnection.kt",
    "clients/extreme/overlay/vpn-tests/PinkVpnIdentityTest.kt",
    "clients/extreme/overlay/pink-bridge.js",
    "clients/extreme/overlay/pink-bridge.test.ts",
    "clients/extreme/overlay/pink-session.js",
    "clients/extreme/overlay/pink-session.test.ts",
    "docs/VPN_QUOTA_RECOVERY_066.md",
}


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], check=True)
    current = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
    assert current and current <= ALLOWED, sorted(current - ALLOWED)
    assert "backend/app/vpn.py" in current
    assert "clients/extreme/overlay/vpn/PinkConnection.kt" in current
    assert not any("/recovery-events/" in path or "/transitions/" in path for path in current)

    backend = Path("backend/app/vpn.py").read_text()
    session = Path("clients/extreme/overlay/pink-session.js").read_text()
    bridge = Path("clients/extreme/overlay/pink-bridge.js").read_text()
    native = Path("clients/extreme/overlay/vpn/PinkConnection.kt").read_text()
    assert 'headers={"Cache-Control": "no-store", "Retry-After": str(seconds)}' in backend
    assert "min(86400, math.ceil" in backend
    assert 'controlFailure.contains(";status=429;")' in native
    assert 'controlFailure.startsWith("ENROLL:PINNED_HTTPS_CONTROL:")' in native
    assert '"VPN_LIMIT"' in native and '"VPN_LIMIT"' in bridge
    assert "Não desinstale esta aplicação" in session
    assert "vpn_max_installations_per_account" in backend
    assert "VpnService.prepare(app)" in native
    assert "VPN_ENROLL" in native
    assert "no browser/provider fallback" in session
    assert 'POST /v1/vpn/enroll' not in session
    print("PINK066_EXACT_QUOTA_FAILURE_PRIVACY_ARCHITECTURE_SCOPE=PASS")


if __name__ == "__main__":
    main()
