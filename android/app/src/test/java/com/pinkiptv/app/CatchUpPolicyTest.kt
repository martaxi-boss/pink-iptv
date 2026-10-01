package com.pinkiptv.app

import com.pinkiptv.app.model.EpgChannel
import com.pinkiptv.app.model.EpgProgramme
import com.pinkiptv.app.state.CatchUpPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class CatchUpPolicyTest {
    @Test
    fun eligibleArchivedProgrammeCreatesTypedReferenceFromProviderEvidence() {
        val ref = CatchUpPolicy.createRef(
            channel = channel(tvArchive = true),
            programme = programme(
                startProvider = "2026-09-30 12:34:56",
                start = 1000L,
                stop = 1600L,
                archive = true,
            ),
            nowEpochSeconds = 2000L,
        )

        assertNotNull(ref)
        assertEquals("10", ref?.streamId)
        assertEquals("Programme", ref?.title)
        assertEquals("2026-09-30:12-34", ref?.providerStart)
        assertEquals(10, ref?.durationMinutes)
    }

    @Test
    fun catchUpEligibilityFailsClosedForMissingArchiveFutureCurrentAndInvalidTiming() {
        val archived = programme(
            startProvider = "2026-09-30 12:34:56",
            start = 1000L,
            stop = 1600L,
            archive = true,
        )

        assertNull(CatchUpPolicy.createRef(channel(false), archived, 2000L))
        assertNull(
            CatchUpPolicy.createRef(
                channel(true),
                archived.copy(hasArchive = false),
                2000L,
            ),
        )
        assertNull(
            CatchUpPolicy.createRef(
                channel(true),
                archived.copy(startTimestamp = 2100L, stopTimestamp = 2200L),
                2000L,
            ),
        )
        assertNull(
            CatchUpPolicy.createRef(
                channel(true),
                archived.copy(startTimestamp = 1900L, stopTimestamp = 2100L),
                2000L,
            ),
        )
        assertNull(
            CatchUpPolicy.createRef(
                channel(true),
                archived.copy(startProvider = "bad start"),
                2000L,
            ),
        )
        assertNull(
            CatchUpPolicy.createRef(
                channel(true),
                archived.copy(startTimestamp = null),
                2000L,
            ),
        )
        assertNull(
            CatchUpPolicy.createRef(
                channel(true),
                archived.copy(stopTimestamp = 1000L),
                2000L,
            ),
        )
    }

    @Test
    fun durationUsesCeilingMinutesAndEnforcesSafetyBounds() {
        assertEquals(1, CatchUpPolicy.durationMinutes(100L, 160L))
        assertEquals(2, CatchUpPolicy.durationMinutes(100L, 161L))
        assertEquals(1440, CatchUpPolicy.durationMinutes(100L, 86500L))
        assertNull(CatchUpPolicy.durationMinutes(100L, 86501L))
        assertNull(CatchUpPolicy.durationMinutes(100L, 100L))
        assertNull(CatchUpPolicy.durationMinutes(0L, 60L))
    }

    @Test
    fun providerStartFormattingIsStrictAndNeverUsesUiTime() {
        assertEquals(
            "2026-09-30:09-05",
            CatchUpPolicy.normalizeProviderStart("2026-09-30 09:05:59"),
        )
        assertNull(CatchUpPolicy.normalizeProviderStart("2026-09-30T09:05:59"))
        assertNull(CatchUpPolicy.normalizeProviderStart("2026-02-31 09:05:59"))
        assertNull(CatchUpPolicy.normalizeProviderStart("09:05"))
        assertNull(CatchUpPolicy.normalizeProviderStart(null))
    }

    private fun channel(tvArchive: Boolean) = EpgChannel(
        streamId = "10",
        name = "Channel",
        epgChannelId = "epg-10",
        tvArchive = tvArchive,
        tvArchiveDurationDays = 7,
    )

    private fun programme(
        startProvider: String,
        start: Long?,
        stop: Long?,
        archive: Boolean,
    ) = EpgProgramme(
        programmeId = "p1",
        title = "Programme",
        description = null,
        startProvider = startProvider,
        endProvider = "2026-09-30 12:44:56",
        startTimestamp = start,
        stopTimestamp = stop,
        nowPlaying = false,
        hasArchive = archive,
    )
}
