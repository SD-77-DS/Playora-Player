package com.playora.app.ui

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.playora.app.data.Db
import com.playora.app.data.MediaFile
import com.playora.app.util.formatDuration
import com.playora.app.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bumped whenever favourites/history/playlists change so screens re-read the database. */
object Rev {
    var n by mutableIntStateOf(0)
    fun bump() { n++ }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {},
    floating: @Composable () -> Unit = {}, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { actions() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = floating,
        content = content,
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String = "", action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (action != null) { Spacer(Modifier.size(16.dp)); action() }
    }
}

@Composable
fun SectionHeader(title: String, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onMore != null) TextButton(onMore) { Text("See all") }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String = "Search", modifier: Modifier = Modifier) {
    TextField(
        value, onChange, modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton({ onChange("") }) { Icon(Icons.Rounded.Close, "Clear") } },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Thumbnail: video frame from MediaStore's own thumbnail cache, or album art via Coil, with a themed fallback. */
@Composable
fun Thumb(f: MediaFile, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(if (f.isVideo) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
        if (f.isVideo) {
            val ctx = LocalContext.current
            val bmp by produceState<android.graphics.Bitmap?>(ThumbCache.get(f.id), f.id) {
                if (value == null) value = withContext(Dispatchers.IO) { ThumbCache.load(ctx, f) }
            }
            bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        } else AsyncImage(f.artUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** In-memory LRU in front of MediaStore's persistent thumbnail cache (so scrolling never re-decodes video). */
object ThumbCache {
    private val lru = object : android.util.LruCache<Long, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 10).toInt().coerceAtMost(48 * 1024 * 1024)) {
        override fun sizeOf(k: Long, v: android.graphics.Bitmap) = v.byteCount
    }
    fun get(id: Long): android.graphics.Bitmap? = lru.get(id)
    fun clear() = lru.evictAll()
    @Suppress("DEPRECATION")
    fun load(ctx: Context, f: MediaFile): android.graphics.Bitmap? = runCatching {
        val b = if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(f.uri, android.util.Size(384, 216), null)
        else android.provider.MediaStore.Video.Thumbnails.getThumbnail(ctx.contentResolver, f.id, android.provider.MediaStore.Video.Thumbnails.MINI_KIND, null)
        b?.also { lru.put(f.id, it) }
    }.getOrNull()
}

/** A media row used in lists across the app. */
@Composable
fun MediaRow(f: MediaFile, queue: List<MediaFile>, onPlay: () -> Unit, progress: Float? = null, onRemove: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(f, Modifier.size(if (f.isVideo) 96.dp else 56.dp, 56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (f.isVideo) "${formatDuration(f.durationMs)} · ${formatSize(f.size)}" else "${f.artist} · ${formatDuration(f.durationMs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        MediaMenu(f, onRemove)
    }
}

@Composable
fun MediaGridItem(f: MediaFile, onPlay: () -> Unit) {
    Column(Modifier.clickable(onClick = onPlay).padding(6.dp)) {
        Thumb(f, Modifier.fillMaxWidth().aspectRatio(if (f.isVideo) 16f / 9f else 1f))
        Text(f.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Text(formatDuration(f.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Overflow menu: favourite, add to playlist, share, info (and optional remove). */
@Composable
fun MediaMenu(f: MediaFile, onRemove: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val fav = remember(Rev.n, f.uri) { Db.isFavorite(f.uri.toString()) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Rounded.MoreVert, "More") }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(if (fav) "Remove from favourites" else "Add to favourites") },
                { Db.toggleFavorite(f.uri.toString(), f.title, f.isVideo); Rev.bump(); open = false },
                leadingIcon = { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) })
            DropdownMenuItem({ Text("Add to playlist") }, { dialog = "playlist"; open = false })
            DropdownMenuItem({ Text("Share") }, { share(ctx, f); open = false })
            DropdownMenuItem({ Text("Properties") }, { dialog = "info"; open = false })
            if (onRemove != null) DropdownMenuItem({ Text("Remove") }, { onRemove(); open = false })
        }
    }
    when (dialog) {
        "playlist" -> AddToPlaylistDialog(f.uri.toString(), f.title, f.isVideo) { dialog = null }
        "info" -> MediaInfoDialog(f) { dialog = null }
    }
}

fun share(ctx: Context, f: MediaFile) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = f.mime; putExtra(Intent.EXTRA_STREAM, f.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share ${f.title}"))
}

@Composable
fun AddToPlaylistDialog(uri: String, title: String, isVideo: Boolean, onDismiss: () -> Unit) {
    val lists = remember { Db.playlists() }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Add to playlist") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lists.forEach { p -> TextButton({ Db.addToPlaylist(p.id, uri, title, isVideo); Rev.bump(); onDismiss() }, Modifier.fillMaxWidth()) { Text("${p.name} (${p.count})", Modifier.fillMaxWidth()) } }
                TextField(newName, { newName = it }, placeholder = { Text("New playlist name") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({ if (newName.isNotBlank()) { val id = Db.createPlaylist(newName.trim()); Db.addToPlaylist(id, uri, title, isVideo); Rev.bump(); onDismiss() } }, enabled = newName.isNotBlank()) { Text("Create & add") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Shows real file properties; technical fields come from MediaMetadataRetriever when available. */
@Composable
fun MediaInfoDialog(f: MediaFile, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra by produceState<List<Pair<String, String>>>(emptyList(), f) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, f.uri)
                fun m(k: Int) = r.extractMetadata(k)
                buildList {
                    m(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { add("Format" to it) }
                    if (f.isVideo) {
                        val w = m(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); val h = m(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        if (w != null && h != null) add("Resolution" to "${w}×${h}")
                        if (Build.VERSION.SDK_INT >= 31) m(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let { add("Frame rate" to "$it fps") }
                    } else {
                        m(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { add("Genre" to it) }
                        m(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let { add("Album artist" to it) }
                        if (Build.VERSION.SDK_INT >= 31) m(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.let { add("Sample rate" to "$it Hz") }
                    }
                    m(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { add("Bitrate" to "${it / 1000} kbps") }
                }
            } catch (_: Exception) { emptyList() } finally { runCatching { r.release() } }
        }
    }
    val rows = buildList {
        add("File name" to f.fileName); add("Folder" to f.folder)
        if (!f.isVideo) { add("Artist" to f.artist); add("Album" to f.album) }
        add("Size" to formatSize(f.size)); add("Duration" to formatDuration(f.durationMs))
        addAll(extra)
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Properties") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            rows.forEach { (k, v) -> Column(Modifier.padding(vertical = 4.dp)) {
                Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(v)
            } }
        } },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}
