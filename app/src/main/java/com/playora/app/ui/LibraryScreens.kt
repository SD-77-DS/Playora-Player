package com.playora.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.playora.app.data.Db
import com.playora.app.data.MediaFile
import com.playora.app.data.Settings
import com.playora.app.playback.Playback

private val SORT_LABELS = listOf("Name", "Date added", "Size", "Duration")

fun sortFiles(list: List<MediaFile>, key: Int, asc: Boolean): List<MediaFile> {
    val s = when (key) {
        0 -> list.sortedBy { it.title.lowercase() }
        2 -> list.sortedBy { it.size }
        3 -> list.sortedBy { it.durationMs }
        else -> list.sortedBy { it.dateAdded }
    }
    return if (asc) s else s.reversed()
}

@Composable
private fun SortMenu() {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Rounded.Sort, "Sort") }
        DropdownMenu(open, { open = false }) {
            SORT_LABELS.forEachIndexed { i, l ->
                DropdownMenuItem({ Text(l + if (Settings.sortKey == i) (if (Settings.sortAsc) "  ↑" else "  ↓") else "") }, {
                    if (Settings.sortKey == i) Settings.sortAsc = !Settings.sortAsc else { Settings.sortKey = i; Settings.sortAsc = i == 0 }
                    open = false
                })
            }
        }
    }
}

