package tv.own.owntv.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * In-app YouTube trailer player (plan §7.3 / U4): a fullscreen WebView-backed IFrame player
 * (`android-youtube-player`) under a minimal Compose overlay — just an **Exit** button and a
 * **progress seekbar** (non-navigable for now; display-only, driven by the player's
 * currentSecond/duration callbacks). Focus stays entirely in the Compose overlay; the WebView is
 * treated as a pure video surface, which sidesteps the classic "iframe steals D-pad focus" problem.
 *
 * Graceful fallback (required by the plan): if the WebView is missing/ancient or the video errors,
 * we hand off to an external "Open in YouTube" intent and exit — the button always does *something*.
 */
@Composable
fun TrailerPlayerScreen(videoKey: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val colors = OwnTVTheme.colors

    var currentSec by remember { mutableFloatStateOf(0f) }
    var durationSec by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var reportedPlaybackQuality by remember { mutableStateOf<PlayerConstants.PlaybackQuality?>(null) }

    val exitFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { exitFocus.requestFocus() } }
    BackHandler { onExit() }

    // Some no-name boxes ship without a usable System WebView — constructing the player view itself
    // can throw there, so treat construction failure like a playback error (external fallback).
    val playerView = remember {
        runCatching {
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Pure video surface: the Compose overlay owns all D-pad focus.
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }.getOrNull()
    }

    if (playerView == null) {
        LaunchedEffect(Unit) { openInYouTube(context, videoKey); onExit() }
        return
    }

    LaunchedEffect(failed) {
        if (failed) { openInYouTube(context, videoKey); onExit() }
    }

    val playerOptions = remember(context) {
        IFramePlayerOptions.Builder(context)
            .controls(0)
            .fullscreen(0)
            .rel(0)
            .ivLoadPolicy(3)
            .ccLoadPolicy(0)
            .build()
    }

    DisposableEffect(Unit) {
        val listener = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                player = youTubePlayer
                youTubePlayer.loadVideo(videoKey, 0f)
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                // The IFrame bridge reports every 100 ms. A TV progress label only needs whole seconds;
                // avoiding ten Compose state writes/layouts per second leaves more UI budget for WebView video.
                val wholeSecond = second.toInt().coerceAtLeast(0)
                if (currentSec.toInt() != wholeSecond) currentSec = wholeSecond.toFloat()
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                durationSec = duration
            }

            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                failed = true
            }

            override fun onPlaybackQualityChange(
                youTubePlayer: YouTubePlayer,
                playbackQuality: PlayerConstants.PlaybackQuality,
            ) {
                reportedPlaybackQuality = playbackQuality
            }

            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                if (state == PlayerConstants.PlayerState.ENDED) onExit()
            }
        }
        // OwnTV supplies the TV-safe D-pad controls; do not make WebView render a second control layer.
        runCatching { playerView.initialize(listener, playerOptions) }.onFailure { failed = true }
        onDispose { player = null; runCatching { playerView.release() } }
    }

    // Navigable seek (§7.3, additive step): D-pad ◀/▶ on the overlay = seek ±10s. The Exit button is the
    // only focus target, so its key events bubble up here; consuming Left/Right also stops focus search
    // from wandering (there is nothing to the button's sides anyway).
    val onSeekKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = onKey@{ e ->
        if (e.type != KeyEventType.KeyDown) return@onKey false
        val p = player ?: return@onKey false
        // Physical by design: left rewinds and right advances trailer time in every locale.
        when (e.key) {
            Key.DirectionLeft -> { p.seekTo((currentSec - 10f).coerceAtLeast(0f)); true }
            Key.DirectionRight -> {
                val target = currentSec + 10f
                p.seekTo(if (durationSec > 0f) target.coerceAtMost(durationSec - 1f) else target)
                true
            }
            else -> false
        }
    }

    // Fullscreen and unclipped, unlike the windowed TMDB details popup this used to copy.
    //
    // The WebView only renders video on a hardware overlay when nothing forces it to be composited
    // into the app's own layer. A rounded clip, a fractional size and a scrim behind it all did, and
    // the result was that every decoded frame was copied through the GPU instead: playback filled the
    // log with "no buffers currently available in the reader queue" / "CopySharedImage: Source shared
    // image is not accessable" and dropped frames continuously. Decoding was never the problem — the
    // Realtek AV1 hardware decoder kept up throughout.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent(onSeekKey)
            .trapAllFocusExit()
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

            // Minimal chrome: Exit + progress bar + time, bottom-aligned over the video.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                OwnTVButton(
                    stringResource(R.string.player_trailer_exit),
                    onClick = onExit,
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(exitFocus),
                )
                // Display-only progress bar (plan: non-navigable seek first; D-pad seek is a later additive step).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        val fraction = if (durationSec > 0f) (currentSec / durationSec).coerceIn(0f, 1f) else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(colors.primary),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.player_trailer_progress, formatSec(currentSec), formatSec(durationSec)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.width(120.dp),
                )
                if (BuildConfig.DEV_TOOLS) {
                    reportedPlaybackQuality?.let { quality ->
                        Text(
                            text = quality.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/** External fallback: YouTube app if installed, else any browser. Never throws. */
private fun openInYouTube(context: Context, videoKey: String) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoKey"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoKey"))
    runCatching { context.startActivity(app) }
        .recoverCatching { context.startActivity(web) }
}

@Composable
private fun formatSec(s: Float): String {
    val total = s.toInt().coerceAtLeast(0)
    val m = total / 60
    val sec = total % 60
    return stringResource(R.string.common_timestamp_minutes, m, sec)
}
