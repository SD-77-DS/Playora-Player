package com.playora.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Reads real device media through MediaStore (scoped-storage friendly, no broad file access). */
object MediaRepo {

    fun loadVideos(c: Context): List<MediaFile> {
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.MIME_TYPE,
        )
        return query(c, base, proj, null) { cur ->
            val id = cur.getLong(0)
            val name = cur.getString(1) ?: ""
            MediaFile(
                id, ContentUris.withAppendedId(base, id), cur.getString(2)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                name, cur.getLong(3), cur.getLong(4), cur.getLong(5), true,
                cur.getString(6) ?: "Unknown", "", "", 0, cur.getString(7) ?: "video/*",
            )
        }
    }

    fun loadAudio(c: Context): List<MediaFile> {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA,
        )
        // Exclude ringtones/notification sounds/alarms: only music-like content.
        val sel = "${MediaStore.Audio.Media.DURATION} > 0 AND ${MediaStore.Audio.Media.IS_RINGTONE}=0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION}=0 AND ${MediaStore.Audio.Media.IS_ALARM}=0"
        return query(c, base, proj, sel) { cur ->
            val id = cur.getLong(0)
            val name = cur.getString(1) ?: ""
            val artist = cur.getString(6)?.takeIf { it != "<unknown>" } ?: "Unknown artist"
            val album = cur.getString(7)?.takeIf { it != "<unknown>" } ?: "Unknown album"
            val folder = cur.getString(10)?.substringBeforeLast('/')?.substringAfterLast('/') ?: "Unknown"
            MediaFile(
                id, ContentUris.withAppendedId(base, id), cur.getString(2)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                name, cur.getLong(3), cur.getLong(4), cur.getLong(5), false,
                folder, artist, album, cur.getLong(8), cur.getString(9) ?: "audio/*",
            )
        }
    }

    private fun query(c: Context, uri: Uri, proj: Array<String>, sel: String?, map: (android.database.Cursor) -> MediaFile): List<MediaFile> {
        val out = ArrayList<MediaFile>()
        try {
            c.contentResolver.query(uri, proj, sel, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cur ->
                while (cur.moveToNext()) runCatching { map(cur) }.onSuccess { out.add(it) }
            }
        } catch (_: SecurityException) { /* permission not granted yet */ }
        return out
    }

    /** Whether the runtime media permission needed on this Android version is granted. */
    fun permissionsNeeded(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
        else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}
