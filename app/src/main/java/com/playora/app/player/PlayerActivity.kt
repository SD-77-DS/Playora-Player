package com.playora.app.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon as MIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.playora.app.data.Db
import com.playora.app.data.Settings
import com.playora.app.playback.Playback
import com.playora.app.playback.SleepTimer
import com.playora.app.playback.rememberPlayerUi
import com.playora.app.theme.PlayoraTheme
import com.playora.app.util.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Immutable description of what to play, parsed from the launching intent. */
data class PlayRequest(val uri: Uri, val title: String, val subtitleUrl: String?)

/**
 * Full-screen video player (local + network). Uses the shared [PlaybackService] player through a
 * MediaController, which is what makes audio-only mode and background playback seamless.
 */
class PlayerActivity : ComponentActivity() {
    private var request by mutableStateOf<PlayRequest?>(null)
    private var audioOnly by mutableStateOf(false)
    private var inPip by mutableStateOf(false)

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            Playback.controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        request = parse(intent)
        if (request == null) { finish(); return }
        requestedOrientation = when (Settings.orientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { PlayoraTheme { Screen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parse(intent)?.let { request = it }
    }

    private fun parse(i: Intent): PlayRequest? {
        val uri = i.data ?: sharedStream(i) ?: return null
        val title = i.getStringExtra(EXTRA_TITLE) ?: displayName(uri) ?: uri.lastPathSegment ?: "Video"
        return PlayRequest(uri, title, i.getStringExtra(EXTRA_SUBTITLE_URL))
    }

    @Suppress("DEPRECATION")
    private fun sharedStream(i: Intent): Uri? {
        if (i.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }

    private fun displayName(uri: Uri): String? = runCatching {
        if (uri.scheme == "content")
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        else null
    }.getOrNull()

    // ---------- Picture-in-Picture ----------
    private fun pipSupported() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val vs = Playback.controller?.videoSize
        val ratio = if (vs != null && vs.width > 0 && vs.height > 0)
            (vs.width * vs.pixelWidthHeightRatio / vs.height).coerceIn(0.42f, 2.39f) else 16f / 9f
        val playing = Playback.controller?.isPlaying == true
        val icon = Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_TOGGLE).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).roundToInt(), 1000))
            .setActions(listOf(RemoteAction(icon, "Play/Pause", "Play or pause", pi)))
            .apply { if (Build.VERSION.SDK_INT >= 31) { setAutoEnterEnabled(autoEnter); setSeamlessResizeEnabled(true) } }
            .build()
    }

    private fun enterPip() {
        if (!Settings.pip || !pipSupported() || audioOnly) return
        runCatching { enterPictureInPictureMode(pipParams(false)) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // API 31+ enters automatically through setAutoEnterEnabled; older versions enter here.
        if (Build.VERSION.SDK_INT < 31 && Playback.controller?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    // ---------- Lifecycle ----------
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun saveProgress() {
        val c = Playback.controller ?: return
        val item = c.currentMediaItem ?: return
        val d = c.duration
        if (Playback.isVideo(item) && d > 0) Db.saveProgress(item.mediaId, item.mediaMetadata.title?.toString() ?: "", true, c.currentPosition, d)
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        // Leaving the screen pauses video unless the user switched to audio-only (headphone) mode.
        if (!isChangingConfigurations && !audioOnly) Playback.controller?.pause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        if (isFinishing && !audioOnly) Playback.controller?.run { stop(); clearMediaItems() }
        super.onDestroy()
    }

    // ---------- Loading ----------
    private fun subtitleMime(uri: Uri): String {
        val name = (displayName(uri) ?: uri.lastPathSegment ?: "").lowercase()
        return when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun load(c: androidx.media3.session.MediaController, req: PlayRequest, extraSub: Uri?) {
        val id = req.uri.toString()
        val sameItem = c.currentMediaItem?.mediaId == id
        val start = when {
            sameItem -> c.currentPosition
            Settings.resume -> Db.positionFor(id)
            else -> 0L
        }
        val subs = buildList {
            req.subtitleUrl?.let { add(subConfig(Uri.parse(it))) }
            extraSub?.let { add(subConfig(it)) }
        }
        val item = Playback.buildItem(req.uri, req.title, null, null, null, true)
            .buildUpon().setSubtitleConfigurations(subs).build()
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly).build()
        c.setMediaItem(item, start)
        c.setPlaybackSpeed(Settings.defaultSpeed)
        c.prepare(); c.play()
    }

    private fun subConfig(uri: Uri) = MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(subtitleMime(uri)).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()

    // ---------- UI ----------
    @Composable
    private fun Screen() {
        val ctx = LocalContext.current
        val controller = Playback.controller
        val req = request ?: return
        var error by remember { mutableStateOf<String?>(null) }
        var reloadTick by remember { mutableIntStateOf(0) }
        var extraSub by remember(req) { mutableStateOf<Uri?>(null) }
        var showControls by remember { mutableStateOf(true) }
        var locked by remember { mutableStateOf(false) }
        var zoom by remember { mutableFloatStateOf(1f) }
        var aspect by remember { mutableIntStateOf(Settings.aspect) }
        var hud by remember { mutableStateOf<String?>(null) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var attempts by remember { mutableIntStateOf(0) }

        // Load media once the service connection is ready; re-run on retry / new subtitle.
        LaunchedEffect(controller, req, reloadTick, extraSub) {
            if (controller == null) return@LaunchedEffect
            val id = req.uri.toString()
            val alreadyPlaying = controller.currentMediaItem?.mediaId == id &&
                controller.playbackState != Player.STATE_IDLE && reloadTick == 0 && extraSub == null
            error = null
            if (!alreadyPlaying) load(controller, req, extraSub)
        }

        // Translate player errors into friendly messages; auto-retry transient network failures.
        if (controller != null) {
            DisposableEffect(controller) {
                val l = object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        val network = e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        if (network && attempts < Settings.retryCount) {
                            attempts++
                            error = "Connection lost. Retrying ($attempts/${Settings.retryCount})…"
                            // Retry in place so the position is kept.
                            controller.prepare()
                        } else error = friendlyError(e)
                    }
                    override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_READY) { error = null; attempts = 0 } }
                }
                controller.addListener(l)
                onDispose { controller.removeListener(l) }
            }
        }

        if (controller == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            return
        }
        val ui = rememberPlayerUi(controller)

        // Arm automatic Picture-in-Picture (Android 12+) only while video is actually playing.
        LaunchedEffect(ui.isPlaying, audioOnly, Settings.pip, ui.videoSize) {
            if (pipSupported()) runCatching { setPictureInPictureParams(pipParams(ui.isPlaying && Settings.pip && !audioOnly)) }
        }
        // Auto-hide controls.
        LaunchedEffect(showControls, ui.isPlaying, dialog) {
            if (showControls && ui.isPlaying && dialog == null) { delay(4000); showControls = false }
        }
        LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }

        val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        var brightness by remember { mutableFloatStateOf(window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f) }
        var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()) }
        val seekMs = Settings.seekInterval * 1000L

        fun seekBy(d: Long) {
            val t = (controller.currentPosition + d).coerceIn(0, controller.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
            controller.seekTo(t)
            hud = (if (d > 0) "+" else "−") + "${abs(d) / 1000}s"
        }

        BackHandler(enabled = locked) { /* swallow back while the screen is locked */ }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Video surface
            AndroidView(
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom),
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { v ->
                    v.player = controller
                    v.resizeMode = when (aspect) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    v.subtitleView?.apply {
                        setStyle(CaptionStyleCompat(
                            Settings.subColor, if (Settings.subBg) 0xAA000000.toInt() else 0, 0,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE, 0xFF000000.toInt(), null))
                        setFractionalTextSize(Settings.subSize / 100f)
                        setBottomPaddingFraction(Settings.subBottom / 100f)
                    }
                },
                onRelease = { it.player = null },
            )

            if (audioOnly) AudioOnlyBackdrop(ui.title)

            // Gesture layer
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked, Settings.gestures, seekMs) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { o ->
                                if (!locked && Settings.gestures) seekBy(if (o.x < size.width / 2f) -seekMs else seekMs)
                            },
                        )
                    }
                    .pointerInput(locked, Settings.gestures) {
                        if (locked || !Settings.gestures) return@pointerInput
                        var left = false
                        detectVerticalDragGestures(onDragStart = { left = it.x < size.width / 2f }) { change, dy ->
                            change.consume()
                            val delta = -dy / size.height * 1.2f
                            if (left) {
                                brightness = (brightness + delta).coerceIn(0.02f, 1f)
                                window.attributes = window.attributes.apply { screenBrightness = brightness }
                                hud = "Brightness ${(brightness * 100).roundToInt()}%"
                            } else {
                                volume = (volume + delta).coerceIn(0f, 1f)
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0)
                                hud = "Volume ${(volume * 100).roundToInt()}%"
                            }
                        }
                    }
                    .pointerInput(locked, Settings.gestures) {
                        if (locked || !Settings.gestures) return@pointerInput
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = {
                                val d = (acc / size.width * 120_000).toLong()
                                if (d != 0L) seekBy(d)
                            },
                        ) { change, dx ->
                            change.consume(); acc += dx
                            val d = (acc / size.width * 120_000).toLong()
                            hud = (if (d >= 0) "+" else "−") + formatDuration(abs(d))
                        }
                    }
                    .pointerInput(locked, Settings.gestures) {
                        if (locked || !Settings.gestures) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent()
                                if (ev.changes.size >= 2) {
                                    val z = ev.calculateZoom()
                                    if (z != 1f) {
                                        zoom = (zoom * z).coerceIn(1f, 4f)
                                        hud = "Zoom ${(zoom * 100).roundToInt()}%"
                                        ev.changes.forEach { it.consume() }
                                    }
                                }
                            } while (ev.changes.any { it.pressed })
                        }
                    },
            )

            if (ui.state == Player.STATE_BUFFERING) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

            hud?.let {
                Text(it, color = Color.White, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 10.dp))
            }

            error?.let { msg ->
                Column(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(msg, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(12.dp))
                    Row {
                        Button(onClick = { attempts = 0; reloadTick++ }) { MIcon(Icons.Rounded.Replay, null); Spacer(Modifier.width(6.dp)); Text("Retry") }
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.OutlinedButton(onClick = { finish() }) { Text("Close", color = Color.White) }
                    }
                }
            }

            if (!inPip) {
                // Lock overlay: only an unlock button is shown while locked.
                AnimatedVisibility(locked && showControls, Modifier.align(Alignment.CenterStart).safeDrawingPadding(), enter = fadeIn(), exit = fadeOut()) {
                    RoundBtn(Icons.Rounded.LockOpen, "Unlock") { locked = false }
                }
                AnimatedVisibility(!locked && showControls, enter = fadeIn(), exit = fadeOut()) {
                    Controls(
                        ui = ui, title = ui.title.ifBlank { req.title }, audioOnly = audioOnly, aspect = aspect,
                        onBack = { finish() },
                        onAudioOnly = {
                            audioOnly = !audioOnly
                            controller.trackSelectionParameters = controller.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly).build()
                            hud = if (audioOnly) "Audio-only: screen can be turned off" else "Video on"
                        },
                        onPip = { enterPip() },
                        onLock = { locked = true; showControls = true },
                        onAspect = { aspect = (aspect + 1) % 3; Settings.aspect = aspect; zoom = 1f; hud = listOf("Fit", "Fill", "Zoom")[aspect] },
                        onRotate = {
                            val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            requestedOrientation = if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        onSeekBack = { seekBy(-seekMs) }, onSeekFwd = { seekBy(seekMs) },
                        onDialog = { dialog = it },
                    )
                }
            }
        }

        // Panels
        val pickSub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
            if (u != null) { extraSub = u; dialog = null }
        }
        when (dialog) {
            "speed" -> SpeedDialog(controller) { dialog = null }
            "audio" -> TrackDialog("Audio track", C.TRACK_TYPE_AUDIO, controller, allowOff = false) { dialog = null }
            "subs" -> SubtitlePanel(controller, onPick = { pickSub.launch(arrayOf("*/*")) }) { dialog = null }
            "sleep" -> SleepDialog { dialog = null }
            "info" -> VideoInfoDialog(req, controller) { dialog = null }
        }
    }

    private fun friendlyError(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
            "This device can't decode this video's format or codec. Try a different file."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "This file type isn't supported by Playora."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "This file looks damaged or incomplete."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "This file can't be found. It may have been moved or deleted."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Playora doesn't have permission to open this file."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network unavailable. Check your connection and retry."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server refused the request. The link may have expired or require sign-in."
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "The live stream moved ahead. Retry to rejoin."
        else -> "Playback failed. Please try again."
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE_URL = "subtitle_url"
        private const val ACTION_TOGGLE = "com.playora.app.PIP_TOGGLE"
    }
}

