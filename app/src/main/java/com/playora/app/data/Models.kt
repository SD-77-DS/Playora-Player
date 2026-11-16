package com.playora.app.data

import android.net.Uri

/** A media file discovered on the device through MediaStore. */
data class MediaFile(
    val id: Long,
    val uri: Uri,
    val title: String,
    val fileName: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val isVideo: Boolean,
    val folder: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val mime: String,
) {
    /** Album art for audio (works with Coil); videos use frame extraction from [uri]. */
    val artUri: Uri?
        get() = if (isVideo) null else Uri.parse("content://media/external/audio/albumart/$albumId")
}

/** A row of playback history. */
data class HistoryEntry(
    val uri: String, val title: String, val isVideo: Boolean,
    val positionMs: Long, val durationMs: Long, val updated: Long,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

data class FavoriteEntry(val uri: String, val title: String, val isVideo: Boolean)
data class UrlEntry(val url: String, val favorite: Boolean, val updated: Long)
data class Playlist(val id: Long, val name: String, val count: Int)
data class PlaylistItem(val uri: String, val title: String, val isVideo: Boolean)
