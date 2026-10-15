"""Fast deterministic PINK cinema test; no Android emulator or provider needed."""
from pathlib import Path
from tempfile import TemporaryDirectory
from premium_overlay import apply


def test() -> None:
    root = Path(__file__).resolve().parent
    with TemporaryDirectory() as temp:
        dest = Path(temp)
        (dest / "public").mkdir()
        (dest / "src/layouts").mkdir(parents=True)
        (dest / "src/pages").mkdir(parents=True)
        (dest / "src/layouts/Layout.astro").write_text(
            '<html lang="en" class="h-full">\n  <head>\n  </head>\n<slot />\n</html>\n')
        (dest / "src/layouts/TvLayout.astro").write_text(
            '<html lang="en">\n  <head>\n  </head>\n<div id="tv-nav"></div>\n</html>\n')
        home = dest / "src/pages/index.astro"
        home.write_text('<main>\n\t\t<nav\n\t\t\tid="hub-tiles">'
                        '<a href="/livetv">Live</a><a href="/movies">Movies</a>'
                        '<a href="/series">Series</a></nav>\n</main>\n')
        apply(root, dest)
        actual = home.read_text()
        assert actual.count('class="pink-masthead"') == 1
        assert actual.count('id="hub-tiles"') == 1
        for route in ('/livetv', '/movies', '/series'):
            assert actual.count('href="' + route + '"') == 1
        assert actual.index('class="pink-masthead"') < actual.index('id="hub-tiles"')
        for layout in ('Layout.astro', 'TvLayout.astro'):
            data = (dest / "src/layouts" / layout).read_text()
            assert data.count('data-pink-cinema="1"') == 1
            assert data.count('href="/pink-cinema.css"') == 1
        assert (dest / "public/pink-cinema.css").read_bytes() == (root / "overlay/pink-cinema.css").read_bytes()
        try:
            apply(root, dest)
        except ValueError:
            pass
        else:
            raise AssertionError("Duplicate PINK cinema overlay permitted")
    print("PINK_CINEMA_NO_NAVIGATION_REGRESSION=PASS")


if __name__ == "__main__":
    test()
