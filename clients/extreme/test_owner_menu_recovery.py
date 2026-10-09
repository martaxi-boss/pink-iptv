"""Fast owner recovery gate: keep the menus and both catalog transports."""
from pathlib import Path
root = Path(__file__).resolve().parent
overlay = (root / "vpn_overlay.py").read_text()
bridge = (root / "overlay/pink-bridge.js").read_text()
native = (root / "overlay/PinkWebBridge.kt").read_text()
vod_overlay = (root / "vod_overlay.py").read_text()
media_tests = (root / "overlay/vpn-tests/PinkVodTracksChecks.kt").read_text()
cast = (root / "overlay/pink-screen-cast.js").read_text()
cat = (root / "overlay/pink-catalog.js").read_text()
kt = (root / "overlay/PinkCatalog.kt").read_text()
stage = (root / "overlay/vpn/PinkVodCatalog.kt").read_text()
assert "    button.hidden = true" not in overlay
assert "Pinned upstream must contain both Live cast menu actions" in overlay
assert 'pink-screen-cast.js' in overlay
assert "pink_video_picture_in_picture" in overlay and "pink_video_cast_screen" in overlay
assert "R.id.pink_more_actions" in overlay and 'android:id="@+id/pink_more_actions"' in overlay
assert "pink_video_more_actions" in vod_overlay and "pink_video_more_dots" in vod_overlay
assert "verifyOwnerPlaybackMenu()" in media_tests
assert "PINK_ANDROID_NATIVE_DOTS_MENU_PIP_CAST_AND_REAL_DISPLAY_MODE=PASS" in media_tests
assert "Settings.ACTION_CAST_SETTINGS" in native and '"castPicker" ->' in native
assert "window.PinkScreenCast" in bridge and "await open()" in cast
assert '"BRIDGE_OPEN"' in native and '"BRIDGE_CHUNK"' in native
assert '"get_vod_categories"' in kt and '"get_series_categories"' in kt
assert "native.read(action, entryId)" in cat
assert "PinkVodStageStore()" in stage and "STAGE_CAPACITY" in stage
assert "throw new Error" in overlay and "tv-cast.ts" in overlay
print("PINK_OWNER_MENUS_VOD_SERIES_SAFE_MIRRORING=PASS")
