package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.player.MiniPlayerPosition
import tv.own.owntv.core.player.MiniPlayerSize
import tv.own.owntv.player.labelRes
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.rememberStepperFocus
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Settings → Video player → Mini player: how big the docked mini-player is, and which corner it sits
 * in. Both are also adjustable on the fly from the mini-player's own resize / move buttons.
 *
 * Two settings, so this is **one panel** rather than a screen holding two rows that each opened another
 * popup. That chain was three levels deep for a size and a corner, and every level was another Back
 * press and another focus restore to get wrong.
 *
 * The position choices are laid out as the screen itself — top row above bottom row, left/centre/right
 * across — so the grid *is* the preview: the option you focus sits where the mini-player will.
 */
@Composable
fun MiniPlayerSettingsDialog(onDismiss: () -> Unit) {
    val vm: SettingsViewModel = koinViewModel()
    val sizePct by vm.miniPlayerSizePct.collectAsStateWithLifecycle()
    val position by vm.miniPlayerPosition.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    val plusEnabled = sizePct < MiniPlayerSize.MAX
    val minusEnabled = sizePct > MiniPlayerSize.MIN
    val steppers = rememberStepperFocus(plusEnabled, minusEnabled)
    // The popup's own window owns focus a frame or two after it opens, so the request [rememberStepperFocus]
    // makes on mount can be dropped. Ask again once the window exists — same wait the pickers use.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        runCatching { (if (plusEnabled) steppers.plus else steppers.minus).requestFocus() }
    }
    BackHandler { onDismiss() }

    OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.dialogPanel(width = 440.dp, padding = 18.dp)) {
                Text(
                    stringResource(R.string.settings_mini_player),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_mini_player_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // --- Size ---
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_size),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    StepBtn(stringResource(R.string.common_stepper_minus), enabled = minusEnabled, modifier = Modifier.focusRequester(steppers.minus)) {
                        vm.setMiniPlayerSize((sizePct - MiniPlayerSize.STEP).coerceAtLeast(MiniPlayerSize.MIN))
                    }
                    Text(
                        stringResource(R.string.common_percent, sizePct),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    StepBtn(stringResource(R.string.common_stepper_plus), enabled = plusEnabled, modifier = Modifier.focusRequester(steppers.plus)) {
                        vm.setMiniPlayerSize((sizePct + MiniPlayerSize.STEP).coerceAtMost(MiniPlayerSize.MAX))
                    }
                }
                Spacer(Modifier.height(18.dp))

                // --- Position, laid out as the screen it describes ---
                Text(
                    stringResource(R.string.settings_position),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    MiniPlayerPosition.entries.take(3),   // top row
                    MiniPlayerPosition.entries.drop(3),   // bottom row
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { spot ->
                            PositionCell(
                                label = stringResource(spot.labelRes),
                                selected = spot == position,
                                modifier = Modifier.weight(1f),
                            ) { vm.setMiniPlayerPosition(spot) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton(
                        stringResource(R.string.common_reset),
                        onClick = {
                            vm.setMiniPlayerSize(MiniPlayerSize.DEFAULT)
                            vm.setMiniPlayerPosition(MiniPlayerPosition.DEFAULT)
                        },
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun PositionCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        shape = RoundedCornerShape(12.dp),
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}
