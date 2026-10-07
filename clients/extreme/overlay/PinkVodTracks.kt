package com.pinkiptv.extreme

import android.view.View
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

/** VOD-only selectors on the existing controller, including forced/unsupported real tracks. */
@androidx.annotation.OptIn(UnstableApi::class)
object PinkVodTracks {
    fun attach(view: PlayerView, player: Player) {
        val audio = view.findViewById<View>(androidx.media3.ui.R.id.exo_audio_track)
        val text = view.findViewById<View>(androidx.media3.ui.R.id.exo_subtitle)
        fun update() {
            audio?.isEnabled = hasTracks(player, C.TRACK_TYPE_AUDIO)
            text?.isEnabled = hasTracks(player, C.TRACK_TYPE_TEXT)
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
                    setOnDismissListener { view.showController() }
                    show()
                }
        }
        audio?.setOnClickListener { show(C.TRACK_TYPE_AUDIO) }
        text?.setOnClickListener { show(C.TRACK_TYPE_TEXT) }
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { view.post { update() } }
        })
        update()
    }

    private fun hasTracks(player: Player, type: Int): Boolean =
        player.currentTracks.groups.any { it.type == type && it.length > 0 }
}
