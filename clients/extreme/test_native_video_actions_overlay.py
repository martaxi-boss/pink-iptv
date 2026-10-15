"""Offline, synthetic regression test for native playback actions on all three routes.

Real Android/Media3 and exact pinned upstream compilation remain separate CI gates.
"""
from pathlib import Path
from tempfile import TemporaryDirectory

from native_video_actions_overlay import NATIVE, _replace_once, apply


def _put(root: Path, relative: str, source: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source)


def test() -> None:
    with TemporaryDirectory() as directory:
        root = Path(directory)

        _put(root, NATIVE, """    updateControllerForMode()
    initializePlayer()
  }
  // Gear-button menu. Speed is VOD-only: a live edge has nowhere to run ahead to.
  private fun showPlayerSettings() {
    if (mode == MODE_LIVE) {
      showDisplayModeChooser()
      return
    }
    val labels = arrayOf(
      getString(R.string.xt_video_display_mode),
      getString(R.string.xt_video_playback_speed),
    )
    AlertDialog.Builder(this)
      .setTitle(R.string.xt_video_settings_title)
      .setItems(labels) { _, which ->
        if (which == 0) showDisplayModeChooser() else showPlaybackSpeedChooser()
      }
      .show()
  }
""")
        _put(root, "src-tauri/gen/android/app/src/main/res/values/strings.xml",
             '<resources>\n    <string name="xt_video_display_stretch">Stretch</string>\n</resources>\n')
        _put(root, "src/scripts/lib/android-video-launcher.ts",
             '  | "xt:android-native-volume"\n'
             '  const types = [\n    "xt:android-native-volume",\n  ]\n')
        _put(root, "src/scripts/stream/stream.ts",
             '  subscribeAndroidNativeEvents((event) => {\n'
             '    if (event.type !== "xt:android-native-channel-changed") return\n'
             '  })\n')
        movie = """  launchAndroidNativeVodWithProgress,
let pipBtnBound = false
      progressExtras: { title: movie.name, logo: movie.logo || null },
    })
    if (launched) return
)

subscribeExternalPlayerExit(() => {
  if (!externalPresenceActive) return
"""
        _put(root, "src/scripts/movies/detail.ts", movie)
        series = """  launchAndroidNativeVodWithProgress,
let pipBtnBound = false
    })
    if (launched) return
  }

  let backend = getPlayerBackend()
)

subscribeExternalPlayerExit(() => {
  if (!externalPresenceActive) return
"""
        _put(root, "src/scripts/series/detail.ts", series)

        apply(root)

        native = (root / NATIVE).read_text()
        assert "pink_native_pip" in native
        assert "pink_native_play_on_tv" in native
        assert "playerView?.showController()" in native
        assert 'put("contentKey", castKey)' in native
        assert 'put("channelId", channel.id)' in native
        assert "finish()" in native
        bridge = (root / "src/scripts/lib/android-video-launcher.ts").read_text()
        assert bridge.count('"xt:android-native-cast-request"') == 2
        live = (root / "src/scripts/stream/stream.ts").read_text()
        assert 'event.payload?.mode !== "live"' in live
        assert "String(entry.id) === String(event.payload.channelId)" in live
        for kind, route in (("vod", "movies"), ("ep", "series")):
            source = (root / f"src/scripts/{route}/detail.ts").read_text()
            assert 'target.playlistId !== activePlaylistId' in source
            assert 'target.contentKey !== event.payload?.contentKey' in source
            assert f'target.contentKey !== "{kind}:" +' in source
            assert f'document.getElementById("{("movie" if kind == "vod" else "series")}-detail-play-tv")' in source
            assert "button && !button.hidden && button.isConnected" in source

        # Refuse ambiguous updates. No duplicate menu or silent partial patch.
        try:
            apply(root)
        except ValueError:
            pass
        else:
            raise AssertionError("Overlay must reject reapplication")
        try:
            _replace_once(root, NATIVE, "nonexistent-original-selector", "x")
        except ValueError:
            pass
        else:
            raise AssertionError("Missing pinned anchor must fail")
    print("PINK087_THREE_NATIVE_VIDEO_ACTIONS_HANDOFF_FAST_TEST=PASS")


if __name__ == "__main__":
    test()
