package com.playora.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.playora.app.data.Db
import com.playora.app.playback.Playback
import com.playora.app.playback.SleepTimer
import com.playora.app.playback.rememberPlayerUi
import com.playora.app.player.PlayerActivity
import com.playora.app.player.SleepDialog
import com.playora.app.player.SpeedDialog
import com.playora.app.util.formatDuration

/** Compact player pinned above the bottom navigation while something is loaded. */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val c = Playback.controller ?: return
    val ui = rememberPlayerUi(c)
    if (ui.count == 0 || ui.state == Player.STATE_IDLE && !ui.isPlaying && ui.mediaId.isEmpty()) return
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                    AsyncImage(ui.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(ui.title.ifBlank { "Playing" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (ui.artist.isNotBlank()) Text(ui.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton({ if (ui.isPlaying) c.pause() else c.play() }) { Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause") }
                IconButton({ c.stop(); c.clearMediaItems() }) { Icon(Icons.Rounded.Close, "Stop") }
            }
            androidx.compose.material3.LinearProgressIndicator({ if (ui.duration > 0) ui.position.toFloat() / ui.duration else 0f }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun NowPlayingScreen(onBack: () -> Unit, nav: Nav) {
    val ctx = LocalContext.current
    val c = Playback.controller
    if (c == null || c.mediaItemCount == 0) {
        ScreenScaffold("Now playing", onBack) { Box(Modifier.padding(it)) { EmptyState(Icons.Rounded.MusicNote, "Nothing playing", "Pick a song or video to start.") } }
        return
    }
    val ui = rememberPlayerUi(c)
    var dialog by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val fav = remember(Rev.n, ui.mediaId) { Db.isFavorite(ui.mediaId) }
    val isVideo = c.currentMediaItem?.let { Playback.isVideo(it) } == true

    ScreenScaffold("Now playing", onBack, actions = {
        IconButton({ nav.push(Route.Equalizer) }) { Icon(Icons.Rounded.Equalizer, "Equalizer") }
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f, fill = false).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(if (isVideo) Icons.Rounded.Headphones else Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
                AsyncImage(ui.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.size(20.dp))
            Text(ui.title.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            val sub = listOf(ui.artist, ui.album).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isVideo) androidx.compose.material3.AssistChip(
                { ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply { data = ui.mediaId.toUri(); putExtra(PlayerActivity.EXTRA_TITLE, ui.title) }) },
                { Text("Audio-only · Return to video") }, leadingIcon = { Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp)) }, modifier = Modifier.padding(top = 8.dp))

            Slider(
                value = (drag ?: ui.position.toFloat()).coerceIn(0f, ui.duration.coerceAtLeast(1).toFloat()),
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { c.seekTo(it.toLong()) }; drag = null },
                valueRange = 0f..ui.duration.coerceAtLeast(1).toFloat(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(drag?.toLong() ?: ui.position), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(ui.duration), style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ c.shuffleModeEnabled = !ui.shuffle }) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (ui.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton({ c.seekToPrevious() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                IconButton({ if (ui.isPlaying) c.pause() else c.play() }, Modifier.size(72.dp)) {
                    Icon(if (ui.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle, "Play/Pause", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(68.dp))
                }
                IconButton({ c.seekToNext() }, enabled = ui.hasNext) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp)) }
                IconButton({
                    c.repeatMode = when (ui.repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                }) {
                    Icon(if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
                        tint = if (ui.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton({ dialog = "speed" }) { Icon(Icons.Rounded.Speed, "Speed") }
                IconButton({ dialog = "sleep" }) { Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (SleepTimer.endsAt > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                IconButton({ Db.toggleFavorite(ui.mediaId, ui.title, isVideo); Rev.bump() }) { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                IconButton({ dialog = "queue" }) { Icon(Icons.Rounded.QueueMusic, "Queue") }
            }
        }
    }
    when (dialog) {
        "speed" -> SpeedDialog(c) { dialog = null }
        "sleep" -> SleepDialog { dialog = null }
        "queue" -> AlertDialog({ dialog = null }, title = { Text("Queue (${ui.count})") },
            text = {
                LazyColumn {
                    itemsIndexed(List(ui.count) { c.getMediaItemAt(it) }) { i, item ->
                        ListItem({ Text(item.mediaMetadata.title?.toString() ?: "Track ${i + 1}", maxLines = 1, color = if (i == ui.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                            supportingContent = { item.mediaMetadata.artist?.let { Text(it.toString(), maxLines = 1) } },
                            modifier = Modifier.clickable { c.seekTo(i, 0); c.play() },
                            trailingContent = { IconButton({ c.removeMediaItem(i); if (c.mediaItemCount == 0) dialog = null }) { Icon(Icons.Rounded.Close, "Remove from queue") } })
                    }
                }
            },
            confirmButton = { TextButton({ dialog = null }) { Text("Close") } })
    }
}
