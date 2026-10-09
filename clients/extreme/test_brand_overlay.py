"""Focused tests for the identical, non-interactive PINK 3-menu artwork."""
from pathlib import Path
from tempfile import TemporaryDirectory

from brand_overlay import apply, _patch_live


def _live() -> str:
    return """<main>
      <div id="player-wrap">
            <div
              id="player-empty"
              class="pointer-events-none flex">
              <span data-i18n="livetv.idle">Idle</span>
              <p data-i18n="livetv.pickChannel">Pick a channel.</p>
              <p data-i18n="livetv.pickChannelHelper">Choose from the list, or change category.</p>
            </div>
            <video
              id="player" controls hidden></video>
      </div>
      <div id="current"><span>OFF</span></div>
      <div id="epg"><button id="epg-expand-toggle">EPG</button></div>
      <button id="epg-toggle">Calendar</button>
      <button id="category-picker-trigger">Category</button>
    </main>
    <style>
      .unrelated-original-style { color: rebeccapurple; }
    </style>
    <script>import "../scripts/stream/stream.ts";</script>
    """.replace('            <div\n              id="player-empty"', '\t\t\t\t\t\t<div\n\t\t\t\t\t\t\tid="player-empty"').replace('            <video\n              id="player"', '\t\t\t\t\t\t<video\n\t\t\t\t\t\t\tid="player"').replace("    <style>","\t<style>\n")


def _catalog(kind: str) -> str:
    plural = "movies" if kind == "movie" else "series"
    return (f'<Layout pageTitle="{plural}">\n'
            '\t\t<header class="route-hero shrink-0">\n'
            f'          <span id="{kind}-hero-count"></span>\n'
            '        </header>\n'
            f'        <button id="{kind}-category-picker-trigger"></button>\n'
            f'        <input id="{kind}-search" />\n'
            f'        <section id="{kind}-grid"></section>\n'
            f'        <script>import "@/scripts/{plural}/{plural}.ts";</script>\n'
            '</Layout>\n')


def test() -> None:
    with TemporaryDirectory() as directory:
        root = Path(directory) / "overlay"
        dest = Path(directory) / "pinned"
        (root / "assets").mkdir(parents=True)
        (root / "assets/pink-idle.webp").write_bytes(b"synthetic-brand-fixture")
        (dest / "public").mkdir(parents=True)
        (dest / "src/pages/movies").mkdir(parents=True)
        (dest / "src/pages/series").mkdir(parents=True)
        (dest / "src/pages/livetv.astro").write_text(_live())
        for kind, plural in (("movie", "movies"), ("series", "series")):
            (dest / f"src/pages/{plural}/index.astro").write_text(_catalog(kind))
        live_old = (dest / "src/pages/livetv.astro").read_text()
        old = {p: (dest / f"src/pages/{p}/index.astro").read_text() for p in ("movies", "series")}
        apply(root, dest)
        assert (dest / "public/pink-idle.webp").read_bytes() == b"synthetic-brand-fixture"
        live = (dest / "src/pages/livetv.astro").read_text()
        assert 'src="/pink-idle.webp"' in live
        assert ":global(#current), :global(#epg) { display: none !important; }" in live
        assert 'id="current"' in live and 'id="epg"' in live
        assert 'id="epg-toggle"' in live and 'id="category-picker-trigger"' in live
        assert 'id="player-wrap"' in live and 'id="player"' in live
        assert 'data-empty-title="external"' in live
        assert 'data-i18n="livetv.pickChannel"' in live
        assert 'import "../scripts/stream/stream.ts"' in live
        assert "OFF" in live_old and "OFF" in live
        assert live.count('id="player-empty"') == 1
        for name, kind in (("movies", "movie"), ("series", "series")):
            newer = (dest / f"src/pages/{name}/index.astro").read_text()
            assert newer.count('src="/pink-idle.webp"') == 1
            assert newer.count(f'data-pink-idle-brand="{kind}"') == 1
            # All upstream links/controls and detail-player navigations stay
            # byte-identical after the one visual insertion.
            image_start = newer.index("\t\t<!-- PINK artwork")
            image_end = newer.index('\t\t<header class="route-hero shrink-0">', image_start)
            assert newer[:image_start] + newer[image_end:] == old[name]
        try:
            _patch_live(dest / "src/pages/livetv.astro")
        except (AssertionError, ValueError):
            pass
        else:
            raise AssertionError("Second overlay unexpectedly duplicated the logo")
    print("PINK085_THREE_MENU_BRAND_AND_SOURCE_ISOLATION_FAST_TEST=PASS")


if __name__ == "__main__":
    test()
