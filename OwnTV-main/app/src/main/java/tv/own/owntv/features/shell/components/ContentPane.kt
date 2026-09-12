package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.EmptyState
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Layer 3 — content list/grid area. Phase 1/2 render the header plus a reusable [EmptyState]; the
 * real Paging list/grid arrives in the media-section phases (7–9).
 *
 * The count sits on the subtitle line beneath the title — e.g. `50 channels` — rather than a
 * separate top-right number.
 */
@Composable
fun ContentPane(
    sectionTitle: String,
    categoryName: String,
    countLabel: String,
    emptyIcon: OwnTVIcon,
    emptyMessage: String,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
    ) {
        Text(
            text = stringResource(R.string.content_section_category, sectionTitle, categoryName),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(Dimens.GapTiny))
        Text(
            text = countLabel,
            style = MaterialTheme.typography.titleMedium,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = emptyIcon,
                title = stringResource(R.string.content_nothing_here),
                message = emptyMessage,
                actionLabel = stringResource(R.string.content_add_source),
                onAction = onAddSource,
            )
        }
    }
}
