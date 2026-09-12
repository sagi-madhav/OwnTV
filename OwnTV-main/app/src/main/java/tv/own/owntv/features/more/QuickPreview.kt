package tv.own.owntv.features.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.settings.VIDEO_QUICK_ROWS
import tv.own.owntv.features.settings.videoQuickBinding
import tv.own.owntv.ui.components.OwnTVIcon

/** One pinned Quick row as the More pane shows it: what it is, and what it is set to. */
data class QuickPreviewRow(val icon: OwnTVIcon, val label: String, val value: String?)

/**
 * The user's pinned Quick rows, with their **live values**, for More's Settings pane.
 *
 * There are exactly two kinds of pinnable row and this resolves both:
 *
 * - the **six root rows** (`quick_*`), whose values are plain flows on [SettingsViewModel]; and
 * - the **Video player rows** (`vp_*`), which already have a shared resolver —
 *   [videoQuickBinding] — that `SettingsScreen` itself uses to build the same chips.
 *
 * That is the whole pinnable set, so this is complete rather than a sample: pinning anything else is
 * not possible. It exists because `SettingsScreen`'s own row model (`RootRow`) is private and built
 * inline from forty-odd collected flows — reusing it would mean extracting all of it, and duplicating
 * it would mean two lists that drift. Six keys plus one existing resolver is the small version.
 *
 * Order follows [SettingsViewModel.quickPinnedKeys], which is the order the user pinned them in and
 * the order Settings shows them.
 */
@Composable
fun quickPreviewRows(settingsVm: SettingsViewModel): List<QuickPreviewRow> {
    val pinned by settingsVm.quickPinnedKeys.collectAsStateWithLifecycle()
    if (pinned.isEmpty()) return emptyList()

    val livePreview by settingsVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val previewAudio by settingsVm.livePreviewAudio.collectAsStateWithLifecycle()
    val channelNumbers by settingsVm.directTune.collectAsStateWithLifecycle()
    val hdr by settingsVm.hdrEnabled.collectAsStateWithLifecycle()
    val autoPlayNext by settingsVm.autoPlayNext.collectAsStateWithLifecycle()
    val updateCheck by settingsVm.updateCheckOnStart.collectAsStateWithLifecycle()

    val on = stringResource(R.string.common_on)
    val off = stringResource(R.string.common_off)
    fun onOff(value: Boolean) = if (value) on else off

    val roots: Map<String, QuickPreviewRow> = mapOf(
        "quick_live_preview" to QuickPreviewRow(
            OwnTVIcon.LIVE_TV, stringResource(R.string.settings_quick_live_preview), onOff(livePreview),
        ),
        "quick_preview_sound" to QuickPreviewRow(
            OwnTVIcon.AUDIO, stringResource(R.string.settings_quick_preview_sound), onOff(previewAudio),
        ),
        "quick_channel_numbers" to QuickPreviewRow(
            OwnTVIcon.LIVE_TV, stringResource(R.string.settings_quick_channel_numbers), onOff(channelNumbers),
        ),
        "quick_hdr" to QuickPreviewRow(
            OwnTVIcon.VIDEO, stringResource(R.string.settings_quick_hdr), onOff(hdr),
        ),
        "quick_autoplay" to QuickPreviewRow(
            OwnTVIcon.AUTOPLAY_NEXT, stringResource(R.string.settings_quick_autoplay), onOff(autoPlayNext),
        ),
        "quick_check_update" to QuickPreviewRow(
            OwnTVIcon.DOWNLOADS, stringResource(R.string.settings_quick_check_update), onOff(updateCheck),
        ),
    )

    val video = VIDEO_QUICK_ROWS.associateBy { it.key }
    return pinned.mapNotNull { key ->
        roots[key] ?: video[key]?.let { ref ->
            // The same resolver the Settings root uses for these rows, so the value shown here and
            // the value shown there cannot disagree.
            QuickPreviewRow(ref.icon, stringResource(ref.titleRes), videoQuickBinding(key, settingsVm)?.chip)
        }
    }
}
