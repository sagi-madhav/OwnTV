package tv.own.owntv.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.features.profiles.ProfileEditorDialog
import tv.own.owntv.features.settings.FirstRunLanguageSelector
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.features.settings.EpgSyncDialog
import tv.own.owntv.features.settings.RemoteBackupRestoreScreen
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.detailText
import tv.own.owntv.ui.components.primaryText
import tv.own.owntv.ui.components.remainderText
import tv.own.owntv.ui.components.summaryText
import tv.own.owntv.ui.components.warningText
import tv.own.owntv.ui.theme.OwnTVTheme

private enum class Step { WELCOME, DISCLAIMER, SETUP_CHOICE, CREATE_PROFILE, ADD_CONTENT, ADD_SOURCE_CHOOSER, ADD_SOURCE_REMOTE, ADD_SOURCE, IMPORTING, EXISTING, IMPORT_BACKUP_CHOOSER, IMPORT_BACKUP_REMOTE, IMPORT_BACKUP }

/**
 * Onboarding for one profile. [firstRun] shows language/welcome/disclaimer; otherwise it starts at profile
 * creation (used by "Add profile"). [onDone] receives the newly active profile id and enters the
 * app; [onCancel] backs out (to the gate).
 */
@Composable
fun Onboarding(firstRun: Boolean, onDone: (Long?) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SetupViewModel = koinViewModel()
    val defaultProfileName = stringResource(R.string.setup_default_profile)
    val defaultIptvName = stringResource(R.string.setup_default_iptv)
    val defaultPlaylistName = stringResource(R.string.setup_name_default_playlist)
    val defaultPortalName = stringResource(R.string.setup_default_portal)
    var step by rememberSaveable(firstRun) { mutableStateOf(if (firstRun) Step.WELCOME else Step.CREATE_PROFILE) }
    val importState by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val epgSync by vm.epgSync.collectAsStateWithLifecycle()
    var existing by remember { mutableStateOf<List<SourceEntity>>(emptyList()) }
    // Where "Try Again" returns to when an import fails (new source vs. linking existing).
    var importOrigin by remember { mutableStateOf(Step.ADD_SOURCE) }
    // Where Back from the backup-restore picker returns to (first-run choice vs. add-content step).
    var backupOrigin by remember { mutableStateOf(Step.ADD_CONTENT) }

    // Refresh the "existing playlists" availability whenever we land on the add-content step.
    LaunchedEffect(step) { if (step == Step.ADD_CONTENT) existing = runCatching { vm.availableExistingSources() }.getOrDefault(emptyList()) }

    Box(modifier = modifier.fillMaxSize().background(OwnTVTheme.colors.background)) {
        when (step) {
            Step.WELCOME -> WelcomeScreen(onNext = { step = Step.DISCLAIMER })
            Step.DISCLAIMER -> DisclaimerScreen(onAgree = { step = Step.SETUP_CHOICE }, onBack = { step = Step.WELCOME })
            // First decision: start fresh or bring everything back from a backup (profiles included —
            // no point creating a profile first that the restore would replace).
            Step.SETUP_CHOICE -> SetupChoiceScreen(
                onCreate = { step = Step.CREATE_PROFILE },
                onRestore = { backupOrigin = Step.SETUP_CHOICE; step = Step.IMPORT_BACKUP_CHOOSER },
                onBack = { step = Step.DISCLAIMER },
            )
            Step.CREATE_PROFILE -> ProfileEditorDialog(
                initial = null,
                onConfirm = { name, avatar, kids, pin -> vm.createProfile(name.ifBlank { defaultProfileName }, avatar, kids, pin) { step = Step.ADD_CONTENT } },
                onDismiss = { if (firstRun) step = Step.SETUP_CHOICE else onCancel() },
            )
            Step.ADD_CONTENT -> AddContentScreen(
                hasExisting = existing.isNotEmpty(),
                onNew = { step = Step.ADD_SOURCE_CHOOSER },
                onExisting = { step = Step.EXISTING },
                onImport = { backupOrigin = Step.ADD_CONTENT; step = Step.IMPORT_BACKUP_CHOOSER },
                onSkip = { vm.finish(onDone) },
            )
            Step.ADD_SOURCE_CHOOSER -> AddSourceChooserScreen(
                onRemote = { step = Step.ADD_SOURCE_REMOTE },
                onManual = { step = Step.ADD_SOURCE },
                onBack = { step = Step.ADD_CONTENT },
            )
            Step.ADD_SOURCE_REMOTE -> RemoteSetupScreen(
                state = vm.remoteState.collectAsStateWithLifecycle().value,
                payloads = vm.remotePayloads,
                onStartListener = { port -> vm.startRemoteListener(port) },
                onStopListener = { vm.stopRemoteListener() },
                // A remote submission hands off to the pre-filled Manual form (the user presses Start Import).
                onPayloadReceived = { step = Step.ADD_SOURCE },
                onBack = { vm.stopRemoteListener(); step = Step.ADD_SOURCE_CHOOSER },
            )
            Step.ADD_SOURCE -> AddSourceScreen(
                onStartXtream = { name, server, user, pass, ua, epg, refresh, live, movies, series, _, preferHls ->
                    vm.startXtream(name.ifBlank { defaultIptvName }, server, user, pass, ua, epg, refresh, live, movies, series, preferHls)
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                onStartM3u = { name, url, ua, epg, refresh, _ -> vm.startM3u(name.ifBlank { defaultPlaylistName }, url, ua, epg, refresh); importOrigin = Step.ADD_SOURCE; step = Step.IMPORTING },
                onStartStalker = { name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature, ua, refresh, _, live, movies, series ->
                    vm.startStalker(
                        name.ifBlank { defaultPortalName }, portalUrl, mac, serialNumber, deviceId,
                        deviceId2, signature, ua, refresh, live, movies, series,
                    )
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                // Submissions from the Remote screen land here pre-filled (type + fields).
                remotePayload = vm.remotePayload,
                onRemotePayloadConsumed = { vm.consumeRemotePayload() },
                onBack = { step = Step.ADD_SOURCE_CHOOSER },
                showDefaultToggle = false, // first playlist in setup: nothing to be "default" over yet
            )
            Step.IMPORTING -> ImportProgressScreen(
                state = importState,
                progress = progress,
                onContinue = { vm.finish(onDone) }, // playlist + its EPG synced (auto)
                onRetry = { vm.reset(); step = importOrigin },
                onCancel = { vm.cancelImport(); step = importOrigin },
                onBackground = { vm.continueInBackground(onDone) }, // enter the app; sync keeps running
            )
            Step.EXISTING -> ExistingSourcesScreen(
                sources = existing,
                onAdd = { ids -> vm.linkExisting(ids); importOrigin = Step.EXISTING; step = Step.IMPORTING },
                onBack = { step = Step.ADD_CONTENT },
            )
            Step.IMPORT_BACKUP_CHOOSER -> ImportBackupChooserScreen(
                onRemote = { step = Step.IMPORT_BACKUP_REMOTE },
                onLocal = { step = Step.IMPORT_BACKUP },
                onBack = { step = backupOrigin },
            )
            Step.IMPORT_BACKUP_REMOTE -> RemoteBackupRestoreScreen(
                state = vm.remoteState.collectAsStateWithLifecycle().value,
                backups = vm.remoteBackups,
                onStart = { port -> vm.startRemoteRestore(port) },
                onStop = { vm.stopRemoteRestore() },
                // An uploaded file starts the restore; the state-driven IMPORT_BACKUP screen shows
                // progress, the password prompt, or the result from here on.
                onBackupReceived = { file -> vm.importBackup(file, onDone); step = Step.IMPORT_BACKUP },
                onBack = { vm.stopRemoteRestore(); step = Step.IMPORT_BACKUP_CHOOSER },
            )
            Step.IMPORT_BACKUP -> ImportBackupScreen(
                state = importState,
                onPick = { file -> vm.importBackup(file, onDone) }, // restore activates a profile itself
                onPassword = { file, pass -> vm.restoreWithPassword(file, pass, onDone) },
                onBack = { vm.reset(); step = backupOrigin },
            )
        }
        // Semi-auto EPG: after the first playlist imports, ask → sync (live count) → done (overlays "All set!").
        EpgSyncDialog(
            state = epgSync,
            onSync = vm::syncPendingEpg,
            onDismiss = vm::dismissPendingEpg,
            onBackground = { vm.syncEpgInBackground(onDone) }, // enter the app; guide keeps downloading
        )
    }
}

@Composable
private fun WelcomeScreen(onNext: () -> Unit) {
    MainSetupPage {
        Text(
            stringResource(R.string.setup_welcome_to),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            ),
            color = OwnTVTheme.colors.primary.copy(alpha = 0.82f),
        )
        Spacer(Modifier.height(19.dp))
        BrandLockup(markSize = 82, textSize = 62)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_welcome_tagline), style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
        SetupAccentRule()
        Spacer(Modifier.height(26.dp))
        FirstRunLanguageSelector()
        Spacer(Modifier.height(14.dp))
        OwnTVButton(
            stringResource(R.string.setup_get_started),
            onClick = onNext,
            modifier = Modifier.width(192.dp).height(50.dp),
            icon = OwnTVIcon.PLAY,
        )
    }
}

