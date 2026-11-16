package com.playora.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local SQLite store: history/positions, favourites, streaming URLs and playlists. */
object Db {
    private lateinit var helper: SQLiteOpenHelper
    private val db: SQLiteDatabase get() = helper.writableDatabase

    fun init(c: Context) {
        helper = object : SQLiteOpenHelper(c.applicationContext, "playora.db", null, 1) {
            override fun onCreate(d: SQLiteDatabase) {
                d.execSQL("CREATE TABLE history(uri TEXT PRIMARY KEY, title TEXT, video INTEGER, pos INTEGER, dur INTEGER, updated INTEGER)")
                d.execSQL("CREATE TABLE favorites(uri TEXT PRIMARY KEY, title TEXT, video INTEGER, added INTEGER)")
                d.execSQL("CREATE TABLE urls(url TEXT PRIMARY KEY, fav INTEGER, updated INTEGER)")
                d.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")
                d.execSQL("CREATE TABLE playlist_items(pid INTEGER, pos INTEGER, uri TEXT, title TEXT, video INTEGER)")
            }
            override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {}
        }
    }

    // ---- History / resume ----
    fun saveProgress(uri: String, title: String, isVideo: Boolean, pos: Long, dur: Long) {
        val cv = ContentValues().apply {
            put("uri", uri); put("title", title); put("video", if (isVideo) 1 else 0)
            put("pos", pos); put("dur", dur); put("updated", System.currentTimeMillis())
        }
        db.insertWithOnConflict("history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun positionFor(uri: String): Long =
        db.rawQuery("SELECT pos, dur FROM history WHERE uri=?", arrayOf(uri)).use {
            if (it.moveToFirst()) {
                val p = it.getLong(0); val d = it.getLong(1)
                if (d > 0 && p > d * 0.95) 0L else p
            } else 0L
        }

    fun history(videoOnly: Boolean? = null, limit: Int = 200): List<HistoryEntry> {
        val where = when (videoOnly) { true -> "WHERE video=1"; false -> "WHERE video=0"; null -> "" }
        return db.rawQuery("SELECT uri,title,video,pos,dur,updated FROM history $where ORDER BY updated DESC LIMIT $limit", null).use {
            buildList { while (it.moveToNext()) add(HistoryEntry(it.getString(0), it.getString(1), it.getInt(2) == 1, it.getLong(3), it.getLong(4), it.getLong(5))) }
        }
    }

    /** Videos that are partially watched: more than 5 s in and less than 95 % done. */
    fun continueWatching(): List<HistoryEntry> =
        history(true).filter { it.positionMs > 5_000 && it.progress < 0.95f }

    fun removeHistory(uri: String) { db.delete("history", "uri=?", arrayOf(uri)) }
    fun clearHistory() { db.delete("history", null, null) }

    // ---- Favourites ----
    fun isFavorite(uri: String) = db.rawQuery("SELECT 1 FROM favorites WHERE uri=?", arrayOf(uri)).use { it.moveToFirst() }
    fun toggleFavorite(uri: String, title: String, isVideo: Boolean): Boolean {
        return if (isFavorite(uri)) { db.delete("favorites", "uri=?", arrayOf(uri)); false } else {
            val cv = ContentValues().apply { put("uri", uri); put("title", title); put("video", if (isVideo) 1 else 0); put("added", System.currentTimeMillis()) }
            db.insert("favorites", null, cv); true
        }
    }
    fun favorites(): List<FavoriteEntry> =
        db.rawQuery("SELECT uri,title,video FROM favorites ORDER BY added DESC", null).use {
            buildList { while (it.moveToNext()) add(FavoriteEntry(it.getString(0), it.getString(1), it.getInt(2) == 1)) }
        }

    // ---- Streaming URLs ----
    fun addUrl(url: String) {
        val fav = db.rawQuery("SELECT fav FROM urls WHERE url=?", arrayOf(url)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val cv = ContentValues().apply { put("url", url); put("fav", fav); put("updated", System.currentTimeMillis()) }
        db.insertWithOnConflict("urls", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun urls(): List<UrlEntry> =
        db.rawQuery("SELECT url,fav,updated FROM urls ORDER BY updated DESC LIMIT 100", null).use {
            buildList { while (it.moveToNext()) add(UrlEntry(it.getString(0), it.getInt(1) == 1, it.getLong(2))) }
        }
    fun setUrlFavorite(url: String, fav: Boolean) {
        db.execSQL("UPDATE urls SET fav=? WHERE url=?", arrayOf(if (fav) 1 else 0, url))
    }
    fun removeUrl(url: String) { db.delete("urls", "url=?", arrayOf(url)) }
    fun clearUrls() { db.delete("urls", "fav=0", null) }

    // ---- Playlists ----
    fun createPlaylist(name: String): Long = db.insert("playlists", null, ContentValues().apply { put("name", name) })
    fun renamePlaylist(id: Long, name: String) { db.update("playlists", ContentValues().apply { put("name", name) }, "id=?", arrayOf(id.toString())) }
    fun deletePlaylist(id: Long) { db.delete("playlists", "id=?", arrayOf(id.toString())); db.delete("playlist_items", "pid=?", arrayOf(id.toString())) }
    fun playlists(): List<Playlist> =
        db.rawQuery("SELECT p.id, p.name, (SELECT COUNT(*) FROM playlist_items i WHERE i.pid=p.id) FROM playlists p ORDER BY p.name COLLATE NOCASE", null).use {
            buildList { while (it.moveToNext()) add(Playlist(it.getLong(0), it.getString(1), it.getInt(2))) }
        }
    fun addToPlaylist(pid: Long, uri: String, title: String, isVideo: Boolean) {
        val next = db.rawQuery("SELECT COALESCE(MAX(pos),-1)+1 FROM playlist_items WHERE pid=?", arrayOf(pid.toString())).use { it.moveToFirst(); it.getInt(0) }
        db.insert("playlist_items", null, ContentValues().apply { put("pid", pid); put("pos", next); put("uri", uri); put("title", title); put("video", if (isVideo) 1 else 0) })
    }
    fun playlistItems(pid: Long): List<PlaylistItem> =
        db.rawQuery("SELECT uri,title,video FROM playlist_items WHERE pid=? ORDER BY pos", arrayOf(pid.toString())).use {
            buildList { while (it.moveToNext()) add(PlaylistItem(it.getString(0), it.getString(1), it.getInt(2) == 1)) }
        }
    fun removeFromPlaylist(pid: Long, index: Int) {
        val items = playlistItems(pid).toMutableList().also { it.removeAt(index) }
        rewritePlaylist(pid, items)
    }
    fun moveInPlaylist(pid: Long, from: Int, to: Int) {
        val items = playlistItems(pid).toMutableList()
        if (to !in items.indices) return
        items.add(to, items.removeAt(from))
        rewritePlaylist(pid, items)
    }
    private fun rewritePlaylist(pid: Long, items: List<PlaylistItem>) {
        db.beginTransaction()
        try {
            db.delete("playlist_items", "pid=?", arrayOf(pid.toString()))
            items.forEachIndexed { i, it ->
                db.insert("playlist_items", null, ContentValues().apply { put("pid", pid); put("pos", i); put("uri", it.uri); put("title", it.title); put("video", if (it.isVideo) 1 else 0) })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
