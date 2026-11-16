package com.playora.app.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.playora.app.data.Settings
import com.playora.app.playback.SleepTimer
import com.playora.app.util.formatSize
import java.util.Locale
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)

@Composable
fun SpeedDialog(player: Player, onDismiss: () -> Unit) {
    var custom by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SPEEDS.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { s ->
                                val sel = abs(custom - s) < 0.01f
                                androidx.compose.material3.FilterChip(sel, { custom = s; player.setPlaybackSpeed(s) }, { Text("${s}x") })
                            }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text("Custom: ${"%.2f".format(custom)}x")
                Slider(custom, { custom = (it * 20).roundToInt() / 20f; player.setPlaybackSpeed(custom) }, valueRange = 0.25f..4f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use as default", Modifier.weight(1f))
                    TextButton({ Settings.defaultSpeed = custom }) { Text(if (Settings.defaultSpeed == custom) "Saved" else "Save") }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}

private fun abs(f: Float) = if (f < 0) -f else f

/** One selectable track (a format inside a group). */
private data class TrackOption(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

private fun trackOptions(player: Player, type: Int): List<TrackOption> =
    player.currentTracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            val f = g.getTrackFormat(i)
            val lang = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage }
            val label = listOfNotNull(f.label, lang, if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount}ch" else null, f.sampleMimeType?.substringAfter('/')?.uppercase())
                .distinct().joinToString(" · ").ifBlank { "Track ${i + 1}" }
            TrackOption(g, i, label, g.isTrackSelected(i))
        }
    }

private fun selectTrack(player: Player, type: Int, opt: TrackOption?) {
    val b = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
    if (opt == null) b.setTrackTypeDisabled(type, true)
    else b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
    player.trackSelectionParameters = b.build()
}

@Composable
fun TrackDialog(title: String, type: Int, player: Player, allowOff: Boolean, onDismiss: () -> Unit) {
    val opts = trackOptions(player, type)
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(type)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (opts.isEmpty()) Text("No ${title.lowercase()}s available for this media.")
                if (allowOff && opts.isNotEmpty()) TrackRow("Off", disabled) { selectTrack(player, type, null); onDismiss() }
                opts.forEach { o -> TrackRow(o.label, o.selected && !disabled) { selectTrack(player, type, o); onDismiss() } }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label)
    }
}

private val SUB_COLORS = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFF4ADE80, 0xFF22D3EE, 0xFFFB923C).map { it.toInt() }

/** Dedicated subtitle configuration panel: track, external file, size, colour, background, position. */
@Composable
fun SubtitlePanel(player: Player, onPick: () -> Unit, onDismiss: () -> Unit) {
    val opts = trackOptions(player, C.TRACK_TYPE_TEXT)
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Subtitles") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TrackRow("Off", disabled || opts.none { it.selected }) { selectTrack(player, C.TRACK_TYPE_TEXT, null) }
                opts.forEach { o -> TrackRow(o.label, o.selected && !disabled) { selectTrack(player, C.TRACK_TYPE_TEXT, o) } }
                if (opts.isEmpty()) Text("No subtitle tracks found.", style = MaterialTheme.typography.bodySmall)
                Button(onPick, Modifier.padding(vertical = 8.dp)) { Text("Load subtitle file (SRT, ASS, SSA, VTT)") }
                Text("Size ${Settings.subSize}")
                Slider(Settings.subSize.toFloat(), { Settings.subSize = it.roundToInt() }, valueRange = 3f..12f)
                Text("Position from bottom ${Settings.subBottom}%")
                Slider(Settings.subBottom.toFloat(), { Settings.subBottom = it.roundToInt() }, valueRange = 0f..40f)
                Text("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    SUB_COLORS.forEach { c ->
                        Spacer(Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                            .border(if (Settings.subColor == c) 3.dp else 1.dp, if (Settings.subColor == c) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                            .clickable { Settings.subColor = c })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Background", Modifier.weight(1f)); Switch(Settings.subBg, { Settings.subBg = it })
                }
                Text("Subtitle timing offset isn't supported by the playback engine yet.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}

@Composable
fun SleepDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Sleep timer") },
        text = {
            Column {
                if (SleepTimer.endsAt > 0) {
                    val left = ((SleepTimer.endsAt - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                    Text("Playback will pause in about $left min.", Modifier.padding(bottom = 8.dp))
                    TextButton({ SleepTimer.cancel(); onDismiss() }) { Text("Turn off timer") }
                }
                listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                    TextButton({ SleepTimer.start(m); onDismiss() }) { Text("$m minutes") }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

private fun queryFile(ctx: Context, uri: Uri): Pair<String?, Long?> = runCatching {
    if (uri.scheme != "content") return@runCatching null to null
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) to it.getLong(1) else null to null
    } ?: (null to null)
}.getOrDefault(null to null)

/** Technical info built only from metadata the engine actually reports. */
@Composable
fun VideoInfoDialog(req: PlayRequest, player: Player, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val (name, size) = remember(req) { queryFile(ctx, req.uri) }
    val tracks = player.currentTracks
    fun fmt(type: Int) = tracks.groups.filter { it.type == type }.flatMap { g -> (0 until g.length).filter { g.isTrackSelected(it) }.map { g.getTrackFormat(it) } }
    val v = fmt(C.TRACK_TYPE_VIDEO).firstOrNull()
    val a = fmt(C.TRACK_TYPE_AUDIO).firstOrNull()
    val subs = tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
    val rows = buildList {
        add("File name" to (name ?: req.title))
        add("Location" to (if (req.uri.scheme?.startsWith("http") == true) req.uri.toString() else req.uri.path ?: req.uri.toString()))
        size?.takeIf { it > 0 }?.let { add("File size" to formatSize(it)) }
        v?.containerMimeType?.let { add("Container" to it) }
        v?.sampleMimeType?.let { add("Video codec" to it.substringAfter('/') + (v.codecs?.let { c -> " ($c)" } ?: "")) }
        v?.takeIf { it.width > 0 }?.let { add("Resolution" to "${it.width}×${it.height}") }
        v?.takeIf { it.frameRate > 0 }?.let { add("Frame rate" to "%.2f fps".format(it.frameRate)) }
        (v?.bitrate?.takeIf { it > 0 })?.let { add("Video bitrate" to "${it / 1000} kbps") }
        a?.sampleMimeType?.let { add("Audio codec" to it.substringAfter('/') + (a.channelCount.takeIf { c -> c > 0 }?.let { c -> " · ${c}ch" } ?: "")) }
        a?.takeIf { it.sampleRate > 0 }?.let { add("Sample rate" to "${it.sampleRate} Hz") }
        if (player.duration > 0) add("Duration" to com.playora.app.util.formatDuration(player.duration))
        add("Subtitle tracks" to subs.toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Video information") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            rows.forEach { (k, v2) -> Column(Modifier.padding(vertical = 4.dp)) {
                Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(v2, style = MaterialTheme.typography.bodyMedium)
            } }
        } },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}
