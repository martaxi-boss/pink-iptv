package com.pinkiptv.extreme

import android.view.View
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

/** User-visible track options on the existing Media3 controller for Live and VOD. */
@androidx.annotation.OptIn(UnstableApi::class)
object PinkVodTracks {
    fun attach(view: PlayerView, player: Player) {
        // Media3 already owns this controller button's visibility with the rest of
        // the full-screen controls. Reuse it as one stable, remote-focusable entry
        // point instead of adding a permanent overlay above video or channel UI.
        view.setShowSubtitleButton(true)
        val options = requireNotNull(
            view.findViewById<View>(androidx.media3.ui.R.id.exo_subtitle)
        ) { "Media3 subtitle control is required for PINK track options" }

        fun update() {
            val available = hasTracks(player, C.TRACK_TYPE_AUDIO) ||
                hasTracks(player, C.TRACK_TYPE_TEXT)
            options.visibility = View.VISIBLE
            options.isEnabled = available
            options.isFocusable = available
            options.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            options.contentDescription = view.context.getString(R.string.pink_player_options)
        }

        fun show(type: Int) {
            if (!hasTracks(player, type)) return
            TrackSelectionDialogBuilder(view.context,
                view.context.getString(if (type == C.TRACK_TYPE_AUDIO)
                    R.string.pink_vod_audio else R.string.pink_vod_subtitles), player, type)
                .setIsDisabled(player.trackSelectionParameters.disabledTrackTypes.contains(type))
                .setShowDisableOption(type == C.TRACK_TYPE_TEXT)
                .setAllowAdaptiveSelections(false)
                .setAllowMultipleOverrides(false)
                .build().apply {
                    setOnDismissListener {
                        view.showController()
                        options.requestFocus()
                    }
                    show()
                }
        }

        options.setOnClickListener {
            val types = listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)
                .filter { hasTracks(player, it) }
            if (types.isEmpty()) return@setOnClickListener
            val labels = types.map { type ->
                view.context.getString(if (type == C.TRACK_TYPE_AUDIO)
                    R.string.pink_vod_audio else R.string.pink_vod_subtitles)
            }.toTypedArray()
            android.app.AlertDialog.Builder(view.context)
                .setTitle(R.string.pink_player_options)
                .setItems(labels) { _, index -> show(types[index]) }
                .setOnDismissListener {
                    view.showController()
                    options.requestFocus()
                }
                .show()
        }
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { view.post { update() } }
        })
        update()
    }

    private fun hasTracks(player: Player, type: Int): Boolean =
        player.currentTracks.groups.any { it.type == type && it.length > 0 }
}
