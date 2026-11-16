package com.playora.app.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.playora.app.data.Settings

/** Applies the user's equalizer to the real audio session (android.media.audiofx). */
object EqualizerManager {
    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessFx: LoudnessEnhancer? = null

    var bandCount by mutableIntStateOf(0); private set
    var minLevel by mutableIntStateOf(-1500); private set
    var maxLevel by mutableIntStateOf(1500); private set
    var centerFreqsHz by mutableStateOf<List<Int>>(emptyList()); private set
    var bassSupported by mutableStateOf(false); private set

    /** Preset curves as 5 control points (low→high) in the range -1..1, interpolated over actual bands. */
    val presets: Map<String, FloatArray> = linkedMapOf(
        "Normal" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Classical" to floatArrayOf(0.4f, 0.2f, -0.1f, 0.2f, 0.4f),
        "Dance" to floatArrayOf(0.7f, 0.3f, 0f, 0.3f, 0.5f),
        "Electronic" to floatArrayOf(0.5f, 0.2f, -0.2f, 0.3f, 0.6f),
        "Hip-Hop" to floatArrayOf(0.6f, 0.4f, -0.1f, 0.1f, 0.3f),
        "Jazz" to floatArrayOf(0.3f, 0.2f, -0.2f, 0.2f, 0.5f),
        "Pop" to floatArrayOf(-0.2f, 0.2f, 0.5f, 0.2f, -0.2f),
        "Rock" to floatArrayOf(0.5f, 0.2f, -0.3f, 0.3f, 0.6f),
        "Acoustic" to floatArrayOf(0.4f, 0.2f, 0.2f, 0.3f, 0.3f),
        "Bass Boost" to floatArrayOf(0.9f, 0.6f, 0f, 0f, 0f),
        "Vocal" to floatArrayOf(-0.3f, -0.1f, 0.5f, 0.4f, -0.1f),
    )

    /** Interpolate a 5-point curve onto [bands] bands, scaled to [maxMb] millibels. */
    fun curveToLevels(curve: FloatArray, bands: Int, maxMb: Int): List<Int> =
        List(bands) { i ->
            val pos = if (bands == 1) 0f else i * (curve.size - 1f) / (bands - 1)
            val lo = pos.toInt().coerceIn(0, curve.size - 1)
            val hi = (lo + 1).coerceAtMost(curve.size - 1)
            val v = curve[lo] + (curve[hi] - curve[lo]) * (pos - lo)
            (v * maxMb).toInt()
        }

    fun levels(): List<Int> {
        val saved = Settings.eqLevels.split(',').mapNotNull { it.toIntOrNull() }
        return if (saved.size == bandCount) saved else List(bandCount) { 0 }
    }

    fun customPresets(): Map<String, List<Int>> =
        Settings.customPresets.split(';').filter { it.contains('=') }.associate {
            it.substringBefore('=') to it.substringAfter('=').split(',').mapNotNull(String::toIntOrNull)
        }

    fun saveCustomPreset(name: String) {
        val map = customPresets().toMutableMap(); map[name] = levels()
        Settings.customPresets = map.entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" }
        Settings.eqPreset = name
    }

    fun setLevels(l: List<Int>) { Settings.eqLevels = l.joinToString(","); apply() }

    fun applyPreset(name: String) {
        val custom = customPresets()[name]
        val l = custom?.takeIf { it.size == bandCount }
            ?: presets[name]?.let { curveToLevels(it, bandCount, maxLevel) } ?: return
        Settings.eqPreset = name
        setLevels(l)
    }

    fun attach(sessionId: Int) {
        release()
        if (sessionId == 0 || sessionId == -1) return
        try {
            eq = Equalizer(0, sessionId).also { e ->
                bandCount = e.numberOfBands.toInt()
                minLevel = e.bandLevelRange[0].toInt(); maxLevel = e.bandLevelRange[1].toInt()
                centerFreqsHz = List(bandCount) { e.getCenterFreq(it.toShort()) / 1000 }
            }
        } catch (_: Exception) { eq = null; bandCount = 0 }
        try { bassBoost = BassBoost(0, sessionId); bassSupported = bassBoost?.strengthSupported == true } catch (_: Exception) { bassBoost = null; bassSupported = false }
        try { loudnessFx = LoudnessEnhancer(sessionId) } catch (_: Exception) { loudnessFx = null }
        apply()
    }

    /** Push the stored settings to the audio effects. */
    fun apply() {
        val on = Settings.eqEnabled
        try {
            eq?.let { e ->
                e.enabled = on
                levels().forEachIndexed { i, mb -> e.setBandLevel(i.toShort(), mb.coerceIn(minLevel, maxLevel).toShort()) }
            }
            bassBoost?.let { it.enabled = on && Settings.bass > 0; if (bassSupported) it.setStrength(Settings.bass.toShort()) }
            loudnessFx?.let { it.enabled = on && Settings.loudness > 0; it.setTargetGain(Settings.loudness) }
        } catch (_: Exception) { /* effect released mid-update */ }
    }

    fun release() {
        runCatching { eq?.release() }; runCatching { bassBoost?.release() }; runCatching { loudnessFx?.release() }
        eq = null; bassBoost = null; loudnessFx = null
    }
}
