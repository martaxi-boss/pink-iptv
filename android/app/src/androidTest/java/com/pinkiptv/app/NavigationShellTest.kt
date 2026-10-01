package com.pinkiptv.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogPhase
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpgChannel
import com.pinkiptv.app.model.EpgPhase
import com.pinkiptv.app.model.EpgProgramme
import com.pinkiptv.app.model.EpgProgrammeUi
import com.pinkiptv.app.model.EpgUiState
import com.pinkiptv.app.model.EpisodePlaybackRef
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.SeriesDetailPhase
import com.pinkiptv.app.model.SeriesDetailUiState
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.SeriesSeason
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.ui.navigation.AuthenticatedShell
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Rule
import org.junit.Test

class NavigationShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveCatalogNavigatesAndBackReturnsHome() {
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            PinkTheme {
                AuthenticatedShell(
                    liveCatalog = CatalogUiState(
                        kind = CatalogKind.Live,
                        phase = CatalogPhase.Loading,
                    ),
                    movieCatalog = CatalogUiState(CatalogKind.Movies),
                    seriesCatalog = CatalogUiState(CatalogKind.Series),
                    onLoadCatalog = {},
                    onSelectCatalogCategory = { _, _ -> },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_live").performClick()
        composeRule.onNodeWithText("TV ao Vivo").assertExists()
        composeRule.onNodeWithTag("catalog_loading").assertIsDisplayed()

        composeRule.runOnIdle {
            requireNotNull(backDispatcher).onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_live").assertExists()
    }

    @Test
    fun liveMovieAndSeriesEpisodeNavigationStayDeterministic() {
        val factory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Playing,
                title = "Playback",
                kind = PlaybackKind.Live,
                isPlaying = true,
            ),
        )

        composeRule.setContent {
            var selected by remember { mutableStateOf<PlaybackRef?>(null) }
            PinkTheme {
                AuthenticatedShell(
                    liveCatalog = contentState(
                        kind = CatalogKind.Live,
                        item = CatalogUiItem(
                            id = "live-1",
                            name = "Live One",
                            categoryId = null,
                            artworkUrl = null,
                            subtitle = "live",
                            playbackRef = LivePlaybackRef("101", "Live One"),
                        ),
                    ),
                    movieCatalog = contentState(
                        kind = CatalogKind.Movies,
                        item = CatalogUiItem(
                            id = "movie-1",
                            name = "Movie One",
                            categoryId = null,
                            artworkUrl = null,
                            subtitle = "mp4",
                            playbackRef = VodPlaybackRef("202", "Movie One", "mp4"),
                        ),
                    ),
                    seriesCatalog = contentState(
                        kind = CatalogKind.Series,
                        item = CatalogUiItem(
                            id = "series-1",
                            name = "Series One",
                            categoryId = null,
                            artworkUrl = null,
                            subtitle = null,
                            playbackRef = null,
                        ),
                    ),
                    seriesDetail = SeriesDetailUiState(
                        phase = SeriesDetailPhase.Content,
                        selectedSeriesId = "series-1",
                        title = "Series One",
                        seasons = listOf(
                            SeriesSeason("1", "Season 1", 1, null),
                        ),
                        selectedSeasonId = "1",
                        episodes = listOf(
                            SeriesEpisode(
                                episodeId = "episode-1",
                                episodeNumber = "1",
                                title = "Episode One",
                                seasonId = "1",
                                containerExtension = "mp4",
                                artworkUrl = null,
                                duration = null,
                                plot = null,
                            ),
                        ),
                    ),
                    selectedPlayback = selected,
                    playbackFacadeFactory = factory,
                    onLoadCatalog = {},
                    onSelectCatalogCategory = { _, _ -> },
                    onOpenSeries = {},
                    onRetrySeriesDetail = {},
                    onSelectSeriesSeason = {},
                    onClearSeriesDetail = {},
                    onSelectPlayback = { selected = it.playbackRef },
                    onSelectEpisode = { episode ->
                        selected = EpisodePlaybackRef(
                            episodeId = episode.episodeId,
                            title = episode.title,
                            containerExtension = episode.containerExtension,
                        )
                    },
                    onClearPlayback = { selected = null },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_live").performClick()
        composeRule.onNodeWithTag("catalog_item_live-1").performClick()
        composeRule.onNodeWithTag("player_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("player_play_pause").assertIsDisplayed()
        composeRule.onNodeWithTag("player_back").performClick()
        composeRule.onNodeWithTag("catalog_item_live-1").assertIsDisplayed()

        composeRule.onNodeWithTag("catalog_back").performClick()
        composeRule.onNodeWithTag("home_movies").performClick()
        composeRule.onNodeWithTag("catalog_item_movie-1").performClick()
        composeRule.onNodeWithTag("player_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("player_back").performClick()
        composeRule.onNodeWithTag("catalog_item_movie-1").assertIsDisplayed()

        composeRule.onNodeWithTag("catalog_back").performClick()
        composeRule.onNodeWithTag("home_series").performClick()
        composeRule.onNodeWithTag("catalog_item_series-1").performClick()
        composeRule.onNodeWithTag("series_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("series_episode_episode-1").performClick()
        composeRule.onNodeWithTag("player_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("player_back").performClick()
        composeRule.onNodeWithTag("series_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("series_episode_episode-1").assertIsDisplayed()
    }


    @Test
    fun epgCatchUpUsesExistingPlayerAndBackReturnsToEpg() {
        val factory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Playing,
                title = "Archived",
                kind = PlaybackKind.CatchUp,
                isPlaying = true,
                seekable = true,
            ),
        )
        val catchUp = CatchUpPlaybackRef(
            streamId = "10",
            title = "Archived",
            providerStart = "2026-09-30:12-34",
            durationMinutes = 30,
        )
        val epg = EpgUiState(
            phase = EpgPhase.Content,
            channels = listOf(
                EpgChannel(
                    streamId = "10",
                    name = "Channel",
                    epgChannelId = "epg-10",
                    tvArchive = true,
                    tvArchiveDurationDays = 7,
                ),
            ),
            selectedChannelId = "10",
            programmes = listOf(
                EpgProgrammeUi(
                    key = "archived-0",
                    programme = EpgProgramme(
                        programmeId = "archived",
                        title = "Archived",
                        description = null,
                        startProvider = "2026-09-30 12:34:56",
                        endProvider = "2026-09-30 13:04:56",
                        startTimestamp = 1000L,
                        stopTimestamp = 2800L,
                        nowPlaying = false,
                        hasArchive = true,
                    ),
                    isCurrent = false,
                    catchUpRef = catchUp,
                ),
            ),
        )

        composeRule.setContent {
            var selected by remember { mutableStateOf<PlaybackRef?>(null) }
            PinkTheme {
                AuthenticatedShell(
                    liveCatalog = CatalogUiState(CatalogKind.Live),
                    movieCatalog = CatalogUiState(CatalogKind.Movies),
                    seriesCatalog = CatalogUiState(CatalogKind.Series),
                    epgState = epg,
                    selectedPlayback = selected,
                    playbackFacadeFactory = factory,
                    onLoadCatalog = {},
                    onSelectCatalogCategory = { _, _ -> },
                    onOpenEpg = {},
                    onSelectEpgChannel = {},
                    onRetryEpg = {},
                    onClearEpg = {},
                    onSelectCatchUp = { selected = it },
                    onClearPlayback = { selected = null },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_epg").performClick()
        composeRule.onNodeWithTag("epg_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("epg_catchup_archived-0").performClick()
        composeRule.onNodeWithTag("player_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("player_back").performClick()
        composeRule.onNodeWithTag("epg_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("epg_catchup_archived-0").assertIsDisplayed()
    }

    private fun contentState(
        kind: CatalogKind,
        item: CatalogUiItem,
    ) = CatalogUiState(
        kind = kind,
        phase = CatalogPhase.Content,
        items = listOf(item),
    )
}