@Composable
private fun AudioOnlyBackdrop(title: String) {
    Box(Modifier.fillMaxSize().background(Color(0xFF101510)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MIcon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
            Spacer(Modifier.size(8.dp))
            Text("Audio-only mode", color = Color.White, fontSize = 18.sp)
            Text(title, color = Color(0xFFB6C2BA), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
    IconButton(onClick, Modifier.padding(4.dp).clip(CircleShape).background(Color(0x66000000))) { MIcon(icon, desc, tint = tint) }
}

@Composable
private fun Controls(
    ui: com.playora.app.playback.PlayerUi, title: String, audioOnly: Boolean, aspect: Int,
    onBack: () -> Unit, onAudioOnly: () -> Unit, onPip: () -> Unit, onLock: () -> Unit, onAspect: () -> Unit,
    onRotate: () -> Unit, onSeekBack: () -> Unit, onSeekFwd: () -> Unit, onDialog: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    var drag by remember { mutableStateOf<Float?>(null) }
    Box(Modifier.fillMaxSize().background(Color(0x55000000)).safeDrawingPadding()) {
        // Top bar
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { MIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onAudioOnly) { MIcon(Icons.Rounded.Headphones, "Audio-only mode", tint = if (audioOnly) accent else Color.White) }
            IconButton(onPip) { MIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
            IconButton(onLock) { MIcon(Icons.Rounded.Lock, "Lock controls", tint = Color.White) }
        }
        // Centre transport
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ ui.player.seekToPreviousMediaItem() }, enabled = ui.hasPrev) { MIcon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp)) }
            IconButton(onSeekBack, Modifier.size(56.dp)) { Text("−${Settings.seekInterval}", color = Color.White, fontSize = 15.sp) }
            IconButton({ if (ui.isPlaying) ui.player.pause() else ui.player.play() }, Modifier.size(80.dp)) {
                MIcon(if (ui.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle, "Play/Pause", tint = Color.White, modifier = Modifier.size(72.dp))
            }
            IconButton(onSeekFwd, Modifier.size(56.dp)) { Text("+${Settings.seekInterval}", color = Color.White, fontSize = 15.sp) }
            IconButton({ ui.player.seekToNextMediaItem() }, enabled = ui.hasNext) { MIcon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp)) }
        }
        // Bottom: seek bar + tools
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(drag?.toLong() ?: ui.position), color = Color.White, fontSize = 12.sp)
                Slider(
                    value = (drag ?: ui.position.toFloat()).coerceIn(0f, ui.duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { ui.player.seekTo(it.toLong()) }; drag = null },
                    valueRange = 0f..ui.duration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatDuration(ui.duration), color = Color.White, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                ToolBtn(Icons.Rounded.Speed, "${"%.2f".format(ui.speed).trimEnd('0').trimEnd('.')}x") { onDialog("speed") }
                ToolBtn(Icons.Rounded.AspectRatio, listOf("Fit", "Fill", "Zoom")[aspect], onAspect)
                ToolBtn(Icons.Rounded.ScreenRotation, "Rotate", onRotate)
                ToolBtn(Icons.Rounded.Subtitles, "Subtitles") { onDialog("subs") }
                ToolBtn(Icons.Rounded.Translate, "Audio") { onDialog("audio") }
                ToolBtn(Icons.Rounded.Bedtime, if (SleepTimer.endsAt > 0) "On" else "Sleep") { onDialog("sleep") }
                ToolBtn(Icons.Rounded.Info, "Info") { onDialog("info") }
            }
        }
    }
}

@Composable
private fun ToolBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick) {
        MIcon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
