package tv.own.owntv.features.search

import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.content.SearchIntent
import tv.own.owntv.core.content.SearchResults
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.ChannelSearchResult
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.format.localizedInteger

/** One row in the flattened results list — drives both the list rows and the detail pane. */
@Composable
private fun SearchIntent.displayLabel(): String = stringResource(
    when (this) {
        SearchIntent.CONTINUE -> R.string.search_continue
        SearchIntent.UNWATCHED -> R.string.search_unwatched
        SearchIntent.CHANNELS -> R.string.search_channels
    },
)

private sealed interface SearchItem {
    data class ChannelItem(val row: ChannelSearchResult) : SearchItem
    data class MovieItem(val movie: MovieEntity) : SearchItem
    data class SeriesItem(val series: SeriesEntity) : SearchItem
}

/**
 * Phase 11 + Batch 5 — global search across channels, movies and series with a remote-first launcher
 * empty state (recent terms + Continue / Unwatched / Channels intents) and a list + detail layout so a
 * focused result shows its poster/plot/rating and a primary action without opening it. Back clears the
 * query/intent first, then leaves the screen.
 */
@Composable
fun SearchScreen(
    onFullscreen: () -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
    onChildFocused: () -> Unit,
    /** Tune a channel result through the shared Live TV path (Prefer HLS, the ExoPlayer→mpv ladder,
     *  compatibility-mode pins, the zap list). Null falls back to this screen's own minimal mpv-only
     *  play — kept only so the screen still works standalone in a preview/test host. */
    /** Required: every live tune in the app goes through the one shared path in LiveViewModel, so a
     *  channel gets the same Prefer HLS handling, ExoPlayer→mpv ladder, per-channel engine pin and
     *  external-player routing however the user reached it. */
    onPlayChannel: (ChannelEntity) -> Unit,
    vm: SearchViewModel = koinViewModel(),
    returnToHomeOnBack: Boolean = false,
    onReturnToHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val curated by vm.curatedResults.collectAsStateWithLifecycle()
    val intent by vm.intent.collectAsStateWithLifecycle()
    val recent by vm.recentSearches.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteChannelIds.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    // Back clears an active query/intent (returning to the launcher) before leaving the screen.
    BackHandler(enabled = returnToHomeOnBack || query.isNotBlank() || intent != null) {
        vm.setQuery("")
        vm.setIntent(null)
        if (returnToHomeOnBack) onReturnToHome()
        else runCatching { searchFocus.requestFocus() }
    }

    val searching = query.trim().length >= 2
    val shown = if (searching) results else curated

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel(fillColor = ContentPanelFill)
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
    ) {
        Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(14.dp))
        SearchBar(
            query = query,
            onQueryChange = vm::setQuery,
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
        )
        Spacer(Modifier.height(20.dp))

        when {
            !searching && intent == null ->
                LauncherEmptyState(
                    recent = recent,
                    onRecent = { vm.setQuery(it) },
                    onClearRecent = { vm.clearRecentSearches() },
                    onIntent = { vm.setIntent(it) },
                )
            searching && query.trim().length < 2 ->
                CenterHint(stringResource(R.string.search_minimum_query))
            shown.isEmpty ->
                CenterHint(
                    if (searching) stringResource(R.string.search_no_results, query.trim())
                    else stringResource(R.string.search_nothing_in_list, intent?.displayLabel() ?: stringResource(R.string.search_title)),
                )
            else -> ResultsWithDetail(
                results = shown,
                favoriteIds = favoriteIds,
                heading = if (searching) null else intent?.displayLabel(),
                // When a curated intent is chosen its chip disappears, so pull focus onto the first
                // result row (otherwise focus falls back to the sidebar). While typing, leave focus
                // in the search field so the keyboard keeps working.
                autoFocusFirst = !searching,
                onPlayChannel = { ch ->
                    // History + "recent search" bookkeeping stays here; playback goes to the one shared
                    // live path so a channel behaves the same however it was found.
                    vm.noteChannelPlayed(ch)
                    onPlayChannel(ch)
                },
                onToggleFavorite = { vm.toggleFavoriteChannel(it) },
                onPlayMovie = { vm.playMovie(it); if (!vm.externalPlayerOn.value) onFullscreen() },
                onOpenSeries = { vm.rememberCurrentQuery(); onOpenSeries(it) },
            )
        }
    }
}