@Composable
private fun DisclaimerScreen(onAgree: () -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    MainSetupPage {
        BrandLockup(markSize = 36, textSize = 26)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_before_you_start), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.setup_disclaimer),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(20.dp))
        SetupAccentRule()
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton(
                stringResource(R.string.common_back),
                onClick = onBack,
                modifier = Modifier.width(140.dp),
                style = OwnTVButtonStyle.SECONDARY,
            )
            OwnTVButton(
                stringResource(R.string.setup_i_understand),
                onClick = onAgree,
                modifier = Modifier.width(220.dp).focusRequester(fr),
            )
        }
    }
}

@Composable
private fun SetupChoiceScreen(onCreate: () -> Unit, onRestore: () -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onBack() }
    MainSetupPage {
        BrandLockup(markSize = 36, textSize = 26)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_set_up_owntv), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.setup_setup_choice_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(20.dp))
        SetupAccentRule()
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(icon = OwnTVIcon.PERSON, title = stringResource(R.string.setup_new_profile), desc = stringResource(R.string.setup_create_profile_add_sources), modifier = Modifier.focusRequester(fr), onClick = onCreate)
            ChoiceCard(icon = OwnTVIcon.DOWNLOADS, title = stringResource(R.string.setup_restore_backup), desc = stringResource(R.string.setup_import_profiles_playlists), onClick = onRestore)
        }
    }
}

