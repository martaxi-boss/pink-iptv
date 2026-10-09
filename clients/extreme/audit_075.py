"""Bounded non-production audit of 10-slot VPN Android self-service recovery."""

from pathlib import Path
import json
import subprocess

BASE = "0c4b0b93fa77a433cf4d2d2ccd3076539643adef"  # pragma: allowlist secret - Git SHA
ALLOWED = {
    ".github/workflows/pink-extreme-042.yml",
    "backend/app/vpn.py",
    "backend/tests/test_vpn.py",
    "clients/extreme/audit_075.py",
    "clients/extreme/overlay/vpn/PinkConnection.kt",
    "clients/extreme/overlay/vpn-tests/PinkVpnIdentityTest.kt",
    "clients/extreme/overlay/PinkWebBridge.kt",
    "clients/extreme/overlay/pink-bridge.js",
    "clients/extreme/overlay/pink-bridge.test.ts",
    "clients/extreme/overlay/pink-session.js",
    "clients/extreme/overlay/pink-session.test.ts",
    "clients/extreme/overlay/pink-login.js",
    "clients/extreme/overlay/pink-login.test.ts",
    ".project-leader/transitions/PINK-IPTV-VPN-ANDROID-075-MERGE.authorization.json",
    ".project-leader/transitions/PINK-IPTV-VPN-ANDROID-075-MERGE-R2.authorization.json",
    ".project-leader/transitions/PINK-IPTV-VPN-ANDROID-075-MERGE-R3.authorization.json",
}


def main() -> None:
    subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], check=True)
    changed = set(subprocess.check_output(
        ["git", "diff", "--name-only", BASE, "HEAD"], text=True
    ).splitlines())
    assert changed and changed <= ALLOWED, sorted(changed - ALLOWED)

    # Authorization-only descendants preserve tested application logic.
    # Admit only these exact Owner grants, never arbitrary control records.
    transition_dir = Path(".project-leader/transitions")
    for suffix in ("MERGE", "MERGE-R2", "MERGE-R3"):
        item = transition_dir / (
            "PINK-IPTV-VPN-ANDROID-075-" + suffix + ".authorization.json"
        )
        if not item.exists():
            continue
        grant = json.loads(item.read_text())
        assert grant["repository"] == "martaxi-boss/pink-iptv"
        assert grant["action"] == "merge_to_main"
        assert grant["effect_class"] == "E2_CONSEQUENTIAL_TRANSITION"
        assert grant["authority"]["source"] == "STANDING_OWNER_GRANT"
        assert grant["target"]["identifier"] == "61"
        assert grant["target"]["base_revision"] == BASE
        assert grant["target"]["environment"] == "repository_main"
        subprocess.run(
            ["git", "merge-base", "--is-ancestor", grant["target"]["revision"], "HEAD"],
            check=True,
        )
        if suffix in ("MERGE-R2", "MERGE-R3"):
            # A pull_request job checks out a synthetic GitHub merge commit.
            # HEAD^ is the target base, NOT the source branch parent. Instead
            # resolve the exact immutable commit introducing this grant.
            auth_commit = subprocess.check_output(
                ["git", "log", "-n", "1", "--format=%H", "--", str(item)], text=True
            ).strip()
            assert auth_commit
            parent = subprocess.check_output(
                ["git", "rev-parse", auth_commit + "^"], text=True
            ).strip()
            assert parent == grant["target"]["revision"]
            assert subprocess.check_output(
                ["git", "diff", "--name-only", parent, auth_commit], text=True
            ).splitlines() == [str(item)]

    source = Path("clients/extreme/overlay/vpn/PinkConnection.kt").read_text()
    bridge = Path("clients/extreme/overlay/PinkWebBridge.kt").read_text()
    web = Path("clients/extreme/overlay/pink-bridge.js").read_text()
    session = Path("clients/extreme/overlay/pink-session.js").read_text()
    login = Path("clients/extreme/overlay/pink-login.js").read_text()
    backend = Path("backend/app/vpn.py").read_text()
    assert 'X-Pink-Vpn-Admission' in source
    assert 'admission=capacity' in source
    assert 'admission=throttle' not in source or 'admission=capacity' in source
    assert 'classifyLoginFailure' in source and 'VPN_LIMIT' in source
    assert 'pendingRecoveryBearer' in source
    assert 'VpnService.prepare(app)' in source
    assert 'GoBackend' in source
    assert 'VPN_LIMIT' in session and 'VPN_LIMIT' in web
    assert 'vpnInstallations' in bridge and 'vpnReleaseInstallation' in bridge
    assert 'sourceOrigin.host != "tauri.localhost"' in bridge and '!mainFrame' in bridge
    assert 'X-Pink-Device-Public-Key' in source and 'X-Pink-Device-Public-Key' in backend
    assert 'installPinkBridge' in session
    assert 'parseRecoverableInstallations' in login
    assert 'privateKey' not in web and 'session_token' not in web
    assert 'session_token' not in login and 'device_token' not in login
    print("PINK075_VPN_QUOTA_RECOVERY_SCOPE_AND_SECRET_BOUNDARIES=PASS")


if __name__ == "__main__":
    main()
