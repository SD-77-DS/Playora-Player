package com.playora.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.playora.app.data.Db
import com.playora.app.playback.Playback
import com.playora.app.util.UrlValidator

/** Play a direct media link (progressive, HLS .m3u8 or DASH .mpd). Does not try to scrape web pages. */
@Composable
fun UrlScreen(onBack: () -> Unit, nav: Nav) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val saved = remember(Rev.n) { Db.urls() }

    fun play(raw: String) {
        val norm = UrlValidator.normalise(raw)
        if (norm == null) { error = "That doesn't look like a valid http(s) link."; return }
        val sub = subUrl.takeIf { it.isNotBlank() }?.let { UrlValidator.normalise(it) ?: run { error = "The subtitle link isn't valid."; return } }
        error = null
        Db.addUrl(norm); Rev.bump()
        val title = norm.substringBefore('?').substringAfterLast('/').ifBlank { "Stream" }
        if (UrlValidator.isAudio(norm)) {
            Playback.controller?.let { c ->
                c.setMediaItem(Playback.buildItem(norm.toUri(), title, null, null, null, false)); c.prepare(); c.play()
                nav.push(Route.NowPlaying)
            } ?: run { error = "Player isn't ready yet. Try again." }
        } else Playback.openVideo(ctx, norm.toUri(), title, sub)
    }

    ScreenScaffold("Play URL", onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                OutlinedTextField(url, { url = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Video or audio link") },
                    placeholder = { Text("https://example.com/video.mp4 or .m3u8 / .mpd") }, singleLine = true, isError = error != null,
                    supportingText = { Text(error ?: "Direct media links only. DRM-protected or sign-in-only streams can't be played.") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = { if (url.isNotEmpty()) IconButton({ url = ""; error = null }) { Icon(Icons.Rounded.Clear, "Clear") } })
                OutlinedTextField(subUrl, { subUrl = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Subtitle link (optional)") }, singleLine = true,
                    placeholder = { Text("https://…/subtitles.srt or .vtt") })
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton({
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                        if (t.isNullOrBlank()) Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show() else url = t.trim()
                    }) { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Paste") }
                    Button({ play(url) }, enabled = url.isNotBlank()) { Icon(Icons.Rounded.PlayArrow, null); Text("Play") }
                }
            }
            if (saved.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent & favourite links", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton({ Db.clearUrls(); Rev.bump() }) { Text("Clear recent") }
                    }
                }
                items(saved.sortedByDescending { it.favorite }, key = { it.url }) { u ->
                    ListItem({ Text(u.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Rounded.Link, null) },
                        modifier = Modifier.clickable { url = u.url; play(u.url) },
                        trailingContent = { Row {
                            IconButton({ Db.setUrlFavorite(u.url, !u.favorite); Rev.bump() }) { Icon(if (u.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favourite", tint = if (u.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton({ Db.removeUrl(u.url); Rev.bump() }) { Icon(Icons.Rounded.Delete, "Remove") }
                        } })
                }
            } else item { Text("Links you play are saved here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