@Composable
private fun AddContentScreen(hasExisting: Boolean, onNew: () -> Unit, onExisting: () -> Unit, onImport: () -> Unit, onSkip: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    MainSetupPage {
        BrandLockup(markSize = 36, textSize = 26)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_add_playlist), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.setup_add_playlist_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(20.dp))
        SetupAccentRule()
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(icon = OwnTVIcon.ADD, title = stringResource(R.string.setup_new), desc = stringResource(R.string.setup_add_m3u_xtream), modifier = Modifier.focusRequester(fr), onClick = onNew)
            if (hasExisting) {
                ChoiceCard(icon = OwnTVIcon.PLAYLIST, title = stringResource(R.string.setup_existing), desc = stringResource(R.string.setup_use_other_profile_playlists), onClick = onExisting)
            }
            ChoiceCard(icon = OwnTVIcon.DOWNLOADS, title = stringResource(R.string.setup_import), desc = stringResource(R.string.setup_restore_backup_file), onClick = onImport)
        }
        Spacer(Modifier.height(24.dp))
        OwnTVButton(
            stringResource(R.string.setup_skip_for_now),
            onClick = onSkip,
            modifier = Modifier.width(190.dp),
            style = OwnTVButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun SetupAccentRule() {
    Box(
        Modifier
            .width(38.dp)
            .height(3.dp)
            .background(OwnTVTheme.colors.primary, RoundedCornerShape(50)),
    )
}

private const val MAIN_SETUP_CONTENT_SCALE = 0.62f

@Composable
private fun MainSetupPage(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        SetupAmbientBackdrop()
        Box(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = MAIN_SETUP_CONTENT_SCALE
                        scaleY = MAIN_SETUP_CONTENT_SCALE
                    }
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun SetupAmbientBackdrop() {
    val primary = OwnTVTheme.colors.primary
    val transition = rememberInfiniteTransition()
    val ringScale by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.5f, size.height * 0.48f)
        val glowRadius = size.minDimension * 0.46f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.12f),
                    primary.copy(alpha = 0.045f),
                    Color.Transparent,
                ),
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.075f),
            radius = size.minDimension * 0.34f * ringScale,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun ExistingSourcesScreen(sources: List<SourceEntity>, onAdd: (Set<Long>) -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.setup_use_existing_playlists), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.setup_pick_playlists), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            // Cap to the screen (minus header/footer) so Back/Add stay reachable on small screens.
            val listMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 260.dp).coerceIn(140.dp, 320.dp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMax), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources, key = { it.id }) { src ->
                    val checked = src.id in selected
                    FocusableSurface(
                        onClick = { selected = if (checked) selected - src.id else selected + src.id },
                        modifier = if (src.id == sources.firstOrNull()?.id) Modifier.fillMaxWidth().focusRequester(fr) else Modifier.fillMaxWidth(),
                        selected = checked,
                        shape = RoundedCornerShape(12.dp),
                        selectedContainerColor = colors.primaryContainer,
                        contentAlignment = Alignment.CenterStart,
                    ) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(src.name, style = MaterialTheme.typography.titleMedium, color = if (checked) colors.onPrimaryContainer else colors.onSurface)
                                Text(src.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (checked) OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton(pluralStringResource(R.plurals.setup_add_selected_playlists, selected.size, selected.size), onClick = { onAdd(selected) }, enabled = selected.isNotEmpty())
            }
        }
    }
}

