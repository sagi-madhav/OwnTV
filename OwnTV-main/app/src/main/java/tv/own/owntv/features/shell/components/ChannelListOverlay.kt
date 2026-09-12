package tv.own.owntv.features.shell.components

import tv.own.owntv.core.epg.displayLogoUrl
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ProviderChip
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * A channel list that slides in over the playing video. Two instances exist in the player: Left opens
 * the playing channel's own provider category (anchored left), Right opens the profile's watch history
 * (anchored right). Browse with the D-pad, OK switches channel, Back — or pushing outwards past the
 * list edge — closes it, all without leaving full-screen. The current channel is highlighted and
 * focused first; for the history list nothing may be current, so the newest row takes focus.
 */
@Composable
fun ChannelListOverlay(
    channels: List<ChannelEntity>,
    currentId: Long?,
    onSelect: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    nowPlaying: Map<Long, String> = emptyMap(),
    title: String? = null,
    alignEnd: Boolean = false,
    showNumbers: Boolean = true,
    providerNames: Map<Long, String> = emptyMap(),
    onOpenCategories: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val layoutDirection = LocalLayoutDirection.current
    val dismissDirection = if (alignEnd) HorizontalDirection.END else HorizontalDirection.START
    val currentIndex = remember(channels, currentId) {
        channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    val focusCurrent = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { listState.scrollToItem(currentIndex) }
        runCatching { focusCurrent.requestFocus() }
    }
    BackHandler { onDismiss() }

    Box(modifier = modifier.fillMaxSize().modalScrim(strength = 0.58f)) {
        Column(
            modifier = Modifier
                .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .width(380.dp)
                .roundedPanel(radius = 22.dp, fillColor = ContentPanelFill, surface = GlassSurface.DIALOGS)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == dismissDirection) {
                        // Pushing outward from the Start panel opens categories when they are wired;
                        // pushing outward from the End panel closes it. Back always closes.
                        if (!alignEnd && onOpenCategories != null) onOpenCategories() else onDismiss()
                        true
                    } else false
                }
                .padding(vertical = 18.dp),
        ) {
            Text(
                title ?: stringResource(R.string.content_channel_overlay_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(channels, key = { it.id }) { ch ->
                    val isCurrent = ch.id == currentId
                    ChannelRow(
                        channel = ch,
                        isCurrent = isCurrent,
                        nowTitle = nowPlaying[ch.id],
                        showNumber = showNumbers,
                        providerName = providerNames[ch.sourceId],
                        onClick = { onSelect(ch) },
                        modifier = if (ch.id == channels.getOrNull(currentIndex)?.id) Modifier.focusRequester(focusCurrent) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    nowTitle: String? = null,
    showNumber: Boolean = true,
    providerName: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        selected = isCurrent,
        modifier = modifier.fillMaxWidth(),
        surface = GlassSurface.DIALOGS,
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.displayLogoUrl.isNullOrBlank()) {
                    AsyncImage(model = channel.displayLogoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            // Fixed-width number strip, so names stay aligned whatever the digit count (see LiveScreen).
            if (showNumber) {
                tv.own.owntv.ui.components.ChannelNumberColumn(
                    number = channel.number,
                    color = colors.onSurfaceVariant,
                )
            }
            // Name + (optional) current programme subtitle, shown only when guide data exists.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isCurrent -> colors.primary
                        focused -> colors.onSurface
                        else -> colors.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().then(
                        if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    ),
                )
                if (nowTitle != null) {
                    Text(
                        nowTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            providerName?.let { ProviderChip(name = it, maxWidth = 104.dp, compact = true) }
        }
    }
}
