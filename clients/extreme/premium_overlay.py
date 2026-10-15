"""PINK cinema visual overlay for the pinned full Android/Tauri client."""
from pathlib import Path
import shutil


def _one(path: Path, before: str, after: str) -> None:
    source = path.read_text(encoding="utf-8")
    if source.count(before) != 1:
        raise ValueError(f"Unexpected upstream marker / duplicate PINK overlay: {path}")
    path.write_text(source.replace(before, after, 1), encoding="utf-8")


HERO = """\t\t<!-- Visual banner only; real content and original routes follow. -->
\t\t<section class="pink-masthead" aria-label="PINK IPTV">
\t\t\t<div class="pink-masthead__content">
\t\t\t\t<p class="pink-masthead__eyebrow">O teu universo de entretenimento</p>
\t\t\t\t<h1 class="pink-masthead__title"><span>PINK</span> IPTV</h1>
\t\t\t\t<p class="pink-masthead__description">Televisão em direto, filmes e séries. Tudo num só lugar.</p>
\t\t\t</div>
\t\t\t<div class="pink-masthead__emblem" aria-hidden="true"><span>P</span></div>
\t\t</section>

"""


def apply(root: Path, dest: Path) -> None:
    root, dest = Path(root), Path(dest)
    css = root / "overlay/pink-cinema.css"
    classic = dest / "src/layouts/Layout.astro"
    tv = dest / "src/layouts/TvLayout.astro"
    home = dest / "src/pages/index.astro"
    target = dest / "public/pink-cinema.css"
    if not css.is_file() or not target.parent.is_dir() or target.exists():
        raise ValueError("PINK cinema asset missing or already installed")
    _one(classic, '<html lang="en" class="h-full">',
         '<html lang="en" class="h-full" data-pink-cinema="1">')
    _one(tv, '<html lang="en">',
         '<html lang="en" data-pink-cinema="1">')
    for layout in (classic, tv):
        _one(layout, "  <head>\n",
             '  <head>\n    <link rel="stylesheet" href="/pink-cinema.css" />\n')
    _one(home, '\t\t<nav\n\t\t\tid="hub-tiles"', HERO + '\t\t<nav\n\t\t\tid="hub-tiles"')
    shutil.copyfile(css, target)
    assert home.read_text(encoding="utf-8").count('id="hub-tiles"') == 1
    print("PINK_CINEMA_ORIGINAL_ROUTES_PRESERVED=PASS")