@Composable
private fun ImportBackupScreen(
    state: SourceImporter.ImportState,
    onPick: (java.io.File) -> Unit,
    onPassword: (java.io.File, String?) -> Unit,
    onBack: () -> Unit,
) {
    when (state) {
        SourceImporter.ImportState.Running -> Centered {
            OwnTVSpinner(sizeDp = 56); Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.setup_restoring), style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurface)
        }
        is SourceImporter.ImportState.NeedPassword -> Centered {
            var password by remember { mutableStateOf("") }
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
            Text(
                if (state.retry) stringResource(R.string.setup_wrong_backup_password) else stringResource(R.string.setup_enter_backup_password),
                style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    state.retry && state.sealed -> stringResource(R.string.setup_password_mismatch_sealed)
                    state.retry -> stringResource(R.string.setup_password_mismatch)
                    state.sealed -> stringResource(R.string.setup_backup_encrypted_prompt)
                    else -> stringResource(R.string.setup_backup_passwords_encrypted_prompt)
                },
                style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(20.dp))
            OwnTVTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.setup_backup_password),
                isPassword = true,
                focusRequester = firstFocus,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                // No "Skip" for a sealed container: without the password there is nothing to restore.
                if (!state.sealed) {
                    OwnTVButton(stringResource(R.string.setup_skip_no_passwords), onClick = { onPassword(state.file, null) }, style = OwnTVButtonStyle.SECONDARY)
                }
                OwnTVButton(stringResource(R.string.setup_restore), onClick = { onPassword(state.file, password) }, enabled = password.isNotBlank())
            }
        }
        is SourceImporter.ImportState.Failed -> Centered {
            Text(stringResource(R.string.setup_restore_failed), style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(state.failure.displayText(), style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
            Spacer(Modifier.height(20.dp))
            OwnTVButton(stringResource(R.string.common_back), onClick = onBack)
        }
        else -> StorageBrowser(
            title = stringResource(R.string.setup_pick_backup_file),
            mode = BrowseMode.FILE,
            // `.own` containers plus pre-4.2 `.json` backups.
            fileExtensions = tv.own.owntv.core.backup.BackupManager.RESTORE_EXTENSIONS,
            onPick = onPick,
            onDismiss = onBack,
        )
    }
}

