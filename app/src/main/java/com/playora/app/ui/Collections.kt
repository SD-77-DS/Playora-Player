package com.playora.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.playora.app.data.Db
import com.playora.app.data.MediaFile
import com.playora.app.playback.Playback
import androidx.compose.material3.MaterialTheme

private fun LibVM.index() = (videos + audio).associateBy { it.uri.toString() }

@Composable
fun FavoritesContent(vm: LibVM) {
    val ctx = LocalContext.current
    val favs = remember(Rev.n) { Db.favorites() }
    val idx = remember(vm.videos, vm.audio) { vm.index() }
    val files = favs.mapNotNull { idx[it.uri] }
    if (files.isEmpty()) EmptyState(Icons.Rounded.Favorite, "No favourites yet", "Open the ⋮ menu on any video or song and choose Add to favourites.")
    else LazyColumn(Modifier.fillMaxSize()) { items(files, key = { it.uri.toString() }) { f -> MediaRow(f, files, { Playback.openMedia(ctx, f, files) }) } }
}

@Composable
fun FavoritesScreen(vm: LibVM, onBack: () -> Unit) = ScreenScaffold("Favourites", onBack) { Box(Modifier.padding(it)) { FavoritesContent(vm) } }

@Composable
fun HistoryContent(vm: LibVM, audioOnly: Boolean = false) {
    val ctx = LocalContext.current
    val entries = remember(Rev.n, audioOnly) { Db.history(if (audioOnly) false else null) }
    val idx = remember(vm.videos, vm.audio) { vm.index() }
    val files = entries.mapNotNull { idx[it.uri] }
    if (files.isEmpty()) EmptyState(Icons.Rounded.History, "Nothing played yet", "Things you play will show up here.")
    else LazyColumn(Modifier.fillMaxSize()) {
        items(entries.filter { idx.containsKey(it.uri) }, key = { it.uri }) { h ->
            val f = idx.getValue(h.uri)
            MediaRow(f, files, { Playback.openMedia(ctx, f, files) }, progress = if (f.isVideo && h.progress > 0.01f && h.progress < 0.95f) h.progress else null,
                onRemove = { Db.removeHistory(h.uri); Rev.bump() })
        }
    }
}

@Composable
fun HistoryScreen(vm: LibVM, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ScreenScaffold("Playback history", onBack, actions = { IconButton({ confirm = true }) { Icon(Icons.Rounded.Delete, "Clear history") } }) { Box(Modifier.padding(it)) { HistoryContent(vm) } }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Clear history?") }, text = { Text("This removes playback history and resume positions.") },
        confirmButton = { TextButton({ Db.clearHistory(); Rev.bump(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

@Composable
fun ContinueWatchingScreen(vm: LibVM, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val entries = remember(Rev.n) { Db.continueWatching() }
    val idx = remember(vm.videos) { vm.index() }
    ScreenScaffold("Continue watching", onBack) { pad ->
        Box(Modifier.padding(pad)) {
            val shown = entries.filter { idx.containsKey(it.uri) }
            if (shown.isEmpty()) EmptyState(Icons.Rounded.History, "Nothing to resume", "Videos you stop partway through appear here.")
            else LazyColumn { items(shown, key = { it.uri }) { h ->
                val f = idx.getValue(h.uri)
                MediaRow(f, listOf(f), { Playback.openMedia(ctx, f, listOf(f)) }, progress = h.progress, onRemove = { Db.removeHistory(h.uri); Rev.bump() })
            } }
        }
    }
}

// ---------------- Playlists ----------------

@Composable
fun PlaylistsContent(nav: Nav) {
    val lists = remember(Rev.n) { Db.playlists() }
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (lists.isEmpty()) EmptyState(Icons.Rounded.PlaylistPlay, "No playlists", "Create a playlist, then add songs from the ⋮ menu.") {
            TextButton({ creating = true }) { Text("Create playlist") }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(lists, key = { it.id }) { p ->
                ListItem({ Text(p.name) }, supportingContent = { Text("${p.count} items") },
                    leadingContent = { Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { nav.push(Route.PlaylistDetail(p.id, p.name)) })
            }
        }
        if (lists.isNotEmpty()) androidx.compose.material3.FloatingActionButton({ creating = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Rounded.Add, "New playlist") }
    }
    if (creating) NameDialog("New playlist", "", { creating = false }) { Db.createPlaylist(it); Rev.bump(); creating = false }
}

@Composable
fun PlaylistsScreen(vm: LibVM, nav: Nav, onBack: () -> Unit) = ScreenScaffold("Playlists", onBack) { Box(Modifier.padding(it)) { PlaylistsContent(nav) } }

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(onDismiss, title = { Text(title) }, text = { TextField(t, { t = it }, singleLine = true, placeholder = { Text("Name") }) },
        confirmButton = { TextButton({ onOk(t.trim()) }, enabled = t.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun PlaylistDetailScreen(id: Long, name: String, vm: LibVM, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(name) }
    val items = remember(Rev.n, id) { Db.playlistItems(id) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    ScreenScaffold(title, onBack, actions = {
        IconButton({ rename = true }) { Icon(Icons.Rounded.Edit, "Rename") }
        IconButton({ delete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            if (items.isEmpty()) EmptyState(Icons.Rounded.PlaylistPlay, "Empty playlist", "Add songs from the ⋮ menu in your library.")
            else {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button({ Playback.playPlaylist(items) }) { Icon(Icons.Rounded.PlayArrow, null); Text("Play") }
                    androidx.compose.material3.OutlinedButton({ Playback.playPlaylist(items, shuffle = true) }) { Icon(Icons.Rounded.Shuffle, null); Text("Shuffle") }
                }
                LazyColumn {
                    items(items.size, key = { "${items[it].uri}#$it" }) { i ->
                        val it = items[i]
                        ListItem({ Text(it.title) }, supportingContent = { Text(if (it.isVideo) "Video" else "Audio") },
                            modifier = Modifier.clickable {
                                if (it.isVideo) Playback.openVideo(ctx, it.uri.toUri(), it.title) else Playback.playPlaylist(items, i)
                            },
                            trailingContent = { Row {
                                IconButton({ Db.moveInPlaylist(id, i, i - 1); Rev.bump() }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                                IconButton({ Db.moveInPlaylist(id, i, i + 1); Rev.bump() }, enabled = i < items.size - 1) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                                IconButton({ Db.removeFromPlaylist(id, i); Rev.bump() }) { Icon(Icons.Rounded.Delete, "Remove") }
                            } })
                    }
                }
            }
        }
    }
    if (rename) NameDialog("Rename playlist", title, { rename = false }) { Db.renamePlaylist(id, it); title = it; Rev.bump(); rename = false }
    if (delete) AlertDialog({ delete = false }, title = { Text("Delete playlist?") }, text = { Text("\"$title\" will be deleted. Your files are not affected.") },
        confirmButton = { TextButton({ Db.deletePlaylist(id); Rev.bump(); delete = false; onBack() }) { Text("Delete") } },
        dismissButton = { TextButton({ delete = false }) { Text("Cancel") } })
}
