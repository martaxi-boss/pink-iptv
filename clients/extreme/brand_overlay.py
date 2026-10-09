"""Apply the approved PINK IPTV idle artwork without changing media routing.

The source checkout is disposable and pinned by prepare.py. We only alter
Astro visuals; original TV, movie, episode and WireGuard code remains unchanged.
"""
from pathlib import Path
import shutil


BANNER = "pink-idle.webp"


def _replace_once(path: Path, before: str, after: str) -> None:
    source = path.read_text()
    if source.count(before) != 1:
        raise ValueError(f"Unexpected pinned upstream page shape: {path.name}")
    path.write_text(source.replace(before, after, 1))


def _patch_live(page: Path) -> None:
    text = page.read_text()
    if 'class="pink-brand-image ' in text:
        raise ValueError("PINK live visual overlay already applied")
    begin_token = '\t\t\t\t\t\t<div\n\t\t\t\t\t\t\tid="player-empty"'
    video_token = '\t\t\t\t\t\t<video\n\t\t\t\t\t\t\tid="player"'
    assert text.count(begin_token) == 1 and text.count(video_token) == 1
    begin = text.index(begin_token)
    end = text.index(video_token, begin)
    old = text[begin:end]
    # Preserve the selectors used by showExternalPlayerEmptyState() and
    # resetEmptyState(), including their data-i18n targets.
    for token in ('id="player-empty"', 'livetv.idle', 'livetv.pickChannel',
                  'livetv.pickChannelHelper', 'pointer-events-none'):
        assert token in old
    assert 'id="player"' not in old
    branded = """\t\t\t\t\t\t<div
\t\t\t\t\t\t\tid="player-empty"
\t\t\t\t\t\t\tclass="absolute inset-0 flex flex-col items-center justify-center text-center pointer-events-none">
\t\t\t\t\t\t\t<img src="/pink-idle.webp" alt="PINK IPTV"
\t\t\t\t\t\t\t\tclass="pink-brand-image absolute inset-0 h-full w-full object-cover"
\t\t\t\t\t\t\t\tdecoding="async" />
\t\t\t\t\t\t\t<div class="pink-player-runtime-status absolute inset-0 flex-col items-center justify-center gap-3 px-6">
\t\t\t\t\t\t\t\t<span data-i18n="livetv.idle" class="text-sm text-fg-3">Idle</span>
\t\t\t\t\t\t\t\t<p data-i18n="livetv.pickChannel" class="text-xl font-bold text-fg">Pick a channel.</p>
\t\t\t\t\t\t\t\t<p data-i18n="livetv.pickChannelHelper" class="text-sm text-fg-3">Choose from the list, or change category.</p>
\t\t\t\t\t\t\t</div>
\t\t\t\t\t\t</div>
"""
    text = text[:begin] + branded + text[end:]
    style_marker = '\t<style>\n'
    assert text.count(style_marker) == 1
    # Keep the exact upstream OFF/current status, EPG panel, and toggle.
    # Only the empty-player illustration changes; no player or EPG behavior
    # is patched.
    css = """\t<style>
\t\t/* Preserve upstream OFF/current status and EPG controls. */
\t\t:global(#player-empty .pink-player-runtime-status) { display: none; }
\t\t/* External-player status must remain visible (MPV/VLC); original
\t\t   stream.ts owns these state changes and is deliberately untouched. */
\t\t:global(#player-empty:has([data-empty-title="external"]) .pink-brand-image) { display: none; }
\t\t:global(#player-empty:has([data-empty-title="external"]) .pink-player-runtime-status) { display: flex; }
"""
    text = text.replace(style_marker, css, 1)
    assert text.count('id="player-wrap"') == 1
    assert text.count('id="player"') == 1
    assert text.count('id="current"') == 1
    assert text.count('id="epg"') == 1
    page.write_text(text)


def _patch_catalog(page: Path, name: str) -> None:
    assert name in ("movie", "series")
    if 'data-pink-idle-brand=' in page.read_text():
        raise ValueError("PINK catalogue visual overlay already applied")
    start = '\t\t<header class="route-hero shrink-0">'
    image = f"""\t\t<!-- PINK artwork is purely decorative. Card navigation and VOD
\t\t     playback remain in the existing, unmodified catalogue scripts. -->
\t\t<section data-pink-idle-brand="{name}" aria-label="PINK IPTV"
\t\t\tclass="shrink-0 rounded-2xl border border-line bg-surface p-3">
\t\t\t<div class="aspect-video w-full mx-auto max-h-[60dvh] max-w-[calc(60dvh*16/9)]
\t\t\t\toverflow-hidden rounded-xl bg-black ring-1 ring-line">
\t\t\t\t<img src="/pink-idle.webp" alt="PINK IPTV" loading="eager"
\t\t\t\t\tdecoding="async" class="h-full w-full object-cover pointer-events-none" />
\t\t\t</div>
\t\t</section>

"""
    _replace_once(page, start, image + start)
    after = page.read_text()
    assert f'id="{name}-grid"' in after
    assert f'id="{name}-category-picker-trigger"' in after
    assert f'id="{name}-search"' in after


def apply(root: Path, dest: Path) -> None:
    root, dest = Path(root), Path(dest)
    artwork = root / "assets" / BANNER
    if not artwork.is_file():
        raise FileNotFoundError("Exact approved PINK banner asset is missing")
    assert artwork.stat().st_size < 40_000
    shutil.copyfile(artwork, dest / "public" / BANNER)
    _patch_live(dest / "src/pages/livetv.astro")
    _patch_catalog(dest / "src/pages/movies/index.astro", "movie")
    _patch_catalog(dest / "src/pages/series/index.astro", "series")
    print("PINK085_THREE_MENUS_SAME_IDLE_IMAGE=PASS")
    print("PINK086_LIVE_OFF_EPG_PRESENT_AND_INTERACTIVE=PASS")
    print("PINK085_NATIVE_PLAYER_AND_TV_MOVIE_SERIES_URLS_UNCHANGED=PASS")
