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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.menu.applyMenuOrder
import tv.own.owntv.core.menu.catalogue
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.PopupFontTheme

@Composable
private fun menuTitle(menu: ContentMenu) = stringResource(
    when (menu) {
        ContentMenu.LIVE -> R.string.common_nav_live_tv
        ContentMenu.MOVIE -> R.string.common_nav_movies
        ContentMenu.SERIES -> R.string.common_nav_series
        ContentMenu.EPISODE -> R.string.content_episodes
    },
)

private fun menuIcon(menu: ContentMenu) = when (menu) {
    ContentMenu.LIVE -> OwnTVIcon.LIVE_TV
    ContentMenu.MOVIE -> OwnTVIcon.MOVIES
    ContentMenu.SERIES -> OwnTVIcon.SERIES
    ContentMenu.EPISODE -> OwnTVIcon.LIST_GRID
}

/**
 * Long-press menus — the order of the actions in each of the four content menus.
 *
 * Four rows, one per menu. Opening one shows that menu's actions in the Move overlay, where holding
 * OK picks an action up and Up/Down carries it. Nothing is written until Save, so Cancel and Back
 * always leave the menu exactly as it was. "Close" is not listed because it is pinned to the bottom
 * of every menu, outside the arrangeable part.
 */
@Composable
fun ContentMenuSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val colors = OwnTVTheme.colors
    val scrollState = rememberScrollState()
    val rowFocus = remember { ContentMenu.entries.associateWith { FocusRequester() } }
    var openMenu by remember { mutableStateOf<ContentMenu?>(null) }
    LaunchedEffect(Unit) { runCatching { rowFocus.getValue(ContentMenu.LIVE).requestFocus() } }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusProperties { onEnter = { runCatching { rowFocus.getValue(ContentMenu.LIVE).requestFocus() } } }
            .focusGroup()
            .verticalScroll(scrollState)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header(title = stringResource(R.string.settings_content_menus_title), onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_content_menus_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        ContentMenu.entries.forEach { menu ->
            val saved by remember(menu) { vm.menuOrder(menu) }.collectAsStateWithLifecycle(emptyList())
            Row2(
                icon = menuIcon(menu),
                title = menuTitle(menu),
                chip = stringResource(
                    if (saved.isEmpty()) R.string.settings_subtitle_default else R.string.settings_live_latency_custom,
                ),
                primaryChip = saved.isNotEmpty(),
                chevron = true,
                onClick = { openMenu = menu },
                modifier = Modifier.focusRequester(rowFocus.getValue(menu)),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row2(
            icon = OwnTVIcon.REFRESH,
            title = stringResource(R.string.common_reset),
            onClick = { ContentMenu.entries.forEach { vm.setMenuOrder(it, emptyList()) } },
        )
    }

    openMenu?.let { menu ->
        ArrangeMenuOverlay(
            menu = menu,
            onSave = { keys -> vm.setMenuOrder(menu, keys); openMenu = null },
            onCancel = {
                openMenu = null
                runCatching { rowFocus.getValue(menu).requestFocus() }
            },
        )
    }
}

/**
 * One menu's actions in the Move overlay — the same panel the owner reorders channels with, except
 * the item being carried is chosen here rather than handed in: hold OK on an action to pick it up,
 * Up/Down to carry it, OK to put it down.
 */
@Composable
private fun ArrangeMenuOverlay(
    menu: ContentMenu,
    onSave: (List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: SettingsViewModel = koinViewModel()
    val colors = OwnTVTheme.colors
    val refs = catalogue(menu)
    // Wait for DataStore's first real value before seeding. An eager empty placeholder would make
    // every reopened editor show the shipped order even when a custom order is already saved.
    var keys by remember(menu) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(menu) {
        val saved = vm.menuOrder(menu).first()
        keys = applyMenuOrder(refs.map { it.key }, saved) { it }
    }
    // -1 = nothing picked up; otherwise the index Up/Down carries.
    var picked by remember(menu) { mutableIntStateOf(-1) }
    val pickedFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(keys.isEmpty()) { if (keys.isNotEmpty()) runCatching { firstFocus.requestFocus() } }
    // The carried action is a different composable at its new index, so focus has to follow it.
    LaunchedEffect(picked, keys) { if (picked >= 0) runCatching { pickedFocus.requestFocus() } }
    BackHandler { onCancel() }

    fun move(delta: Int) {
        val to = picked + delta
        if (picked < 0 || to !in keys.indices) return
        keys = keys.toMutableList().apply { add(to, removeAt(picked)) }
        picked = to
    }

    // The shared popup shape, not a hand-rolled panel: the Movies menu alone has thirteen actions, and
    // a fixed-height Column that tall is centred on a 540dp-high television with its title and its
    // Save/Cancel row hanging off both ends. dialogPanel() scrolls, and it honours the Glass setting.
    OwnTVPopup(onDismissRequest = onCancel) {
        PopupFontTheme(fontScale = 0.75f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .modalScrim()
                    .trapAllFocusExit()
                    .focusGroup()
                    // Only while an action is picked up: Up/Down carry it instead of moving focus.
                    .onPreviewKeyEvent { event ->
                        if (picked < 0 || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> { move(-1); true }
                            Key.DirectionDown -> { move(1); true }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.dialogPanel(width = 480.dp, padding = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(menuTitle(menu), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(
                        stringResource(R.string.settings_content_menus_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    keys.forEachIndexed { index, key ->
                        val ref = refs.first { it.key == key }
                        ArrangeMenuRow(
                            label = stringResource(ref.labelRes),
                            picked = picked == index,
                            onPick = { picked = if (picked == index) -1 else index },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    when {
                                        picked == index -> Modifier.focusRequester(pickedFocus)
                                        picked < 0 && index == 0 -> Modifier.focusRequester(firstFocus)
                                        else -> Modifier
                                    },
                                ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OwnTVButton(stringResource(R.string.common_save), onClick = { onSave(keys) }, modifier = Modifier.weight(1f))
                        OwnTVButton(stringResource(R.string.common_cancel), onClick = onCancel, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One action in the arrange overlay: accent-filled with a move sign while it is picked up. */
@Composable
private fun ArrangeMenuRow(
    label: String,
    picked: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    // A held OK raises the long press first and the plain click when the key is finally released,
    // which would pick the action up and immediately put it back down. Same guard as Row2.
    var longAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    FocusableSurface(
        onClick = { if (android.os.SystemClock.uptimeMillis() - longAt > 800) onPick() },
        onLongClick = { longAt = android.os.SystemClock.uptimeMillis(); onPick() },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1f,
        unfocusedContainerColor = if (picked) colors.primary else colors.surfaceContainerLowest,
        focusedContainerColor = if (picked) colors.primary else colors.primaryContainer,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        val foreground = when {
            picked -> colors.onPrimary
            focused -> colors.onPrimaryContainer
            else -> colors.onSurface
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (picked) {
                Text(
                    stringResource(R.string.setup_move_indicator),
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
