package tv.own.owntv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

@Composable
internal fun ServiceSummaryCard(
    eyebrow: String,
    title: String,
    description: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    icon: OwnTVIcon = OwnTVIcon.SPARKLE,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = modifier.background(colors.card.copy(alpha = .82f), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ServiceIconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        trailing?.let { ServiceChip(it, true) }
    }
}

@Composable
internal fun MetadataOverview(
    eyebrow: String,
    title: String,
    description: String,
    minuteRemaining: Int?,
    minuteLimit: Int?,
    hourRemaining: Int?,
    hourLimit: Int?,
    dayRemaining: Int?,
    dayLimit: Int?,
    refillTime: String?,
) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServiceSummaryCard(eyebrow, title, description, modifier = Modifier.weight(.34f).fillMaxHeight())
        if (minuteRemaining != null && minuteLimit != null && hourRemaining != null && hourLimit != null &&
            dayRemaining != null && dayLimit != null && refillTime != null
        ) {
            AllowanceCard(
                minuteRemaining, minuteLimit, hourRemaining, hourLimit, dayRemaining, dayLimit, refillTime,
                Modifier.weight(.66f),
            )
        }
    }
}

@Composable
internal fun AllowanceCard(
    minuteRemaining: Int,
    minuteLimit: Int,
    hourRemaining: Int,
    hourLimit: Int,
    dayRemaining: Int,
    dayLimit: Int,
    refillTime: String,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Column(modifier.background(colors.card.copy(alpha = .82f), RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.settings_metadata_allowance).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.settings_allowance_available),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
            }
            ServiceChip(androidx.compose.ui.res.stringResource(R.string.settings_allowance_refills, refillTime), true)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AllowanceBar(
                androidx.compose.ui.res.stringResource(R.string.settings_allowance_minute), minuteRemaining, minuteLimit,
                androidx.compose.ui.res.stringResource(R.string.settings_metadata_reset_automatic), Modifier.weight(1f),
            )
            AllowanceBar(
                androidx.compose.ui.res.stringResource(R.string.settings_allowance_hour), hourRemaining, hourLimit,
                androidx.compose.ui.res.stringResource(R.string.settings_metadata_reset_automatic), Modifier.weight(1f),
            )
            AllowanceBar(
                androidx.compose.ui.res.stringResource(R.string.settings_allowance_day), dayRemaining, dayLimit,
                androidx.compose.ui.res.stringResource(R.string.settings_allowance_refills, refillTime), Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun OpenSubtitlesOverview(
    eyebrow: String,
    title: String,
    profile: String,
    connectedLabel: String,
    accountLabel: String,
    accountValue: String,
    downloadsLabel: String,
    downloadsValue: String,
    resetsLabel: String,
    resetsValue: String,
    connectionLabel: String,
    connectionValue: String,
) {
    val colors = OwnTVTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.card.copy(alpha = .82f), RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(profile, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            ServiceChip(connectedLabel, true)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell(accountLabel, accountValue, Modifier.weight(1f))
            SummaryCell(downloadsLabel, downloadsValue, Modifier.weight(1f))
            SummaryCell(resetsLabel, resetsValue, Modifier.weight(1f))
            SummaryCell(connectionLabel, connectionValue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    Column(modifier.background(colors.surface.copy(alpha = .46f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ServiceSettingsRow(
    icon: OwnTVIcon,
    title: String,
    desc: String? = null,
    chip: String? = null,
    primaryChip: Boolean = true,
    chevron: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        surface = GlassSurface.CARDS,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(colors.outlineVariant, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
            }.padding(horizontal = 8.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ServiceIconTile(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                desc?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
            chip?.let { ServiceChip(it, primaryChip) }
            if (chevron) tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.CHEVRON, colors.onSurfaceVariant, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ServiceIconTile(icon: OwnTVIcon) {
    val colors = OwnTVTheme.colors
    Box(
        Modifier.size(Dimens.IconTileSize).clip(RoundedCornerShape(Dimens.IconTileCorner)).background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.components.OwnTVIcon(icon, colors.onPrimaryContainer, Modifier.size(22.dp))
    }
}

@Composable
private fun ServiceChip(text: String, primary: Boolean) {
    val colors = OwnTVTheme.colors
    val bg = if (primary) colors.primaryContainer else colors.secondaryContainer
    val fg = if (primary) colors.onPrimaryContainer else colors.onSecondaryContainer
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun AllowanceBar(label: String, remaining: Int, limit: Int, footer: String, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val ratio = if (limit <= 0) 0f else (remaining.toFloat() / limit).coerceIn(0f, 1f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.settings_allowance_value, remaining, remaining, limit),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurface,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(colors.surface, RoundedCornerShape(50))) {
            if (ratio > 0f) Box(Modifier.fillMaxWidth(ratio).height(6.dp).background(colors.primary, RoundedCornerShape(50)))
        }
        Spacer(Modifier.height(5.dp))
        Text(footer, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
