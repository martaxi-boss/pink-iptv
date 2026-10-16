"""Fixture contract for the mobile-only Android performance changes."""
from pathlib import Path
from tempfile import TemporaryDirectory
from mobile_performance_overlay import apply


def test() -> None:
    with TemporaryDirectory() as directory:
        dest = Path(directory)
        layout = dest / "src/layouts/Layout.astro"
        layout.parent.mkdir(parents=True)
        layout.write_text("""<html>
        if (perfStored === null && isTvDevice) {
          localStorage.setItem("xt_perf_mode", "1");
          localStorage.setItem("xt_perf_mode_auto", "1");
          perfStored = "1";
        }
        if (perfStored === "1") {
          document.documentElement.setAttribute("data-perf-mode", "on");
        }
</html>""")
        pages = {}
        for kind in ("movies", "series"):
            page = dest / f"src/scripts/{kind}/{kind}.ts"
            page.parent.mkdir(parents=True, exist_ok=True)
            text = """import { buildGroupingIndex, pickPreferredEntryId, groupPassesLanguageFilter } from "@/scripts/lib/language-groups.ts"
const PAGE_SIZE = 200
const AUTO_LOAD_CAP = 1500
// This initializer executes before painting, even if the account is missing.
let groupingIndex = buildGroupingIndex([])
function paint(data) {
  groupingIndex = buildGroupingIndex(all)
  const useVT =
    wasSkeleton &&
    willShowReal &&
    typeof document.startViewTransition === "function"
  const observer = new IntersectionObserver(cb,
      { root: gridEl, rootMargin: "600px 0px" })
}
// Native actions, cache, row indexes and account routing remain unchanged.
function openMovie(id) { return id }
"""
            page.write_text(text)
            pages[kind] = (page, text)
        original_layout = layout.read_text()
        apply(dest, dest)
        newer = layout.read_text()
        assert original_layout in newer.replace(
            """        if (perfStored === null && !isTvDevice &&
            !!window.PinkNative &&
            window.matchMedia("(max-width: 720px) and (pointer: coarse)").matches) {
          localStorage.setItem("xt_perf_mode", "1");
          localStorage.setItem("xt_perf_mode_auto_mobile", "1");
          perfStored = "1";
        }
""", "")
        assert 'perfStored === null' in newer
        assert 'xt_perf_mode_auto_mobile' in newer
        assert 'xt_perf_mode_auto"' in newer  # TV automatic path preserved
        assert 'window.PinkNative' in newer  # no change for ordinary mobile browser
        for kind, (page, old) in pages.items():
            patched = page.read_text()
            assert "PINK_MOBILE_GRID ? 24 : 200" in patched
            assert "PINK_MOBILE_GRID ? 240 : 1500" in patched
            assert 'PINK_MOBILE_GRID ? "160px 0px" : "600px 0px"' in patched
            assert "!PINK_MOBILE_GRID &&" in patched
            assert "getSharedGroupingIndex(all)" in patched
            assert 'import { buildGroupingIndex, getSharedGroupingIndex, ' in patched
            assert "let groupingIndex = buildGroupingIndex([])" in patched
            assert patched.count("buildGroupingIndex(") == 1  # retained only for initial empty index
            assert 'function openMovie(id) { return id }' in patched
            assert patched.count("const PINK_MOBILE_GRID =") == 1
            # Native-only guard and TV/desktop fallbacks remain explicit.
            assert '!!window.PinkNative' in patched
            assert 'PINK_MOBILE_GRID ? 24 : 200' in patched
        try:
            apply(dest, dest)
        except ValueError:
            pass
        else:
            raise AssertionError("Overlay unexpectedly applied twice")
    source = (Path(__file__).parent / "prepare.py").read_text()
    assert "from mobile_performance_overlay import apply as apply_mobile_performance" in source
    assert "apply_mobile_performance(ROOT, DEST)" in source
    print("PINK_MOBILE_FLUIDITY_OVERLAY_FIXTURE=PASS")


if __name__ == "__main__":
    test()
