package com.playora.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playora.app.data.Db
import com.playora.app.data.MediaFile
import com.playora.app.playback.Playback
import com.playora.app.util.formatDuration

@Composable
fun HomeScreen(vm: LibVM, nav: Nav, goTab: (Int) -> Unit) {
    val ctx = LocalContext.current
    val cont = remember(Rev.n, vm.videos) { Db.continueWatching() }
    val recent = remember(Rev.n) { Db.history(null, 15) }
    val favs = remember(Rev.n) { Db.favorites() }
    val lists = remember(Rev.n) { Db.playlists() }
    val byUri = remember(vm.videos, vm.audio) { (vm.videos + vm.audio).associateBy { it.uri.toString() } }
    val added = remember(vm.videos, vm.audio) { (vm.videos + vm.audio).sortedByDescending { it.dateAdded }.take(12) }

    ScreenScaffold("Playora", null, actions = {
        IconButton({ nav.push(Route.Search) }) { Icon(Icons.Rounded.Search, "Search") }
        IconButton({ nav.push(Route.Url) }) { Icon(Icons.Rounded.Link, "Play URL") }
    }) { pad ->
        PullToRefreshBox(vm.loading, { vm.refresh() }, Modifier.padding(pad)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                item {
                    Text("Play Everything. Anywhere.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LibraryTile("Videos", vm.videos.size, Icons.Rounded.VideoLibrary, Modifier.weight(1f)) { goTab(1) }
                        LibraryTile("Music", vm.audio.size, Icons.Rounded.MusicNote, Modifier.weight(1f)) { goTab(2) }
                    }
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickChip("Favourites", Icons.Rounded.Favorite) { nav.push(Route.Favorites) }
                        QuickChip("Playlists", Icons.Rounded.PlaylistPlay) { nav.push(Route.Playlists) }
                        QuickChip("History", Icons.Rounded.History) { nav.push(Route.History) }
                    }
                }
                if (cont.isNotEmpty()) item {
                    SectionHeader("Continue watching") { nav.push(Route.ContinueWatching) }
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(cont, key = { it.uri }) { h ->
                            val f = byUri[h.uri]
                            Card(Modifier.width(200.dp).clickable { f?.let { Playback.openMedia(ctx, it, listOf(it)) } },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Box {
                                    if (f != null) Thumb(f, Modifier.fillMaxWidth().size(112.dp))
                                    IconButton({ Db.removeHistory(h.uri); Rev.bump() }, Modifier.align(Alignment.TopEnd)) {
                                        Icon(Icons.Rounded.Close, "Remove from history", tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)))
                                    }
                                }
                                Column(Modifier.padding(10.dp)) {
                                    Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text("${formatDuration(h.positionMs)} / ${formatDuration(h.durationMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    LinearProgressIndicator({ h.progress }, Modifier.fillMaxWidth().padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
                if (recent.isNotEmpty()) item {
                    SectionHeader("Recently played") { nav.push(Route.History) }
                    MediaStrip(recent.mapNotNull { byUri[it.uri] }, ctx)
                }
                if (added.isNotEmpty()) item {
                    SectionHeader("Recently added")
                    MediaStrip(added, ctx)
                }
                if (lists.isNotEmpty()) item {
                    SectionHeader("Playlists") { nav.push(Route.Playlists) }
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(lists, key = { it.id }) { p ->
                            Card(Modifier.clickable { nav.push(Route.PlaylistDetail(p.id, p.name)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.width(140.dp).padding(14.dp)) {
                                    Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${p.count} tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (favs.isNotEmpty()) item {
                    SectionHeader("Favourites") { nav.push(Route.Favorites) }
                    MediaStrip(favs.mapNotNull { byUri[it.uri] }, ctx)
                }
                if (vm.loaded && vm.videos.isEmpty() && vm.audio.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp)) { EmptyState(Icons.Rounded.VideoLibrary, "No media found", "Add videos or music to your device, then pull down to refresh. You can also play a link from the toolbar.") }
                }
            }
        }
    }
}

@Composable
private fun MediaStrip(items: List<MediaFile>, ctx: android.content.Context) {
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.uri.toString() }) { f ->
            Column(Modifier.width(120.dp).clickable { Playback.openMedia(ctx, f, items) }) {
                Thumb(f, Modifier.fillMaxWidth().size(120.dp, 90.dp))
                Text(f.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Text(formatDuration(f.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LibraryTile(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text("$count files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.AssistChip(onClick, { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
}
