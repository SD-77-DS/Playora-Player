package com.playora.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.playora.app.data.Db
import com.playora.app.data.Settings
import com.playora.app.playback.Playback
import okio.Path.Companion.toOkioPath

class PlayoraApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Db.init(this)
        Playback.connect(this)
    }

    /** Coil loader with a bounded disk cache for generated thumbnails. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbs").toOkioPath()).maxSizeBytes(150L * 1024 * 1024).build() }
            .build()
}
