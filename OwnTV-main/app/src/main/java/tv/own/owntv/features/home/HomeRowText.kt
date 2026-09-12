package tv.own.owntv.features.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow

@Composable
fun HomeRow.displayTitle(): String = stringResource(
    when (this) {
        HomeRow.TRENDING -> R.string.home_row_now_trending
        HomeRow.HERO -> R.string.home_row_keep_watching
        HomeRow.RECENT_CHANNELS -> R.string.home_row_recent_channels
        HomeRow.FAVORITE_CHANNELS -> R.string.home_row_favorite_channels
        HomeRow.CONTINUE_MOVIES -> R.string.home_row_continue_movies
        HomeRow.CONTINUE_SERIES -> R.string.home_row_continue_series
    },
)

@Composable
fun HomeRow.settingsDescription(): String = stringResource(
    when (this) {
        HomeRow.TRENDING -> R.string.home_row_trending_description
        HomeRow.HERO -> R.string.home_row_hero_description
        HomeRow.RECENT_CHANNELS -> R.string.home_row_recent_description
        HomeRow.FAVORITE_CHANNELS -> R.string.home_row_favorite_description
        HomeRow.CONTINUE_MOVIES -> R.string.home_row_continue_movies_description
        HomeRow.CONTINUE_SERIES -> R.string.home_row_continue_series_description
    },
)

@Composable
fun HomeLiveRowMode.displayLabel(): String = stringResource(
    when (this) {
        HomeLiveRowMode.CARDS -> R.string.home_row_cards
        HomeLiveRowMode.ON_NOW -> R.string.home_row_on_now
    },
)
