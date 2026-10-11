"""Restore native playback actions without changing URLs, credentials or player engines.

The pinned upstream Android VideoActivity takes over the screen for Live/VOD.
The web-only action buttons are then hidden behind that Activity.  This overlay
gives the *existing* native settings control PiP and Play on TV, and routes a
credential-free cast intent back to the already existing web cast handlers.
"""
from pathlib import Path


NATIVE = "src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/VideoActivity.kt"


def _replace_once(root: Path, relative: str, before: str, after: str) -> None:
    path = root / relative
    source = path.read_text()
    count = source.count(before)
    if count != 1:
        raise ValueError(f"PINK native playback overlay expected one marker in {relative}, found {count}")
    path.write_text(source.replace(before, after, 1))


def apply(dest: Path) -> None:
    root = Path(dest)

    # Expose the native controller immediately when the video Activity starts.
    # It auto-hides through Media3 as before, and can be recalled by a tap or OK.
    _replace_once(root, NATIVE,
        "    updateControllerForMode()\n    initializePlayer()\n  }",
        "    updateControllerForMode()\n    initializePlayer()\n"
        "    // Playback actions are available from the first frame on phone and TV.\n"
        "    playerView?.showController()\n  }")

    original = """  // Gear-button menu. Speed is VOD-only: a live edge has nowhere to run ahead to.
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
"""
    updated = """  // A single native menu for Live channels, movies and series episodes.
  // Reuse Media3's own video scaling/PiP and the web app's existing cast flow.
  // Casting only queues a content identifier, never a stream URL or credentials.
  private fun showPlayerSettings() {
    val actions = mutableListOf<Pair<String, () -> Unit>>()
    actions.add(Pair(getString(R.string.xt_video_display_mode), { showDisplayModeChooser() }))
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      actions.add(Pair(getString(R.string.pink_native_pip), { enterPipNow() }))
    }
    actions.add(Pair(getString(R.string.pink_native_play_on_tv), { requestCastFromNativePlayer() }))
    if (mode != MODE_LIVE) {
      actions.add(Pair(getString(R.string.xt_video_playback_speed), { showPlaybackSpeedChooser() }))
    }
    AlertDialog.Builder(this)
      .setTitle(R.string.xt_video_settings_title)
      .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
        actions[which].second.invoke()
      }
      .setOnDismissListener { playerView?.showController() }
      .show()
  }

  private fun requestCastFromNativePlayer() {
    val channel = if (mode == MODE_LIVE) channels.getOrNull(currentChannelIndex) else null
    val castKey = if (mode == MODE_LIVE) "live:" + (channel?.id ?: return) else contentKey
    if (castKey.isBlank()) return
    EventQueue.append(
      this,
      "xt:android-native-cast-request",
      JSONObject().apply {
        put("contentKey", castKey)
        put("mode", mode)
        if (channel != null) put("channelId", channel.id)
      }
    )
    // Return to the same web page to use the trusted, existing cast device picker.
    // The event queues only item IDs; trusted web cast code resolves the source.
    finish()
  }
"""
    _replace_once(root, NATIVE, original, updated)
    _replace_once(root, "src-tauri/gen/android/app/src/main/res/values/strings.xml",
        '    <string name="xt_video_display_stretch">Stretch</string>\n',
        '    <string name="xt_video_display_stretch">Stretch</string>\n'
        '    <string name="pink_native_pip">Picture-in-Picture</string>\n'
        '    <string name="pink_native_play_on_tv">Play on TV</string>\n')

    bridge = "src/scripts/lib/android-video-launcher.ts"
    _replace_once(root, bridge,
        '  | "xt:android-native-volume"\n',
        '  | "xt:android-native-volume"\n  | "xt:android-native-cast-request"\n')
    _replace_once(root, bridge,
        '    "xt:android-native-volume",\n',
        '    "xt:android-native-volume",\n    "xt:android-native-cast-request",\n')

    # A channel can change with the TV remote inside the native player. The
    # cast intent's current channelId (not its initial contentKey) is canonical.
    live = "src/scripts/stream/stream.ts"
    _replace_once(root, live,
        '''  subscribeAndroidNativeEvents((event) => {
    if (event.type !== "xt:android-native-channel-changed") return
''',
        '''  subscribeAndroidNativeEvents((event) => {
    if (event.type === "xt:android-native-cast-request") {
      if (event.payload?.mode !== "live" || !event.payload.channelId) return
      const channel = all.find((entry) => String(entry.id) === String(event.payload.channelId))
      if (!channel) return
      // Use the same authenticated source builder and receiver picker as the
      // Live "Play on TV" action: the event contains no media URL or credentials.
      void import("@/scripts/lib/tv-cast.ts").then(({ castLiveChannelToTv }) => {
        return castLiveChannelToTv({
          contentTitle: channel.name || null,
          title: channel.name || "",
          logo: channel.logo || undefined,
          buildSrc: () => buildChannelStreamUrl(channel),
          drm: streamDrmById.get(channel.id) || undefined,
          headers: streamHeadersById.get(channel.id) || undefined,
          preferNativeHls: isNativeHlsFallbackChannel(channel.id),
          stopLocal: releaseLocalPlaybackForHandoff,
          restoreLocal: () => { void play(channel.id, channel.name, "user") },
          liveContext: liveContextForChannelId(channel.id),
        })
      })
      return
    }
    if (event.type !== "xt:android-native-channel-changed") return
''')

    for kind, plural, entity in (("movie", "movies", "movie"), ("series", "series", "currentEpisode")):
        path = f"src/scripts/{plural}/detail.ts"
        _replace_once(root, path,
            "  launchAndroidNativeVodWithProgress,\n",
            "  launchAndroidNativeVodWithProgress,\n  subscribeAndroidNativeEvents,\n")
        _replace_once(root, path,
            "let pipBtnBound = false\n",
            "let pipBtnBound = false\n"
            "let pinkNativeCastTarget: { contentKey: string; playlistId: string } | null = null\n")

    movie = "src/scripts/movies/detail.ts"
    _replace_once(root, movie,
        '''      progressExtras: { title: movie.name, logo: movie.logo || null },
    })
    if (launched) return
''',
        '''      progressExtras: { title: movie.name, logo: movie.logo || null },
    })
    if (launched) {
      pinkNativeCastTarget = { contentKey: "vod:" + movie.id, playlistId: activePlaylistId }
      return
    }
''')
    _replace_once(root, movie,
        ''')

subscribeExternalPlayerExit(() => {
  if (!externalPresenceActive) return
''',
        ''')

// Only honour a cast requested by this exact native movie playback. Never
// cast a stale selection after the account or movie has changed.
subscribeAndroidNativeEvents((event) => {
  if (event.type !== "xt:android-native-cast-request") return
  const target = pinkNativeCastTarget
  if (!target || !movie || target.playlistId !== activePlaylistId ||
      target.contentKey !== event.payload?.contentKey ||
      target.contentKey !== "vod:" + movie.id) return
  pinkNativeCastTarget = null
  const button = document.getElementById("movie-detail-play-tv") as HTMLButtonElement | null
  if (button && !button.hidden && button.isConnected) button.click()
})

subscribeExternalPlayerExit(() => {
  if (!externalPresenceActive) return
''')

    series = "src/scripts/series/detail.ts"
    _replace_once(root, series,
        '''    })
    if (launched) return
  }

  let backend = getPlayerBackend()
''',
        '''    })
    if (launched) {
      pinkNativeCastTarget = { contentKey: "ep:" + episode.id, playlistId: activePlaylistId }
      return
    }
  }

  let backend = getPlayerBackend()
''')
    _replace_once(root, series,
        ''')

subscribeExternalPlayerExit(() => {
  if (!externalPresenceActive) return
''',
        ''')

// The native player returns here to reuse the existing episode-aware cast
// handler (including season/episode navigation), never a new cast transport.
subscribeAndroidNativeEvents((event) => {
  if (event.type !== "xt:android-native-cast-request") return
  const target = pinkNativeCastTarget
  if (!target || !currentEpisode || target.playlistId !== activePlaylistId ||
      target.contentKey !== event.payload?.contentKey ||
      target.contentKey !== "ep:" + currentEpisode.id) return
  pinkNativeCastTarget = null
  const button = document.getElementById("series-detail-play-tv") as HTMLButtonElement | null
  if (button && !button.hidden && button.isConnected) button.click()
})

subscribeExternalPlayerExit(() => {
  if (!externalPresenceActive) return
''')

    print("PINK087_NATIVE_LIVE_MOVIES_SERIES_PLAYBACK_ACTIONS=APPLIED")
