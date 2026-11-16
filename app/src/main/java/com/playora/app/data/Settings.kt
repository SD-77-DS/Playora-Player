package com.playora.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.playora.app.playback.PlaybackService
import kotlin.reflect.KProperty

/** A SharedPreferences-backed value that is also Compose-observable. */
class Pref<T : Any>(private val key: String, private val def: T, private val onSet: ((T) -> Unit)? = null) {
    private var state: MutableState<T>? = null

    @Suppress("UNCHECKED_CAST")
    private fun read(): T = when (def) {
        is Boolean -> Settings.sp.getBoolean(key, def) as T
        is Int -> Settings.sp.getInt(key, def) as T
        is Float -> Settings.sp.getFloat(key, def) as T
        is Long -> Settings.sp.getLong(key, def) as T
        else -> Settings.sp.getString(key, def as String) as T
    }

    private fun st(): MutableState<T> = state ?: mutableStateOf(read()).also { state = it }

    operator fun getValue(r: Any?, p: KProperty<*>): T = st().value

    operator fun setValue(r: Any?, p: KProperty<*>, v: T) {
        st().value = v
        Settings.sp.edit().apply {
            when (v) {
                is Boolean -> putBoolean(key, v)
                is Int -> putInt(key, v)
                is Float -> putFloat(key, v)
                is Long -> putLong(key, v)
                else -> putString(key, v as String)
            }
        }.apply()
        onSet?.invoke(v)
    }
}

/** All user-facing preferences. Every field here is read somewhere in the app. */
object Settings {
    lateinit var sp: SharedPreferences
        private set

    fun init(c: Context) {
        sp = c.applicationContext.getSharedPreferences("playora_settings", Context.MODE_PRIVATE)
    }

    // General
    var onboarded by Pref("onboarded", false)
    var themeMode by Pref("theme_mode", 0)            // 0 system, 1 light, 2 dark
    var accent by Pref("accent", 0xFF02601F.toInt())
    var startTab by Pref("start_tab", 0)
    var gridView by Pref("grid_view", false)
    var sortKey by Pref("sort_key", 1)                // 0 name, 1 date, 2 size, 3 duration
    var sortAsc by Pref("sort_asc", false)

    // Video
    var defaultSpeed by Pref("default_speed", 1.0f)
    var orientation by Pref("orientation", 0)         // 0 auto, 1 landscape, 2 portrait
    var gestures by Pref("gestures", true)
    var seekInterval by Pref("seek_interval", 10)     // seconds
    var resume by Pref("resume", true)
    var pip by Pref("pip", true)
    var aspect by Pref("aspect", 0)                   // 0 fit, 1 fill, 2 zoom
    var subSize by Pref("sub_size", 6)                // percent of video height
    var subColor by Pref("sub_color", 0xFFFFFFFF.toInt())
    var subBg by Pref("sub_bg", true)
    var subBottom by Pref("sub_bottom", 8)            // bottom padding percent

    // Audio
    var bgPlayback by Pref("bg_playback", true)
    var pauseOnUnplug by Pref("pause_on_unplug", true) { PlaybackService.instance?.applyNoisyHandling() }
    var eqEnabled by Pref("eq_enabled", false)
    var eqLevels by Pref("eq_levels", "")             // csv of millibels per band
    var eqPreset by Pref("eq_preset", "Normal")
    var bass by Pref("bass", 0)                       // 0..1000
    var loudness by Pref("loudness", 0)               // millibels 0..1500
    var customPresets by Pref("custom_presets", "")   // name=csv;name=csv

    // Streaming
    var retryCount by Pref("retry_count", 2)
    var bufferMs by Pref("buffer_ms", 15000)          // min buffer duration
}
