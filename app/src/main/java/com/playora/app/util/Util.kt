package com.playora.app.util

import android.net.Uri
import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** Validation and classification of user-entered streaming URLs. */
object UrlValidator {
    private val audioExt = setOf("mp3", "aac", "m4a", "flac", "ogg", "opus", "wav", "oga", "wma", "amr")

    /** Returns a normalised http(s) URL or null if the input cannot be a media link. */
    fun normalise(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val withScheme = if (t.contains("://")) t else "https://$t"
        val u = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        val scheme = u.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (u.host.isNullOrBlank() || !u.host!!.contains('.') && u.host != "localhost") return null
        return withScheme
    }

    private fun ext(url: String) = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()

    fun isAudio(url: String) = ext(url) in audioExt
    fun isAdaptive(url: String) = ext(url).let { it == "m3u8" || it == "mpd" }
}