@Composable
private fun LauncherEmptyState(
    recent: List<String>,
    onRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onIntent: (SearchIntent) -> Unit,
) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().focusGroup(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Intent launcher chips
        SectionLabel(stringResource(R.string.search_jump_to))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchIntent.entries.forEach { i ->
                PillChip(label = i.displayLabel(), tonal = true) { onIntent(i) }
            }
        }

        // Recent searches
        if (recent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(stringResource(R.string.search_recent))
                PillChip(label = stringResource(R.string.search_clear), tonal = false, onClick = onClearRecent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                recent.chunked(4).forEach { rowTerms ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowTerms.forEach { term ->
                            PillChip(label = term, tonal = false, icon = OwnTVIcon.HISTORY) { onRecent(term) }
                        }
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.search_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultsWithDetail(
    results: SearchResults,
    favoriteIds: Set<Long>,
    heading: String?,
    autoFocusFirst: Boolean,
    onPlayChannel: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onPlayMovie: (MovieEntity) -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
) {
    val items = remember(results) {
        buildList {
            results.channels.forEach { add(SearchItem.ChannelItem(it)) }
            results.movies.forEach { add(SearchItem.MovieItem(it)) }
            results.series.forEach { add(SearchItem.SeriesItem(it)) }
        }
    }
    var selected by remember { mutableStateOf<SearchItem?>(null) }
    val active = selected?.takeIf { it in items } ?: items.firstOrNull()
    val firstItem = items.firstOrNull()

    // Land focus on the first row when a curated intent's list appears (see call site).
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocusFirst, items.isNotEmpty()) {
        if (autoFocusFirst && items.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight().focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (heading != null) {
                item(key = "heading") { SectionLabel(heading) }
            }
            items.forEach { item ->
                when (item) {
                    is SearchItem.ChannelItem -> item(key = "c${item.row.channel.id}") {
                        ResultRow(
                            thumbUrl = item.row.channel.displayLogoUrl,
                            fallbackIcon = OwnTVIcon.LIVE_TV,
                            title = item.row.channel.name,
                            subtitle = channelDetail(item.row),
                            isFavorite = item.row.channel.id in favoriteIds,
                            focusRequester = if (item == firstItem) firstRowFocus else null,
                            onFocused = { selected = item },
                            onClick = { onPlayChannel(item.row.channel) },
                            onLongClick = { onToggleFavorite(item.row.channel) },
                        )
                    }
                    is SearchItem.MovieItem -> item(key = "m${item.movie.id}") {
                        ResultRow(
                            thumbUrl = item.movie.posterUrl,
                            fallbackIcon = OwnTVIcon.MOVIES,
                            title = item.movie.name,
                            subtitle = metaLine(item.movie.year, item.movie.rating, stringResource(R.string.search_movie)),
                            isFavorite = false,
                            focusRequester = if (item == firstItem) firstRowFocus else null,
                            onFocused = { selected = item },
                            onClick = { onPlayMovie(item.movie) },
                            onLongClick = null,
                        )
                    }
                    is SearchItem.SeriesItem -> item(key = "s${item.series.id}") {
                        ResultRow(
                            thumbUrl = item.series.posterUrl,
                            fallbackIcon = OwnTVIcon.SERIES,
                            title = item.series.name,
                            subtitle = metaLine(item.series.year, item.series.rating, stringResource(R.string.search_series)),
                            isFavorite = false,
                            focusRequester = if (item == firstItem) firstRowFocus else null,
                            onFocused = { selected = item },
                            onClick = { onOpenSeries(item.series) },
                            onLongClick = null,
                        )
                    }
                }
            }
        }

        DetailPane(
            item = active,
            onPlayChannel = onPlayChannel,
            onPlayMovie = onPlayMovie,
            onOpenSeries = onOpenSeries,
            modifier = Modifier.width(340.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun DetailPane(
    item: SearchItem?,
    onPlayChannel: (ChannelEntity) -> Unit,
    onPlayMovie: (MovieEntity) -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    if (item == null) {
        Box(modifier) {}
        return
    }
    val posterUrl: String?
    val icon: OwnTVIcon
    val title: String
    val subtitle: String?
    val plot: String?
    val actionLabel: String
    val action: () -> Unit
    when (item) {
        is SearchItem.ChannelItem -> {
            posterUrl = item.row.channel.displayLogoUrl; icon = OwnTVIcon.LIVE_TV
            title = item.row.channel.name; subtitle = channelDetail(item.row); plot = null
            actionLabel = stringResource(R.string.search_watch_live); action = { onPlayChannel(item.row.channel) }
        }
        is SearchItem.MovieItem -> {
            posterUrl = item.movie.posterUrl; icon = OwnTVIcon.MOVIES
            title = item.movie.name; subtitle = metaLine(item.movie.year, item.movie.rating, stringResource(R.string.search_movie)); plot = item.movie.plot
            actionLabel = stringResource(R.string.search_play); action = { onPlayMovie(item.movie) }
        }
        is SearchItem.SeriesItem -> {
            posterUrl = item.series.posterUrl; icon = OwnTVIcon.SERIES
            title = item.series.name; subtitle = metaLine(item.series.year, item.series.rating, stringResource(R.string.search_series)); plot = item.series.plot
            actionLabel = stringResource(R.string.search_open_series); action = { onOpenSeries(item.series) }
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLowest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(model = posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                OwnTVIcon(icon, tint = colors.onSurfaceVariant, modifier = Modifier.size(48.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelLarge, color = colors.primary, fontWeight = FontWeight.SemiBold)
        }
        if (!plot.isNullOrBlank()) {
            Text(plot, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        FocusableSurface(
            onClick = action,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentAlignment = Alignment.Center,
            surface = GlassSurface.CARDS,
        ) { focused ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focused) colors.primary else colors.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OwnTVIcon(OwnTVIcon.PLAY, tint = if (focused) colors.onPrimary else colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.onPrimary else colors.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    thumbUrl: String?,
    fallbackIcon: OwnTVIcon,
    title: String,
    subtitle: String?,
    isFavorite: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.CARDS,
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!thumbUrl.isNullOrBlank()) {
                    AsyncImage(model = thumbUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(fallbackIcon, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.primary else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.FAVORITE, tint = colors.favorite, filled = true, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PillChip(
    label: String,
    tonal: Boolean,
    icon: OwnTVIcon? = null,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { focused ->
        val fg = when {
            focused -> colors.onPrimary
            tonal -> colors.onPrimaryContainer
            else -> colors.onSurface
        }
        Row(
            modifier = Modifier
                .background(
                    when {
                        focused -> colors.primary
                        tonal -> colors.primaryContainer
                        else -> colors.surfaceContainerHigh
                    },
                    RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (icon != null) {
                OwnTVIcon(icon, tint = fg, modifier = Modifier.size(15.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = OwnTVTheme.colors.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant)
    }
}

/** "category · #number" so near-identical feeds stay distinguishable. */
@Composable
private fun channelDetail(row: ChannelSearchResult): String? =
    listOfNotNull(
        row.categoryName?.takeIf { it.isNotBlank() },
        row.channel.number?.let { stringResource(R.string.search_channel_number, it) },
    ).joinToString(stringResource(R.string.content_genres_separator)).takeIf { it.isNotBlank() }

@Composable
private fun metaLine(year: Int?, rating: Double?, type: String): String =
    listOfNotNull(type, year?.let { localizedInteger(it, grouping = false) }, rating?.let { stringResource(R.string.content_rating, it) })
        .joinToString(stringResource(R.string.content_genres_separator))
