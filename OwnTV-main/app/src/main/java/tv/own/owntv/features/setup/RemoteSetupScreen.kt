package tv.own.owntv.features.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.companion.CompanionPayload
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.ui.components.companionLockedText
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The Remote add-source screen: opens a small LAN web server and shows the PIN, a QR of the URL, and
 * the URL text so another device on the same Wi-Fi can fill the Add Source form. It only fills it —
 * when a submission arrives, [onPayloadReceived] hands off to the Manual form (pre-filled) where the
 * user presses Start Import. The listener stops automatically when this screen leaves composition.
 */
@Composable
fun RemoteSetupScreen(
    state: CompanionServerState,
    payloads: Flow<CompanionPayload>,
    onStartListener: (port: Int) -> Unit,
    onStopListener: () -> Unit,
    onPayloadReceived: (CompanionPayload) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }

    // A remote submission hands off to the Manual form; the host navigates away (which stops the server).
    LaunchedEffect(payloads) { payloads.collect(onPayloadReceived) }
    DisposableEffect(Unit) { onDispose { onStopListener() } }

    Box(modifier.fillMaxSize().roundedPanel()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.setup_add_from_phone), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.setup_phone_add_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                when (state) {
                    CompanionServerState.Idle, CompanionServerState.Starting -> {
                        val starting = state == CompanionServerState.Starting
                        OwnTVButton(
                            label = if (starting) stringResource(R.string.setup_opening) else stringResource(R.string.setup_open_server),
                            onClick = { onStartListener(CompanionLink.DEFAULT_PORT) },
                            enabled = !starting,
                            modifier = Modifier.focusRequester(actionFocus),
                        )
                    }
                    is CompanionServerState.Listening -> {
                        Text(stringResource(R.string.setup_enter_pin_browser), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.pin,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            letterSpacing = 8.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        state.qr?.let { qr ->
                            Image(
                                bitmap = qr.asImageBitmap(),
                                contentDescription = stringResource(R.string.common_qr_code_companion_url),
                                modifier = Modifier.size(188.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(9.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(stringResource(R.string.setup_open_url), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        state.urls.forEach { url ->
                            Text(url, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(18.dp))
                        OwnTVButton(stringResource(R.string.setup_stop_server), onClick = onStopListener, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(actionFocus))
                    }
                    is CompanionServerState.Failed -> {
                        Text(state.failure.displayText(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        OwnTVButton(stringResource(R.string.setup_try_again), onClick = { onStartListener(CompanionLink.DEFAULT_PORT) }, modifier = Modifier.focusRequester(actionFocus))
                    }
                    CompanionServerState.Locked -> {
                        Text(companionLockedText(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        // Restarting mints a fresh PIN, so this is the recovery path — not a retry of a failure.
                        OwnTVButton(stringResource(R.string.setup_start_again_new_pin), onClick = { onStartListener(CompanionLink.DEFAULT_PORT) }, modifier = Modifier.focusRequester(actionFocus))
                    }
                }
                Spacer(Modifier.height(16.dp))
                OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.height(24.dp)) // breathing room so Back never sits flush to the screen edge
            }
        }
    }
}
