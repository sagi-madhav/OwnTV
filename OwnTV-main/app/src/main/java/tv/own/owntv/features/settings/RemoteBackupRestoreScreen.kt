package tv.own.owntv.features.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.ui.components.companionLockedText
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.File

/**
 * Remote restore: opens the LAN companion server in backup-upload mode and shows the PIN, a QR of the
 * URL, and the URL text so another device on the same Wi-Fi can send an OwnTV backup file to the TV. When an
 * upload arrives, [onBackupReceived] hands the saved file off to the normal restore flow (section
 * picker, password prompt). The listener stops automatically when this screen leaves composition.
 *
 * Shared by Settings → Backup & Restore and the first-run/add-profile setup wizard.
 */
@Composable
fun RemoteBackupRestoreScreen(
    state: CompanionServerState,
    backups: Flow<File>,
    onStart: (port: Int) -> Unit,
    onStop: () -> Unit,
    onBackupReceived: (File) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }

    // Open a fresh backup-upload session on entry; stop it when the screen leaves.
    LaunchedEffect(Unit) { onStart(CompanionLink.DEFAULT_PORT) }
    LaunchedEffect(backups) { backups.collect(onBackupReceived) }
    DisposableEffect(Unit) { onDispose { onStop() } }

    Box(modifier.fillMaxSize().roundedPanel()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.settings_restore_remote_title), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_restore_remote_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                when (state) {
                    CompanionServerState.Idle, CompanionServerState.Starting -> {
                        Text(stringResource(R.string.settings_opening_server), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    is CompanionServerState.Listening -> {
                        Text(stringResource(R.string.settings_enter_pin_browser), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
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
                                contentDescription = stringResource(R.string.settings_companion_qr),
                                modifier = Modifier.size(188.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(9.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(stringResource(R.string.settings_open_url), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        state.urls.forEach { url ->
                            Text(url, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    is CompanionServerState.Failed -> {
                        Text(state.failure.displayText(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        OwnTVButton(stringResource(R.string.settings_try_again), onClick = { onStart(CompanionLink.DEFAULT_PORT) }, icon = OwnTVIcon.REFRESH)
                    }
                    CompanionServerState.Locked -> {
                        Text(companionLockedText(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        // Restarting mints a fresh PIN, so this is the recovery path — not a retry of a failure.
                        OwnTVButton(stringResource(R.string.settings_new_pin), onClick = { onStart(CompanionLink.DEFAULT_PORT) }, icon = OwnTVIcon.REFRESH)
                    }
                }
                Spacer(Modifier.height(16.dp))
                OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(actionFocus))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Remote export: the TV has written the backup to a cache file and is serving it over the companion
 * server. Shows the PIN, a QR of the URL, and the URL text so another device on the same Wi-Fi can
 * open it, enter the PIN and download the file. The server is started by the ViewModel (after the
 * export finishes) and stopped when this screen leaves composition.
 */
@Composable
fun RemoteBackupExportScreen(
    state: CompanionServerState,
    preparing: Boolean,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { onStop() } }

    Box(modifier.fillMaxSize().roundedPanel()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.settings_download_remote_title), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_download_remote_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                val listening = state as? CompanionServerState.Listening
                when {
                    preparing || (listening == null && state !is CompanionServerState.Failed) -> {
                        Text(stringResource(R.string.settings_preparing_backup), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    listening != null -> {
                        Text(stringResource(R.string.settings_enter_pin_browser), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listening.pin,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            letterSpacing = 8.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        listening.qr?.let { qr ->
                            Image(
                                bitmap = qr.asImageBitmap(),
                                contentDescription = stringResource(R.string.settings_companion_qr),
                                modifier = Modifier.size(188.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(9.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(stringResource(R.string.settings_open_url), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        listening.urls.forEach { url ->
                            Text(url, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    state is CompanionServerState.Failed -> {
                        Text(state.failure.displayText(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                    }
                }
                OwnTVButton(stringResource(R.string.common_done), onClick = onBack, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(actionFocus))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