/** Reusable list/grid with search, sort, pull-to-refresh, loading and empty states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCollection(
    vm: LibVM, items: List<MediaFile>, emptyTitle: String, emptyHint: String, header: (@Composable () -> Unit)? = null,
    onPlay: (MediaFile, List<MediaFile>) -> Unit,
) {
    var q by rememberSaveable { mutableStateOf("") }
    val shown = remember(items, q, Settings.sortKey, Settings.sortAsc) {
        sortFiles(items.filter { q.isBlank() || it.title.contains(q, true) || it.fileName.contains(q, true) }, Settings.sortKey, Settings.sortAsc)
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(q, { q = it }, "Search by file name")
        Spacer(Modifier.size(8.dp))
        PullToRefreshBox(vm.loading, { vm.refresh() }, Modifier.fillMaxSize()) {
            when {
                !vm.loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                shown.isEmpty() -> EmptyState(Icons.Rounded.VideoLibrary, if (q.isBlank()) emptyTitle else "No results for \"$q\"", if (q.isBlank()) emptyHint else "Try a different name.")
                Settings.gridView -> LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                    gridItems(shown, key = { it.uri.toString() }) { f -> MediaGridItem(f) { onPlay(f, shown) } }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (header != null) item { header() }
                    items(shown, key = { it.uri.toString() }) { f -> MediaRow(f, shown, { onPlay(f, shown) }) }
                }
            }
        }
    }
}

@Composable
private fun LibraryActions(nav: Nav) {
    IconButton({ nav.push(Route.Search) }) { Icon(Icons.Rounded.Search, "Search") }
    IconButton({ nav.push(Route.Url) }) { Icon(Icons.Rounded.Link, "Play URL") }
    IconButton({ Settings.gridView = !Settings.gridView }) { Icon(if (Settings.gridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, "Layout") }
    SortMenu()
}

@Composable
fun VideoLibraryScreen(vm: LibVM, nav: Nav) {
    val ctx = LocalContext.current
    ScreenScaffold("Videos", null, actions = { LibraryActions(nav) }) { pad ->
        Box(Modifier.padding(pad)) {
            MediaCollection(vm, vm.videos, "No videos found", "Videos on your device will appear here. Pull down to rescan.") { f, list -> Playback.openMedia(ctx, f, list) }
        }
    }
}

@Composable
fun MusicLibraryScreen(vm: LibVM, nav: Nav) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var group by rememberSaveable { mutableStateOf<String?>(null) } // selected artist or album
    BackHandler(enabled = group != null) { group = null }
    val tabs = listOf("Songs", "Artists", "Albums", "Playlists", "Favourites", "Recent")
    ScreenScaffold(group ?: "Music", if (group != null) ({ group = null }) else null, actions = { LibraryActions(nav) },
        floating = {
            if (tab == 0 && vm.audio.isNotEmpty()) FloatingActionButton({ Playback.playAudio(vm.audio.shuffled(), 0, true) }) { Icon(Icons.Rounded.Shuffle, "Shuffle all") }
        }) { pad ->
        Column(Modifier.padding(pad)) {
            if (group == null) PrimaryScrollableTabRow(tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            when {
                group != null -> {
                    val g = group!!
                    val songs = vm.audio.filter { (tab == 1 && it.artist == g) || (tab == 2 && it.album == g) }
                    MediaCollection(vm, songs, "Nothing here", "") { f, l -> Playback.openMedia(ctx, f, l) }
                }
                tab == 0 -> MediaCollection(vm, vm.audio, "No music found", "Songs on your device will appear here. Pull down to rescan.") { f, l -> Playback.openMedia(ctx, f, l) }
                tab == 1 -> GroupList(vm.audio.groupBy { it.artist }, Icons.Rounded.Person, vm) { group = it }
                tab == 2 -> GroupList(vm.audio.groupBy { it.album }, Icons.Rounded.Album, vm) { group = it }
                tab == 3 -> PlaylistsContent(nav)
                tab == 4 -> FavoritesContent(vm)
                else -> HistoryContent(vm, audioOnly = true)
            }
        }
    }
}

@Composable
private fun GroupList(groups: Map<String, List<MediaFile>>, icon: androidx.compose.ui.graphics.vector.ImageVector, vm: LibVM, onOpen: (String) -> Unit) {
    if (groups.isEmpty()) { EmptyState(Icons.Rounded.MusicNote, "No music found"); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups.entries.sortedBy { it.key.lowercase() }.toList(), key = { it.key }) { (name, songs) ->
            ListItem(
                headlineContent = { Text(name) }, supportingContent = { Text("${songs.size} songs") },
                leadingContent = { Thumb(songs.first(), Modifier.size(48.dp)) },
                modifier = Modifier.clickable { onOpen(name) },
            )
        }
    }
}

/** Folder browser: groups video and audio by their containing folder. */
@Composable
fun FoldersScreen(vm: LibVM, nav: Nav) {
    val folders = remember(vm.videos, vm.audio) { (vm.videos + vm.audio).groupBy { it.folder }.toList().sortedBy { it.first.lowercase() } }
    ScreenScaffold("Folders", null, actions = { IconButton({ nav.push(Route.Search) }) { Icon(Icons.Rounded.Search, "Search") } }) { pad ->
        Box(Modifier.padding(pad)) {
            PullToRefreshBox(vm.loading, { vm.refresh() }) {
                when {
                    !vm.loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    folders.isEmpty() -> EmptyState(Icons.Rounded.Folder, "No folders with media", "Folders containing videos or music will appear here.")
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(folders, key = { it.first }) { (name, files) ->
                            val v = files.count { it.isVideo }; val a = files.size - v
                            ListItem(
                                headlineContent = { Text(name) },
                                supportingContent = { Text(listOfNotNull(if (v > 0) "$v videos" else null, if (a > 0) "$a songs" else null).joinToString(" · ")) },
                                leadingContent = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) },
                                modifier = Modifier.clickable { nav.push(Route.FolderDetail(name)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderDetailScreen(name: String, vm: LibVM, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val files = remember(vm.videos, vm.audio, name) { (vm.videos + vm.audio).filter { it.folder == name } }
    ScreenScaffold(name, onBack, actions = { SortMenu() }) { pad ->
        Box(Modifier.padding(pad)) {
            MediaCollection(vm, files, "Folder is empty", "") { f, l -> Playback.openMedia(ctx, f, l) }
        }
    }
}

@Composable
fun SearchScreen(vm: LibVM, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val results = remember(q, vm.videos, vm.audio) {
        if (q.isBlank()) emptyList() else (vm.videos + vm.audio).filter {
            it.title.contains(q, true) || it.fileName.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }.take(300)
    }
    ScreenScaffold("Search", onBack) { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(q, { q = it }, "Search videos and music")
            Spacer(Modifier.size(8.dp))
            if (q.isBlank()) EmptyState(Icons.Rounded.Search, "Search your library", "Find videos and songs by name, artist or album.")
            else if (results.isEmpty()) EmptyState(Icons.Rounded.Search, "No results for \"$q\"")
            else LazyColumn { items(results, key = { it.uri.toString() }) { f -> MediaRow(f, results, { Playback.openMedia(ctx, f, results) }) } }
        }
    }
}
