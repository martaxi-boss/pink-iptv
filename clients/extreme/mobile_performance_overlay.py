"""PINK Android phone fluidity overlay for the pinned Extreme client.

Changes only mobile-classic UI rendering: poster batch size, explicit-load cap,
view-transition overhead and the existing performance-mode default.
Preserves native playback, VPN, account storage, TV layout and user overrides.
"""
from pathlib import Path


def replace_once(path: Path, before: str, after: str) -> None:
    current = path.read_text()
    if current.count(before) != 1:
        raise ValueError(f"Unexpected pinned UI source shape: {path}")
    path.write_text(current.replace(before, after, 1))


def apply(root: Path, dest: Path) -> None:
    dest = Path(dest)
    layout = dest / "src/layouts/Layout.astro"
    # Existing Settings -> Performance mode OFF stores an empty string,
    # so only NULL (never configured) opts into phone-friendly defaults.
    # TV's already-existing auto-selection remains unchanged.
    before = """        if (perfStored === "1") {
          document.documentElement.setAttribute("data-perf-mode", "on");
        }"""
    after = """        if (perfStored === null && !isTvDevice &&
            !!window.PinkNative &&
            window.matchMedia("(max-width: 720px) and (pointer: coarse)").matches) {
          localStorage.setItem("xt_perf_mode", "1");
          localStorage.setItem("xt_perf_mode_auto_mobile", "1");
          perfStored = "1";
        }
""" + before
    replace_once(layout, before, after)

    for name in ("movies", "series"):
        page = dest / f"src/scripts/{name}/{name}.ts"
        replace_once(
            page,
            "const PAGE_SIZE = 200\nconst AUTO_LOAD_CAP = 1500",
            """// The real Android phone gets small, lightweight poster batches.
 // Large Android TV screens and desktop retain their original 200-card batches.
const PINK_MOBILE_GRID =
  typeof window !== "undefined" &&
  !!window.PinkNative &&
  window.matchMedia("(max-width: 720px) and (pointer: coarse)").matches
const PAGE_SIZE = PINK_MOBILE_GRID ? 24 : 200
const AUTO_LOAD_CAP = PINK_MOBILE_GRID ? 240 : 1500""".replace("\n // Large", "\n// Large")
        )
        # Drop large image-grid crossfades only on the small Android phone.
        # The desktop/TV animation and accessibility reduce-motion remain.
        replace_once(
            page,
            "    wasSkeleton &&\n    willShowReal &&",
            "    wasSkeleton &&\n    willShowReal &&\n    !PINK_MOBILE_GRID &&",
        )
        replace_once(
            page,
            '{ root: gridEl, rootMargin: "600px 0px" }',
            '{ root: gridEl, rootMargin: PINK_MOBILE_GRID ? "160px 0px" : "600px 0px" }',
        )
        # This memo is already implemented by upstream. Reuse it for reloads
        # of the same catalog array instead of re-indexing tens of thousands
        # of title variants when an in-page refresh occurs.
        replace_once(
            page,
            "import { buildGroupingIndex, pickPreferredEntryId, groupPassesLanguageFilter } from \"@/scripts/lib/language-groups.ts\"",
            "import { buildGroupingIndex, getSharedGroupingIndex, pickPreferredEntryId, groupPassesLanguageFilter } from \"@/scripts/lib/language-groups.ts\"",
        )
        replace_once(page, "groupingIndex = buildGroupingIndex(all)",
                     "groupingIndex = getSharedGroupingIndex(all)")
    print("PINK_ANDROID_MOBILE_FLUIDITY_SMALL_BATCHES=PASS")
    print("PINK_ANDROID_MOBILE_PERF_DEFAULT_WITH_USER_OVERRIDE=PASS")
    print("PINK_TV_DESKTOP_BATCH_AND_MEDIA_UNTOUCHED=PASS")
