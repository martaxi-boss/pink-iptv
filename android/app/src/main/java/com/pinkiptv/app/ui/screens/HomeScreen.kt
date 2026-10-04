package com.pinkiptv.app.ui.screens

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinkiptv.app.R
import com.pinkiptv.app.ui.theme.PinkSoft
import com.pinkiptv.app.ui.theme.SurfaceRaised

data class HomeItem(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val testTag: String,
)

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, accountName: String? = null) {
    val items = remember {
        listOf(
            HomeItem("live", R.string.live_tv, Icons.Filled.LiveTv, "home_live"),
            HomeItem("movies", R.string.movies, Icons.Filled.Movie, "home_movies"),
            HomeItem("series", R.string.series, Icons.Filled.VideoLibrary, "home_series"),
            HomeItem("epg", R.string.epg, Icons.Filled.CalendarMonth, "home_epg"),
            HomeItem("favorites", R.string.favorites, Icons.Filled.Favorite, "home_favorites"),
            HomeItem("search", R.string.search, Icons.Filled.Search, "home_search"),
            HomeItem("settings", R.string.settings, Icons.Filled.Settings, "home_settings"),
        )
    }
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("O que vamos ver hoje?", style = MaterialTheme.typography.titleLarge)
            accountName?.let {
                Text("Conta: " + it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val columns = if (maxWidth >= 900.dp) 3 else 2
            val focusRequesters = remember(columns) {
                List(items.size) { FocusRequester() }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(items) { index, item ->
                    HomeCard(
                        item = item,
                        focusRequester = focusRequesters[index],
                        left = focusRequesters.getOrNull(index - 1)
                            .takeIf { index % columns != 0 },
                        right = focusRequesters.getOrNull(index + 1)
                            .takeIf { index % columns != columns - 1 },
                        up = focusRequesters.getOrNull(index - columns),
                        down = focusRequesters.getOrNull(index + columns),
                        requestInitialFocus = isTv && index == 0,
                        onClick = { onNavigate(item.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCard(
    item: HomeItem,
    focusRequester: FocusRequester,
    left: FocusRequester?,
    right: FocusRequester?,
    up: FocusRequester?,
    down: FocusRequester?,
    requestInitialFocus: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) PinkSoft else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .testTag(item.testTag)
            .focusRequester(focusRequester)
            .focusProperties {
                left?.let { this.left = it }
                right?.let { this.right = it }
                up?.let { this.up = it }
                down?.let { this.down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (focused) PinkSoft else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(item.titleRes),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
