package tv.own.owntv.features.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.live.CatchupJumps
import tv.own.owntv.R
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * "Go back to…" — pick a point in a live channel's catch-up archive and start there.
 *
 * Rows are wall-clock times rather than "3 hours ago" on purpose: the user is looking for the news
 * that aired at 19:00, so a clock spares them the arithmetic, and it is what the guide-less catch-up
 * pickers on other TV players show. A row that lands on an earlier day carries its weekday, because
 * "19:00" alone cannot distinguish today from yesterday.
 *
 * Shared by both entry points — the Live TV long-press Catch-up dialog (when the channel has no
 * guide, so there are no programmes to list) and the fullscreen player's own button.
 */
@Composable
internal fun CatchupJumpRows(
    offsetsSec: List<Int>,
    firstFocus: FocusRequester,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Last row: leave the suggestions behind and type an exact day + time. Null hides it.
    onChooseExact: (() -> Unit)? = null,
) {
    val colors = OwnTVTheme.colors
    // One "now" for the whole list: recomputing per row would let the clock tick between rows and
    // print two different times for the same offset.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { java.util.TimeZone.getDefault() }
    val timeOnly = rememberBestDateFormatter("Hm")
    val withDay = rememberBestDateFormatter("EEEHm")
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(offsetsSec, key = { it }) { offset ->
            val at = CatchupJumps.instantFor(offset, nowMs)
            val label = if (CatchupJumps.crossesDay(offset, nowMs, zone)) withDay(at) else timeOnly(at)
            FocusableSurface(
                onClick = { onPick(offset) },
                modifier = if (offset == offsetsSec.first()) {
                    Modifier.fillMaxWidth().focusRequester(firstFocus)
                } else {
                    Modifier.fillMaxWidth()
                },
                shape = RoundedCornerShape(12.dp),
                contentAlignment = Alignment.CenterStart,
                surface = GlassSurface.DIALOGS,
            ) { _ ->
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        if (onChooseExact != null) {
            item(key = "exact") {
                FocusableSurface(
                    onClick = onChooseExact,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentAlignment = Alignment.CenterStart,
                    surface = GlassSurface.DIALOGS,
                ) { _ ->
                    Text(
                        stringResource(R.string.content_catchup_jump_exact),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** The standalone popup form, used by the player's "Go back to…" button. */
@Composable
internal fun CatchupJumpDialog(
    title: String,
    offsetsSec: List<Int>,
    windowSec: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    var manual by remember { mutableStateOf(false) }
    if (manual) {
        CatchupManualTimeDialog(
            windowSec = windowSec,
            onPick = { manual = false; onPick(it) },
            onDismiss = { manual = false },
        )
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() }
    }
    androidx.activity.compose.BackHandler { onDismiss() }

    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
            Box(
                Modifier.fillMaxSize()
                    .modalScrim()
                    .trapAllFocusExit()
                    .focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                val listHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 220.dp)
                    .coerceIn(140.dp, 300.dp)
                Column(Modifier.dialogPanel(width = 460.dp, corner = 16.dp, padding = 18.dp, scroll = false)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.content_catchup_jump_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    CatchupJumpRows(
                        offsetsSec = offsetsSec,
                        firstFocus = firstFocus,
                        onPick = onPick,
                        modifier = Modifier.fillMaxWidth().height(listHeight),
                        onChooseExact = { manual = true },
                    )
                    Spacer(Modifier.height(14.dp))
                    OwnTVButton(
                        stringResource(R.string.content_close),
                        onClick = onDismiss,
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                }
            }
        }
    }
}
