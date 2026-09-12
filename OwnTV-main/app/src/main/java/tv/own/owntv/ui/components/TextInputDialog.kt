package tv.own.owntv.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * A simple TV dialog with one text field (e.g. renaming a channel/category). [onConfirm] receives the
 * trimmed text — possibly empty, which callers treat as "reset to original". [onDelete] is optional
 * and renders a destructive button (tinted with the app's favorite/error color) at the left end of
 * the button row — used by the custom-category rename dialog (issue #87) to delete the category.
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String? = null,
    confirmLabel: String? = null,
    hint: String? = null,
    onDelete: (() -> Unit)? = null,
    allowBlank: Boolean = true,
) {
    val colors = OwnTVTheme.colors
    val resolvedLabel = label ?: stringResource(R.string.common_name)
    val resolvedConfirmLabel = confirmLabel ?: stringResource(R.string.common_save)
    var value by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    // A focusable platform popup is a hard boundary from the parent dialog/screen. This matters for
    // nested editors (for example Rule builder -> Rule value): an in-tree overlay lets the parent's
    // focus trap keep D-pad focus behind the editor, leaving its text field completely unreachable.
    OwnTVPopup(onDismissRequest = onDismiss) {
        // Wait until the popup window is attached before asking Android to focus/show the IME.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(80)
            runCatching { fieldFocus.requestFocus() }
        }
        BackHandler { onDismiss() }
        Box(
            modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                if (hint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(18.dp))
                OwnTVTextField(value = value, onValueChange = { value = it }, label = resolvedLabel, modifier = Modifier.fillMaxWidth(), focusRequester = fieldFocus, surface = GlassSurface.DIALOGS)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onDelete != null) {
                        OwnTVButton(stringResource(R.string.common_delete), onClick = onDelete, style = OwnTVButtonStyle.SECONDARY)
                        Spacer(Modifier.weight(1f))
                    }
                    OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(
                        resolvedConfirmLabel,
                        onClick = { onConfirm(value.trim()) },
                        enabled = allowBlank || value.isNotBlank(),
                    )
                }
            }
        }
    }
}
