package tv.own.owntv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The D-pad HSV color picker shared by every "pick a color" dialog — the accent picker in Settings
 * and the subtitle text color in Subtitle appearance (#96). Extracted verbatim from the accent
 * dialog, so its focus behaviour on a remote is the behaviour that was already tuned there.
 */

/** Convert an HSV triple (h 0..360, s/v 0..1) into an uppercase "#RRGGBB" string. */
fun hsvToHex(h: Float, s: Float, v: Float): String {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    return "#%06X".format(java.util.Locale.ROOT, argb and 0xFFFFFF)
}

/** The rainbow gradient for the hue bar (0°→360° across the full spectrum). */
private val HueSpectrum: List<Color> = (0..360 step 30).map {
    Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f)))
}

/**
 * Hue selector: one focusable rainbow strip. Press OK to enter edit mode (the strip glows amber),
 * then ◀ ▶ shift the hue; OK or Back exits. Directional keys are consumed only while editing, so a
 * stray press can't jump focus out of the strip mid-adjust. [hue] is 0..360.
 */
@Composable
fun HueBar(hue: Float, modifier: Modifier = Modifier, onHue: (Float) -> Unit) {
    val colors = OwnTVTheme.colors
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val ring = when {
        editing -> Color(0xFFFFC24A)
        focused -> colors.focusBorder
        else -> colors.outline
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.horizontalGradient(HueSpectrum))
                .border(if (editing || focused) 3.dp else 1.dp, ring, RoundedCornerShape(15.dp))
                .onFocusChanged { focused = it.isFocused; if (!it.isFocused) editing = false }
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    // Physical by design: the hue spectrum always runs left to right.
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { editing = !editing; true }
                        Key.Back -> if (editing) { editing = false; true } else false
                        Key.DirectionLeft -> if (editing) { onHue(((hue - 4f) % 360f + 360f) % 360f); true } else false
                        Key.DirectionRight -> if (editing) { onHue((hue + 4f) % 360f); true } else false
                        Key.DirectionUp, Key.DirectionDown -> editing // trap vertical only while editing
                        else -> false
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // Knob marking the current hue.
            BoxWithConstraints {
                val x = (maxWidth - 12.dp) * (hue / 360f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .offset(x = x)
                        .width(12.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(2.dp, Color(0x99000000), RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

/**
 * Saturation / Brightness square: OK to enter edit mode (the box glows amber), then D-pad moves the
 * dot — left/right = saturation, up/down = brightness — and OK or Back exits. All four directions
 * are consumed while editing so focus stays put; when not editing the box is a normal focus stop.
 */
@Composable
fun SatValSquare(
    hue: Float,
    sat: Float,
    value: Float,
    modifier: Modifier = Modifier,
    onChange: (Float, Float) -> Unit,
) {
    val colors = OwnTVTheme.colors
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val ring = when {
        editing -> Color(0xFFFFC24A)
        focused -> colors.focusBorder
        else -> colors.outline
    }
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val step = 0.04f
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(if (editing || focused) 3.dp else 1.dp, ring, RoundedCornerShape(16.dp))
                .onFocusChanged { focused = it.isFocused; if (!it.isFocused) editing = false }
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    // Physical by design: saturation/value follow the fixed 2D color field.
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { editing = !editing; true }
                        Key.Back -> if (editing) { editing = false; true } else false
                        Key.DirectionLeft -> if (editing) { onChange((sat - step).coerceIn(0f, 1f), value); true } else false
                        Key.DirectionRight -> if (editing) { onChange((sat + step).coerceIn(0f, 1f), value); true } else false
                        Key.DirectionUp -> if (editing) { onChange(sat, (value + step).coerceIn(0f, 1f)); true } else false
                        Key.DirectionDown -> if (editing) { onChange(sat, (value - step).coerceIn(0f, 1f)); true } else false
                        else -> false
                    }
                },
        ) {
            // Base hue, then white (left→right) and black (bottom→top) gradients = an HSV square.
            Box(Modifier.matchParentSize().background(hueColor))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
            // Cursor: x = saturation, y = 1 - brightness.
            BoxWithConstraints(Modifier.matchParentSize()) {
                val cx = (maxWidth - 22.dp) * sat.coerceIn(0f, 1f)
                val cy = (maxHeight - 22.dp) * (1f - value).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .offset(x = cx, y = cy)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(3.dp, Color.White, CircleShape),
                )
            }
        }
    }
}

/** A focusable color swatch circle; the selected one is ringed. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size((sizeDp + 14).dp),
        shape = CircleShape,
        selected = selected,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(3.dp, colors.onSurface, CircleShape)
                    else Modifier,
                ),
        )
    }
}
