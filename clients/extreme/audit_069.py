"""Task069 fixed non-sensitive native catalog stage diagnostics on exact APK lineage."""
from pathlib import Path
import subprocess

BASE = "4fdc889a48834b5058c558695b0f2a0f5d94138b"  # pragma: allowlist secret -- public source SHA
ALLOWED = {
    ".github/workflows/pink-extreme-042.yml",
    "clients/extreme/audit_069.py",
    "clients/extreme/overlay/PinkWebBridge.kt",
    "clients/extreme/overlay/vpn/PinkVodCatalog.kt",
    "clients/extreme/overlay/vpn-jvm/PinkVodCatalogTest.kt",
    "clients/extreme/overlay/pink-catalog-diagnostic.js",
    "clients/extreme/overlay/pink-catalog-diagnostic.test.ts",
    "clients/extreme/overlay/PinkCatalog.kt",
    "clients/extreme/overlay/pink-catalog.js",
    "clients/extreme/overlay/pink-catalog.test.ts",
    "clients/extreme/overlay/pink-catalog-browser.mjs",
    "clients/extreme/overlay/vpn-jvm/PinkCatalogPolicyTest.kt",
    "clients/extreme/overlay/vpn-tests/PinkCatalogTransportChecks.kt",
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
    live = Path("clients/extreme/overlay/PinkCatalog.kt").read_text()
    vod = Path("clients/extreme/overlay/pink-catalog.js").read_text()
    assert '"get_vod_categories", "get_series_categories"' in live
    assert "return readHttp(runtime.openProtectedConnection(url), action)" in live
    assert "return if (action in smallCategories) 8 * 1024 * 1024 else 32 * 1024 * 1024" in live
    assert 'action === "get_vod_categories" || action === "get_series_categories"' in vod
    assert "native.read(action, entryId)" in vod
    assert "native.openVod(action, entryId)" in vod
    browser = Path("clients/extreme/overlay/pink-catalog-browser.mjs").read_text()
    assert "request.operation === 'liveCatalog'" in browser
    assert "bulk catalog transport used after category failure" in browser
    assert "url.openConnection" not in live
    for path in ("clients/extreme/overlay/vpn/PinkConnection.kt",
                 "clients/extreme/overlay/pink-session.js",
                 "backend/app/vpn.py"):
        assert path not in changed
    assert not any(path.startswith(".project-leader/") for path in changed)
    print("PINK069_NATIVE_LIVE_CATEGORY_REUSE_AND_DIAGNOSTIC_SCOPE=PASS")


if __name__ == "__main__":
    main()
