package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Width of the channel-number strip. Fits four digits at [MaterialTheme.typography.bodyMedium]. */
private val NumberColumnWidth = 44.dp

/**
 * The provider channel number, drawn as a fixed-width right-aligned strip that sits ahead of the
 * channel name in a list row.
 *
 * Fixed width and right alignment are the whole point: a list mixing "7" with "101" would otherwise
 * start every name at a different x, and the column of names reads as ragged while scrolling. For the
 * same reason a channel with **no** number still reserves the strip rather than collapsing it —
 * partially-numbered playlists keep their names aligned.
 *
 * Callers gate visibility themselves (Settings → "Channel numbers"); this draws whenever composed.
 */
@Composable
fun ChannelNumberColumn(number: Int?, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(NumberColumnWidth), contentAlignment = Alignment.CenterEnd) {
        if (number != null) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
