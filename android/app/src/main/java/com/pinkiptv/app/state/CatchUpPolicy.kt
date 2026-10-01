package com.pinkiptv.app.state

import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpgChannel
import com.pinkiptv.app.model.EpgProgramme
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object CatchUpPolicy {
    private val providerId = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val providerStart = Regex(
        "^(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})$",
    )

    fun createRef(
        channel: EpgChannel,
        programme: EpgProgramme,
        nowEpochSeconds: Long,
    ): CatchUpPlaybackRef? {
        if (!providerId.matches(channel.streamId)) return null
        if (!channel.tvArchive || !programme.hasArchive) return null

        val startTimestamp = programme.startTimestamp?.takeIf { it > 0L } ?: return null
        val stopTimestamp = programme.stopTimestamp?.takeIf { it > startTimestamp } ?: return null

        // Future and currently-airing programmes are not Catch Up candidates.
        if (startTimestamp >= nowEpochSeconds || stopTimestamp > nowEpochSeconds) return null

        val durationMinutes = durationMinutes(startTimestamp, stopTimestamp) ?: return null
        val normalizedStart = normalizeProviderStart(programme.startProvider) ?: return null

        return CatchUpPlaybackRef(
            streamId = channel.streamId,
            title = programme.title,
            providerStart = normalizedStart,
            durationMinutes = durationMinutes,
        )
    }

    fun durationMinutes(startTimestamp: Long, stopTimestamp: Long): Int? {
        if (startTimestamp <= 0L || stopTimestamp <= startTimestamp) return null
        val seconds = stopTimestamp - startTimestamp
        val minutes = (seconds + 59L) / 60L
        return minutes
            .takeIf { it in 1L..1440L }
            ?.toInt()
    }

    fun normalizeProviderStart(value: String?): String? {
        val input = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val match = providerStart.matchEntire(input) ?: return null

        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        if (parser.parse(input, position) == null || position.index != input.length) return null

        val groups = match.groupValues
        return groups[1] + "-" + groups[2] + "-" + groups[3] + ":" +
            groups[4] + "-" + groups[5]
    }
}
