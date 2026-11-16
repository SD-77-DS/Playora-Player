package com.playora.app.playback

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import kotlinx.coroutines.delay

/** Compose-observable snapshot of a [Player]'s state. */
@Stable
class PlayerUi(val player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
    var state by mutableIntStateOf(player.playbackState)
    var position by mutableLongStateOf(player.currentPosition)
    var duration by mutableLongStateOf(player.duration.coerceAtLeast(0))
    var buffered by mutableLongStateOf(player.bufferedPosition)
    var title by mutableStateOf(player.mediaMetadata.title?.toString() ?: "")
    var artist by mutableStateOf(player.mediaMetadata.artist?.toString() ?: "")
    var album by mutableStateOf(player.mediaMetadata.albumTitle?.toString() ?: "")
    var art by mutableStateOf<Uri?>(player.mediaMetadata.artworkUri)
    var mediaId by mutableStateOf(player.currentMediaItem?.mediaId ?: "")
    var shuffle by mutableStateOf(player.shuffleModeEnabled)
    var repeat by mutableIntStateOf(player.repeatMode)
    var speed by mutableFloatStateOf(player.playbackParameters.speed)
    var index by mutableIntStateOf(player.currentMediaItemIndex)
    var count by mutableIntStateOf(player.mediaItemCount)
    var videoSize by mutableStateOf(player.videoSize)
    var hasNext by mutableStateOf(player.hasNextMediaItem())
    var hasPrev by mutableStateOf(player.hasPreviousMediaItem())

    fun sync() {
        isPlaying = player.isPlaying
        state = player.playbackState
        position = player.currentPosition
        duration = player.duration.coerceAtLeast(0)
        buffered = player.bufferedPosition
        title = player.mediaMetadata.title?.toString() ?: ""
        artist = player.mediaMetadata.artist?.toString() ?: ""
        album = player.mediaMetadata.albumTitle?.toString() ?: ""
        art = player.mediaMetadata.artworkUri
        mediaId = player.currentMediaItem?.mediaId ?: ""
        shuffle = player.shuffleModeEnabled
        repeat = player.repeatMode
        speed = player.playbackParameters.speed
        index = player.currentMediaItemIndex
        count = player.mediaItemCount
        videoSize = player.videoSize
        hasNext = player.hasNextMediaItem()
        hasPrev = player.hasPreviousMediaItem()
    }
}

@Composable
fun rememberPlayerUi(player: Player): PlayerUi {
    val ui = remember(player) { PlayerUi(player) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) { ui.sync() }
            override fun onVideoSizeChanged(videoSize: VideoSize) { ui.sync() }
        }
        player.addListener(l)
        ui.sync()
        onDispose { player.removeListener(l) }
    }
    // Position changes continuously and has no listener callback, so poll while visible.
    LaunchedEffect(player) {
        while (true) { ui.sync(); delay(400) }
    }
    return ui
}
