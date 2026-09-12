package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.sync.EpgActivityTracker
import tv.own.owntv.core.sync.SyncActivityTracker
import tv.own.owntv.core.sync.TrendingActivityTracker
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.util.classifySyncFailure
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.compactCount
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass

/**
 * Unobtrusive "sync running" pill (shell overlay, bottom middle). Reflects BOTH catalog syncs (a
 * backgrounded first import, the movies/series remainder worker, auto refresh — all funnel through
 * SyncManager → [SyncActivityTracker]) AND EPG/guide syncs (manual resync from Settings, auto startup
 * refresh — [EpgSyncWorker] → [EpgActivityTracker]). Semi-transparent, never focusable, renders nothing
 * when no sync is active. The shell hides it during fullscreen playback.
 *
 * **One line per running sync.** It used to show a single line for whichever sync happened to be
 * first, with the rest collapsed into "(+2 more) · EPG too" — so with a playlist and a guide running
 * together you could see neither one's progress. Two playlists syncing at once were entirely
 * indistinguishable. Now every active sync gets its own row with its own counter, capped at
 * [MAX_ROWS] so a many-source setup can't grow the overlay across the screen.
 */
@Composable
fun SyncStatusPill(modifier: Modifier = Modifier) {
    val catalogTracker: SyncActivityTracker = koinInject()
    val epgTracker: EpgActivityTracker = koinInject()
    val trendingTracker: TrendingActivityTracker = koinInject()
    val connectivity: ConnectivityObserver = koinInject()
    val activeCatalog by catalogTracker.active.collectAsStateWithLifecycle()
    val activeEpg by epgTracker.active.collectAsStateWithLifecycle()
    val activeTrending by trendingTracker.active.collectAsStateWithLifecycle()
    val lastCompleted by catalogTracker.lastCompleted.collectAsStateWithLifecycle()
    val lastTrendingCompleted by trendingTracker.lastCompleted.collectAsStateWithLifecycle()

    val colors = OwnTVTheme.colors

    val completedQueue = remember { mutableStateListOf<SyncActivityTracker.CompletedSync>() }
    var currentCompleted by remember { mutableStateOf<SyncActivityTracker.CompletedSync?>(null) }
    var currentTrendingCompleted by remember { mutableStateOf<TrendingActivityTracker.CompletedBuild?>(null) }

    val anyActive = activeCatalog.isNotEmpty() || activeEpg.isNotEmpty() || activeTrending.isNotEmpty()

    LaunchedEffect(lastCompleted) {
        val completed = lastCompleted ?: return@LaunchedEffect
        if (completedQueue.none { it.timestamp == completed.timestamp && it.sourceId == completed.sourceId }) {
            completedQueue.add(completed)
        }
    }

    LaunchedEffect(lastTrendingCompleted) {
        val completed = lastTrendingCompleted ?: return@LaunchedEffect
        currentTrendingCompleted = completed
        trendingTracker.consumeCompleted(completed.timestamp)
    }

    LaunchedEffect(currentCompleted, completedQueue.size) {
        if (currentCompleted == null && completedQueue.isNotEmpty()) {
            val next = completedQueue.removeAt(0)
            currentCompleted = next
            // Keep the just-finished catalog row visible while its follow-up Trending build runs;
            // this makes the two backend phases readable together instead of replacing one another.
            catalogTracker.consumeCompleted(next.timestamp)
        }
    }

    LaunchedEffect(currentCompleted) {
        if (currentCompleted != null) {
            delay(5000)
            currentCompleted = null
        }
    }

    LaunchedEffect(currentTrendingCompleted) {
        if (currentTrendingCompleted != null) {
            delay(5000)
            currentTrendingCompleted = null
        }
    }

    if (!anyActive && currentCompleted == null && currentTrendingCompleted == null) return

    // Catalog syncs first (they're the slower, more interesting ones), then guides, in a stable
    // order so a row doesn't jump around as progress updates arrive.
    val rows = buildList<SyncLine> {
        activeCatalog.values.sortedBy { it.sourceId }.forEach { add(SyncLine.Catalog(it)) }
        activeTrending.values.sortedBy { it.sourceId }.forEach {
            add(SyncLine.Trending(it))
            add(SyncLine.TrendingDetail(it))
        }
        activeEpg.values.sortedBy { it.sourceId }.forEach { add(SyncLine.Epg(it)) }
    }
    val shown = rows.take(MAX_ROWS)
    val hidden = rows.size - shown.size
    val lineCount = shown.size + (if (hidden > 0) 1 else 0) +
        (if (currentCompleted != null) 1 else 0) + (if (currentTrendingCompleted != null) 2 else 0)

    // A tall stack under a 50% corner radius reads as a lozenge, not a pill — soften to a rounded
    // card as soon as there's more than one line.
    val radius = if (lineCount > 1) 18.dp else 50.dp
    val shape = RoundedCornerShape(radius)

    Column(
        modifier = modifier
            .padding(bottom = 14.dp)
            .widthIn(max = 620.dp)
            .clip(shape)
            // Glass effect: the pill is small chrome like the top-bar chips, so it frosts with
            // TOPBAR and takes a lighter frost than a full panel. Off glass it falls back to the
            // same translucent fill it always had.
            .glass(
                surface = GlassSurface.TOPBAR,
                baseFill = colors.surfaceContainerHigh.copy(alpha = 0.72f),
                shape = shape,
                frostScale = 0.8f,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        shown.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OwnTVSpinner(sizeDp = 14)
                Text(
                    line.text(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hidden > 0) {
            Text(
                pluralStringResource(R.plurals.sync_status_more, hidden, hidden),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        currentCompleted?.let { completed ->
            Text(
                completedLine(completed, online = connectivity.isOnlineNow()),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        currentTrendingCompleted?.let { completed ->
            Text(
                trendingCompletedLine(completed),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                trendingCompletedDetailLine(completed),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The `|| count > 0` on each active flag mirrors ManageSourcesScreen: the syncer clears a phase's
 * active flag when it moves on, and without this a finished phase's count would vanish from the line
 * mid-sync. Counts are cumulative, so a type that never runs (M3U movies/series) stays at 0 and is
 * left out.
 */
private sealed interface SyncLine {
    data class Catalog(val sync: SyncActivityTracker.ActiveSync) : SyncLine
    data class Trending(val build: TrendingActivityTracker.ActiveBuild) : SyncLine
    data class TrendingDetail(val build: TrendingActivityTracker.ActiveBuild) : SyncLine
    data class Epg(val sync: EpgActivityTracker.ActiveEpgSync) : SyncLine
}

@Composable
private fun SyncLine.text(): String = when (this) {
    is SyncLine.Catalog -> {
        val stage = sync.stage
        val counts = stage?.let {
            SyncProgressCounts(
                live = it.liveProcessed,
                movies = it.moviesProcessed,
                series = it.seriesProcessed,
                liveActive = it.liveActive || it.liveProcessed > 0,
                moviesActive = it.moviesActive || it.moviesProcessed > 0,
                seriesActive = it.seriesActive || it.seriesProcessed > 0,
            )
        }
        val countsText = counts?.displayText().orEmpty()
        if (countsText.isBlank()) stringResource(R.string.sync_status_catalog, sync.sourceName)
        else stringResource(R.string.sync_status_catalog_with_counts, sync.sourceName, countsText)
    }
    is SyncLine.Epg -> {
        val context = LocalContext.current
        val countParts = buildList {
            if (sync.channels > 0) add(pluralStringResource(R.plurals.sync_count_channels, sync.channels, compactCount(context, sync.channels)))
            if (sync.programmes > 0) add(pluralStringResource(R.plurals.sync_count_epg, sync.programmes, compactCount(context, sync.programmes)))
        }
        val countsText = countParts.joinToString(stringResource(R.string.sync_counts_separator))
        if (countsText.isBlank()) stringResource(R.string.sync_status_epg, sync.sourceName)
        else stringResource(R.string.sync_status_epg_with_counts, sync.sourceName, countsText)
    }
    is SyncLine.Trending -> when (build.stage) {
        TrendingActivityTracker.Stage.STARTING -> stringResource(R.string.sync_status_trending_starting, build.sourceName)
        TrendingActivityTracker.Stage.RECEIVED -> pluralStringResource(
            R.plurals.sync_status_trending_received,
            build.candidates,
            build.candidates,
        )
        TrendingActivityTracker.Stage.PREPARING -> stringResource(R.string.sync_status_trending_preparing)
        TrendingActivityTracker.Stage.MATCHING_MOVIES -> stringResource(R.string.sync_status_trending_matching_movies)
        TrendingActivityTracker.Stage.MATCHING_SERIES -> stringResource(R.string.sync_status_trending_matching_series)
        TrendingActivityTracker.Stage.LOADING_SEASONS -> stringResource(R.string.sync_status_trending_loading_seasons)
        TrendingActivityTracker.Stage.ENRICHING -> pluralStringResource(
            R.plurals.sync_status_trending_building,
            build.matched,
            build.matched,
        )
        TrendingActivityTracker.Stage.PUBLISHING -> pluralStringResource(
            R.plurals.sync_status_trending_publishing,
            build.matched,
            build.matched,
        )
    }
    is SyncLine.TrendingDetail -> when (build.stage) {
        TrendingActivityTracker.Stage.STARTING -> stringResource(R.string.sync_status_trending_detail_waiting)
        TrendingActivityTracker.Stage.RECEIVED -> stringResource(
            R.string.sync_status_trending_detail_received,
            build.movieCandidates,
            build.seriesCandidates,
        )
        TrendingActivityTracker.Stage.PREPARING -> stringResource(
            R.string.sync_status_trending_detail_preparing,
            build.preparationProcessed,
            build.preparationTotal,
        )
        TrendingActivityTracker.Stage.MATCHING_MOVIES -> stringResource(
            R.string.sync_status_trending_detail_movies,
            build.movieChecked,
            build.movieCandidates,
            build.movieMatches,
            build.movieTarget,
        )
        TrendingActivityTracker.Stage.MATCHING_SERIES -> stringResource(
            R.string.sync_status_trending_detail_series,
            build.seriesChecked,
            build.seriesCandidates,
            build.seriesMatches,
            build.seriesTarget,
        )
        TrendingActivityTracker.Stage.LOADING_SEASONS -> stringResource(
            R.string.sync_status_trending_detail_loading_seasons,
            build.seasonsProcessed,
            build.seasonsTotal,
        )
        TrendingActivityTracker.Stage.ENRICHING,
        TrendingActivityTracker.Stage.PUBLISHING -> stringResource(
            R.string.sync_status_trending_detail_selected,
            build.movieMatches,
            build.seriesMatches,
            build.finalItems,
        )
    }
}

@Composable
private fun completedLine(completed: SyncActivityTracker.CompletedSync, online: Boolean): String = when (val result = completed.result) {
    is SyncResult.Success -> {
        val changes = buildList {
            if (result.categoriesAdded > 0) {
                add(pluralStringResource(R.plurals.sync_categories_added, result.categoriesAdded, result.categoriesAdded))
            }
            if (result.categoriesRemoved > 0) {
                add(pluralStringResource(R.plurals.sync_categories_removed, result.categoriesRemoved, result.categoriesRemoved))
            }
        }.joinToString(stringResource(R.string.sync_counts_separator))
        if (changes.isBlank()) stringResource(R.string.sync_status_complete, completed.sourceName)
        else stringResource(R.string.sync_status_complete_with_changes, completed.sourceName, changes)
    }
    is SyncResult.Failed -> stringResource(
        R.string.sync_status_failed,
        completed.sourceName,
        classifySyncFailure(result.message, online).displayText(),
    )
    SyncResult.Cancelled -> stringResource(R.string.sync_status_cancelled, completed.sourceName)
}

@Composable
private fun trendingCompletedLine(completed: TrendingActivityTracker.CompletedBuild): String = when {
    completed.preservedFailure -> stringResource(R.string.sync_status_trending_preserved)
    completed.eligible -> pluralStringResource(
        R.plurals.sync_status_trending_ready,
        completed.itemCount,
        completed.itemCount,
    )
    else -> pluralStringResource(
        R.plurals.sync_status_trending_below_minimum,
        completed.itemCount,
        completed.itemCount,
    )
}

@Composable
private fun trendingCompletedDetailLine(completed: TrendingActivityTracker.CompletedBuild): String = stringResource(
    R.string.sync_status_trending_detail_completed,
    completed.movieMatches,
    completed.movieCandidates,
    completed.seriesMatches,
    completed.seriesCandidates,
)


/** Beyond this many concurrent syncs the pill summarises the rest, rather than covering the screen. */
private const val MAX_ROWS = 4