/** Restore chooser: send the backup from another device (LAN companion server) or pick a local file. */
@Composable
private fun ImportBackupChooserScreen(onRemote: () -> Unit, onLocal: () -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onBack() }
    Centered {
        Text(stringResource(R.string.setup_restore_a_backup), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.setup_restore_choice_description),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(icon = OwnTVIcon.PLAYLIST, title = stringResource(R.string.setup_from_phone), desc = stringResource(R.string.setup_upload_from_wifi_device), modifier = Modifier.focusRequester(fr), onClick = onRemote)
            ChoiceCard(icon = OwnTVIcon.DOWNLOADS, title = stringResource(R.string.setup_local_file), desc = stringResource(R.string.setup_pick_backup_local), onClick = onLocal)
        }
        Spacer(Modifier.height(24.dp))
        OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
    }
}

@Composable
private fun ChoiceCard(icon: OwnTVIcon, title: String, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(width = 220.dp, height = 170.dp),
        shape = RoundedCornerShape(22.dp),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                OwnTVIcon(icon, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (focused) colors.primary else colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ImportProgressScreen(
    state: SourceImporter.ImportState,
    progress: tv.own.owntv.core.sync.ImportStage?,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    val bgFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { bgFr.requestFocus() } }
    LaunchedEffect(state) {
        if (state is SourceImporter.ImportState.Success || state is SourceImporter.ImportState.Failed) runCatching { fr.requestFocus() }
    }
    BackHandler(enabled = state is SourceImporter.ImportState.Running || state is SourceImporter.ImportState.Idle) { onCancel() }
    Centered {
        when (state) {
            SourceImporter.ImportState.Running, SourceImporter.ImportState.Idle,
            is SourceImporter.ImportState.NeedPassword -> {
                val display = progress?.importProgressDisplay()
                OwnTVSpinner(sizeDp = 56)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.setup_importing_catalog), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    display?.primaryText() ?: stringResource(R.string.setup_preparing_catalog),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    display?.detailText() ?: stringResource(R.string.setup_preparing_catalog),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Enter the app right away; the import keeps running (VM is activity-scoped) and
                    // content appears as it lands — no need to sit through a big movies/series sync.
                    OwnTVButton(stringResource(R.string.setup_run_in_background), onClick = onBackground, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(bgFr))
                    OwnTVButton(stringResource(R.string.common_cancel), onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.setup_watching_during_import),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            is SourceImporter.ImportState.Success -> {
                Text(stringResource(R.string.setup_all_set), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(10.dp))
                state.counts?.let { counts ->
                    Text(counts.summaryText(includeEpg = true), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                }
                state.restoredItems?.let { items ->
                    Text(pluralStringResource(R.plurals.setup_restored_items, items, items), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                }
                state.passwordsOmitted.takeIf { it }?.let {
                    Text(stringResource(R.string.setup_passwords_omitted), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                state.skippedSources.takeIf { it > 0 }?.let { skipped ->
                    Text(pluralStringResource(R.plurals.setup_skipped_sources, skipped, skipped), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                if (state.invalidLocale) {
                    Text(stringResource(R.string.setup_invalid_locale), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                state.warnings.warningText()?.let { warning ->
                    Text(warning, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                state.remainder.remainderText()?.let { remainder ->
                    Text(remainder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(28.dp))
                OwnTVButton(stringResource(R.string.setup_continue), onClick = onContinue, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
            }
            is SourceImporter.ImportState.Failed -> {
                Text(stringResource(R.string.setup_import_failed), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(state.failure.displayText(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(stringResource(R.string.common_back), onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
                    OwnTVButton(stringResource(R.string.setup_try_again_caps), onClick = onRetry, modifier = Modifier.focusRequester(fr))
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        // Scrollable so wizard steps taller than a small/low-res screen keep all buttons reachable.
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
