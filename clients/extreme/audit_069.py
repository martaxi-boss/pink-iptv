"""Task069 fixed non-sensitive native catalog stage diagnostics on exact APK lineage."""
from pathlib import Path
import subprocess

BASE = "4fdc889a48834b5058c558695b0f2a0f5d94138b"  # public source SHA
ALLOWED = {
    ".github/workflows/pink-extreme-042.yml",
    "clients/extreme/audit_069.py",
    "clients/extreme/overlay/PinkWebBridge.kt",
    "clients/extreme/overlay/vpn/PinkVodCatalog.kt",
    "clients/extreme/overlay/vpn-jvm/PinkVodCatalogTest.kt",
    "clients/extreme/overlay/pink-catalog-diagnostic.js",
    "clients/extreme/overlay/pink-catalog-diagnostic.test.ts",
}


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], check=True)
    changed = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
    assert changed == ALLOWED, sorted(changed.symmetric_difference(ALLOWED))
    source = Path("clients/extreme/overlay/vpn/PinkVodCatalog.kt").read_text()
    bridge = Path("clients/extreme/overlay/PinkWebBridge.kt").read_text()
    categories = Path("clients/extreme/overlay/pink-catalog-diagnostic.js").read_text()
    for phase in ("ACCOUNT_BINDING", "SOURCE_VALIDATION", "VPN_NETWORK",
                  "STAGE_FILE", "STAGE_LIFECYCLE"):
        assert phase in source
        assert phase in categories
    assert 'catch (_: Exception) { throw PinkCatalogFailure(phase) }' in source
    assert 'catch (failure: PinkCatalogFailure) { throw failure }' in source
    assert '"vodCatalog" -> "BRIDGE_OPEN"' in bridge
    assert '"vodCatalogChunk" -> "BRIDGE_CHUNK"' in bridge
    assert '"STAGE_LIFECYCLE"' in bridge
    for path in ("clients/extreme/overlay/vpn/PinkConnection.kt",
                 "clients/extreme/overlay/PinkCatalog.kt",
                 "clients/extreme/overlay/pink-session.js",
                 "backend/app/vpn.py"):
        assert path not in changed
    assert not any(path.startswith(".project-leader/") for path in changed)
    print("PINK069_SAFE_STAGE_DIAGNOSTIC_NO_VPN_OR_LIVE_MUTATION=PASS")


if __name__ == "__main__":
    main()
