package tv.own.owntv.features.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The in-app update dialog (used both from Settings → Check for updates and the automatic prompt).
 * Binds to [UpdateManager]'s state machine: checking → up-to-date / available → downloading.
 * [checkOnOpen] makes opening the dialog trigger a fresh check (the Settings path).
 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit, checkOnOpen: Boolean = false) {
    val manager: UpdateManager = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (checkOnOpen) manager.check()
    }
    LaunchedEffect(state) {
        if (state is UpdateManager.State.Available || state is UpdateManager.State.UpToDate || state is UpdateManager.State.Failed) {
            runCatching { focus.requestFocus() }
        }
    }
    BackHandler { onDismiss() }

    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 520.dp, corner = 20.dp, padding = 28.dp)) {
            Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))

            when (val s = state) {
                UpdateManager.State.Idle, UpdateManager.State.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OwnTVSpinner(sizeDp = 28)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                UpdateManager.State.UpToDate -> {
                    Text(
                        stringResource(R.string.update_latest, manager.currentVersion),
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, modifier = Modifier.focusRequester(focus))
                    }
                }
                is UpdateManager.State.Available -> {
                    Text(
                        stringResource(R.string.update_available_version, s.info.version, manager.currentVersion),
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                    )
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            renderReleaseNotes(s.info.notes, headingColor = colors.onSurface),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            // Cap to the screen (minus dialog chrome) so the Update/Later buttons stay reachable.
                            modifier = Modifier
                                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 260.dp).coerceIn(120.dp, 320.dp))
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnTVButton(stringResource(R.string.update_later), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                        Spacer(Modifier.weight(1f))
                        OwnTVButton(stringResource(R.string.update_now), onClick = { manager.downloadAndInstall() }, icon = OwnTVIcon.DOWNLOADS, modifier = Modifier.focusRequester(focus))
                    }
                }
                is UpdateManager.State.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OwnTVSpinner(sizeDp = 28)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.update_downloading, s.percent), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_installer),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    )
                }
                is UpdateManager.State.Failed -> {
                    Text(
                        updateFailureText(s.failure),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                        Spacer(Modifier.weight(1f))
                        OwnTVButton(stringResource(R.string.update_try_again), onClick = { manager.retry() }, modifier = Modifier.focusRequester(focus))
                    }
                }
            }
        }
    }
}

/**
 * Renders the minimal release notes (from CHANGELOG_APP.md, via the GitHub release body) for the update
 * dialog. Lightweight Markdown only — enough for our bullet-only format: `### ` section headers become
 * bold heading lines, `- ` bullets become "• ", and inline `**bold**` spans render bold. Everything else
 * is shown as-is. Not a full Markdown parser.
 */
private fun renderReleaseNotes(notes: String, headingColor: Color): AnnotatedString = buildAnnotatedString {
    val lines = notes.replace("\r\n", "\n").trim().split("\n")
    lines.forEachIndexed { index, raw ->
        if (index > 0) append("\n")
        val line = raw.trimEnd()
        when {
            line.startsWith("### ") ->
                withStyle(SpanStyle(color = headingColor, fontWeight = FontWeight.Bold)) {
                    appendInline(line.removePrefix("### ").trim())
                }
            line.startsWith("## ") ->
                withStyle(SpanStyle(color = headingColor, fontWeight = FontWeight.Bold)) {
                    appendInline(line.removePrefix("## ").trim())
                }
            line.startsWith("- ") -> { append("•  "); appendInline(line.removePrefix("- ")) }
            line.startsWith("* ") -> { append("•  "); appendInline(line.removePrefix("* ")) }
            else -> appendInline(line)
        }
    }
}

/** Appends [text], turning `**bold**` spans into actual bold runs (leaves other characters untouched). */
private fun AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) { append(text.substring(i)); break }
        val end = text.indexOf("**", start + 2)
        if (end < 0) { append(text.substring(i)); break }
        append(text.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start + 2, end)) }
        i = end + 2
    }
}
