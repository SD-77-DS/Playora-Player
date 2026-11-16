package com.playora.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.playora.app.data.Db
import com.playora.app.data.MediaFile
import com.playora.app.data.PlaylistItem
import com.playora.app.data.Settings
import com.playora.app.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** App-wide connection to [PlaybackService] plus helpers to start playback. */
object Playback {
    var controller by mutableStateOf<MediaController?>(null); private set
    private var connecting = false
    private val scope = CoroutineScope(Dispatchers.Main)

    fun connect(ctx: Context) {
        if (controller != null || connecting) return
        connecting = true
        val app = ctx.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            connecting = false
            runCatching { future.get() }.onSuccess { c ->
                c.addListener(historyListener)
                controller = c
            }
        }, ContextCompat.getMainExecutor(app))
        // Periodic progress save for resume/continue-watching (cheap: one row write every 5 s while playing).
        scope.launch {
            while (true) {
                delay(5_000)
                saveCurrent()
            }
        }
    }

    fun saveCurrent() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        if (!c.isPlaying) return
        val dur = c.duration.takeIf { it > 0 } ?: return
        Db.saveProgress(item.mediaId, item.mediaMetadata.title?.toString() ?: "", isVideo(item), c.currentPosition, dur)
    }

    fun isVideo(item: MediaItem) = item.mediaMetadata.extras?.getBoolean("video") == true

    private val historyListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Record audio plays in history when a new track starts (videos record progress in the player).
            if (mediaItem != null && !isVideo(mediaItem)) {
                Db.saveProgress(mediaItem.mediaId, mediaItem.mediaMetadata.title?.toString() ?: "", false, 0, 0)
            }
        }
    }

    fun audioItem(f: MediaFile): MediaItem = buildItem(f.uri, f.title, f.artist, f.album, f.artUri, false)

    fun buildItem(uri: Uri, title: String, artist: String?, album: String?, art: Uri?, video: Boolean): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(art)
                    .setExtras(Bundle().apply { putBoolean("video", video) })
                    .build(),
            ).build()

    /** Play [list] (audio) starting at [index]; shows up in the system media notification. */
    fun playAudio(list: List<MediaFile>, index: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        c.setMediaItems(list.map(::audioItem), index.coerceIn(0, (list.size - 1).coerceAtLeast(0)), 0)
        c.setPlaybackSpeed(Settings.defaultSpeed)
        c.shuffleModeEnabled = shuffle
        c.prepare(); c.play()
    }

    fun playPlaylist(items: List<PlaylistItem>, index: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        val audio = items.filter { !it.isVideo }
        if (audio.isEmpty()) return
        val start = audio.indexOfFirst { it.uri == items.getOrNull(index)?.uri }.coerceAtLeast(0)
        c.setMediaItems(audio.map { buildItem(Uri.parse(it.uri), it.title, null, null, null, false) }, start, 0)
        c.shuffleModeEnabled = shuffle
        c.prepare(); c.play()
    }

    fun openVideo(ctx: Context, uri: Uri, title: String, subtitleUrl: String? = null) {
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
            data = uri
            putExtra(PlayerActivity.EXTRA_TITLE, title)
            subtitleUrl?.let { putExtra(PlayerActivity.EXTRA_SUBTITLE_URL, it) }
        })
    }

    fun openMedia(ctx: Context, f: MediaFile, queue: List<MediaFile>) {
        if (f.isVideo) openVideo(ctx, f.uri, f.title)
        else playAudio(queue.filter { !it.isVideo }, queue.filter { !it.isVideo }.indexOfFirst { it.id == f.id })
    }
}

/** Sleep timer that pauses playback after a delay. Works for audio and video. */
object SleepTimer {
    var endsAt by mutableLongStateOf(0L); private set
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun start(minutes: Int) {
        cancel()
        endsAt = System.currentTimeMillis() + minutes * 60_000L
        job = scope.launch {
            delay(minutes * 60_000L)
            Playback.controller?.pause()
            endsAt = 0
        }
    }

    fun cancel() { job?.cancel(); job = null; endsAt = 0 }
}
