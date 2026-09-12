package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.LinkedSubtitle
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.rememberDialogFocusRestore
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Settings → OpenSubtitles account → Delete subtitles (subtitle plan §11). A Movies/Series toggle at
 * the top, the downloaded subtitles for the selected section below (tap to delete one), and a
 * Delete all action (all / all movies / all series).
 */
@Composable
fun DeleteSubtitlesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: DeleteSubtitlesViewModel = koinViewModel()
    val section by vm.section.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val movieCount by vm.movieCount.collectAsStateWithLifecycle()
    val seriesCount by vm.seriesCount.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf<LinkedSubtitle?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    // Land D-pad focus inside the screen (Movies/Series toggle) — without this it opens unfocused.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onBack() }

    // "Delete all" sits above the Movies/Series toggle that owns [firstFocus], so without this the
    // confirmation closing dropped focus onto the toggle instead of the button that opened it.
    val scrollState = rememberScrollState()
    val dialogFocus = rememberDialogFocusRestore(showDeleteAll, scrollState)
    val deleteAllFocus = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .verticalScroll(scrollState)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Title + Delete all (top-right).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Header(stringResource(R.string.settings_delete_subtitles), onBack) }
            if (movieCount + seriesCount > 0) {
                OwnTVButton(
                    stringResource(R.string.settings_delete_all),
                    onClick = { dialogFocus.value = deleteAllFocus; showDeleteAll = true },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(deleteAllFocus),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Movies / Series toggle (like Customize's section switch).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeleteSubtitlesViewModel.Section.entries.forEach { s ->
                val count = if (s == DeleteSubtitlesViewModel.Section.MOVIES) movieCount else seriesCount
                OwnTVButton(
                    stringResource(
                        R.string.settings_section_count,
                        if (s == DeleteSubtitlesViewModel.Section.MOVIES) stringResource(R.string.settings_movies) else stringResource(R.string.settings_series),
                        count,
                    ),
                    onClick = { vm.selectSection(s) },
                    style = if (s == section) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    modifier = if (s == DeleteSubtitlesViewModel.Section.MOVIES) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                stringResource(
                    R.string.settings_no_downloaded_subtitles,
                    if (section == DeleteSubtitlesViewModel.Section.MOVIES) stringResource(R.string.settings_movies).lowercase() else stringResource(R.string.settings_series).lowercase(),
                ),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            items.forEach { item ->
                val displayTitle = item.displayTitle()
                Row2(
                    icon = OwnTVIcon.SUBTITLE,
                    title = displayTitle,
                    desc = listOfNotNull(item.languageName ?: item.language, item.releaseName).joinToString(stringResource(R.string.content_metadata_separator)),
                    chip = stringResource(R.string.common_delete), primaryChip = false,
                    onClick = { confirmDelete = item },
                )
            }
        }
    }

    confirmDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_subtitle),
            message = stringResource(
                R.string.settings_delete_subtitle_message,
                item.languageName ?: item.language ?: item.fileName,
                item.displayTitle(),
            ),
            onConfirm = { vm.deleteOne(item); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }

    if (showDeleteAll) {
        PickerDialog(
            title = stringResource(R.string.settings_delete_all_subtitles),
            options = listOf(
                "ALL" to stringResource(R.string.settings_delete_all),
                "MOVIES" to stringResource(R.string.settings_delete_all_movies),
                "SERIES" to stringResource(R.string.settings_delete_all_series),
            ),
            selected = "",
            onSelect = { choice ->
                when (choice) {
                    "ALL" -> vm.deleteAll()
                    "MOVIES" -> vm.deleteAllMovies()
                    "SERIES" -> vm.deleteAllSeries()
                }
                showDeleteAll = false
            },
            onDismiss = { showDeleteAll = false },
        )
    }
}

@Composable
private fun LinkedSubtitle.displayTitle(): String {
    val episodeTitle = episodeDisplayTitleParts(mediaType, contentTitle, contentKey) ?: return contentTitle
    return stringResource(
        R.string.player_episode_context_title,
        episodeTitle.baseTitle,
        episodeTitle.season,
        episodeTitle.episode,
    )
}

internal data class EpisodeDisplayTitleParts(
    val baseTitle: String,
    val season: Int,
    val episode: Int,
)

/**
 * Normalizes subtitle links written by both schema generations. Pre-i18n rows persisted the English
 * display suffix in [contentTitle]; current rows persist only the raw series title. Strip the old
 * suffix only when its numbers exactly match [contentKey], then let Compose format it for the locale.
 */
internal fun episodeDisplayTitleParts(
    mediaType: String,
    contentTitle: String,
    contentKey: String,
): EpisodeDisplayTitleParts? {
    if (mediaType != "SERIES") return null
    val match = Regex(":S(\\d+)E(\\d+)$").find(contentKey) ?: return null
    val season = match.groupValues[1].toInt()
    val episode = match.groupValues[2].toInt()
    val legacySuffix = " · S${season}E${episode}"
    return EpisodeDisplayTitleParts(
        baseTitle = contentTitle.removeSuffix(legacySuffix),
        season = season,
        episode = episode,
    )
}
