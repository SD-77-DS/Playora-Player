package com.playora.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.playora.app.data.Settings
import com.playora.app.playback.EqualizerManager
import kotlin.math.roundToInt

/** Controls the real android.media.audiofx effects attached to the playback session. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val em = EqualizerManager
    var saving by remember { mutableStateOf(false) }
    ScreenScaffold("Equalizer", onBack) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable equalizer", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(Settings.eqEnabled, { Settings.eqEnabled = it; em.apply() })
            }
            if (em.bandCount == 0) {
                Text("The equalizer attaches when playback starts. Play a song or video, then come back here. (Some devices don't expose an equalizer.)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                return@Column
            }
            Text("Presets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (em.presets.keys + em.customPresets().keys).forEach { name ->
                    FilterChip(Settings.eqPreset == name, { em.applyPreset(name); Settings.eqEnabled = true; em.apply() }, { Text(name) })
                }
            }
            Text("Bands", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            val levels = em.levels()
            levels.forEachIndexed { i, mb ->
                val hz = em.centerFreqsHz.getOrNull(i) ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", Modifier.padding(end = 8.dp).fillMaxWidth(0.2f), style = MaterialTheme.typography.labelMedium)
                    Slider(mb.toFloat(), { v ->
                        val l = levels.toMutableList().also { it[i] = v.roundToInt() }
                        Settings.eqPreset = "Custom"; Settings.eqEnabled = true; em.setLevels(l)
                    }, valueRange = em.minLevel.toFloat()..em.maxLevel.toFloat(), modifier = Modifier.weight(1f))
                    Text("${mb / 100} dB", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (em.bassSupported) {
                Text("Bass boost", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Slider(Settings.bass.toFloat(), { Settings.bass = it.roundToInt(); em.apply() }, valueRange = 0f..1000f)
            }
            Text("Loudness enhancement", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            Slider(Settings.loudness.toFloat(), { Settings.loudness = it.roundToInt(); em.apply() }, valueRange = 0f..1500f)
            Button({ saving = true }, Modifier.padding(vertical = 12.dp)) { Text("Save as custom preset") }
        }
    }
    if (saving) NameDialog("Save preset", "", { saving = false }) { em.saveCustomPreset(it); saving = false }
}
