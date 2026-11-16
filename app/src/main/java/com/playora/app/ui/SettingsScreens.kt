package com.playora.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.playora.app.data.Db
import com.playora.app.data.Settings
import com.playora.app.theme.AccentOptions
import com.playora.app.theme.contentOn
import com.playora.app.util.formatSize

@Composable
private fun Group(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem({ Text(title) }, supportingContent = sub?.let { { Text(it) } }, trailingContent = { Switch(checked, onChange) },
        modifier = Modifier.clickable { onChange(!checked) })
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<String, T>>, current: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem({ Text(title) }, supportingContent = { Text(options.firstOrNull { it.second == current }?.first ?: "") }, modifier = Modifier.clickable { open = true })
    if (open) AlertDialog({ open = false }, title = { Text(title) },
        text = { Column { options.forEach { (label, v) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(v); open = false }, verticalAlignment = Alignment.CenterVertically) { RadioButton(v == current, { onSelect(v); open = false }); Text(label) }
        } } },
        confirmButton = { TextButton({ open = false }) { Text("Close") } })
}

@Composable
private fun NavRow(title: String, sub: String? = null, onClick: () -> Unit) {
    ListItem({ Text(title) }, supportingContent = sub?.let { { Text(it) } }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    ScreenScaffold("Settings", null) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Group("General")
            NavRow("Theme & colours", "Light, dark, accent colour") { nav.push(Route.Theme) }
            ChoiceRow("Open app on", listOf("Home" to 0, "Videos" to 1, "Music" to 2, "Folders" to 3), Settings.startTab) { Settings.startTab = it }
            SwitchRow("Grid layout", "Show libraries as a grid instead of a list", Settings.gridView) { Settings.gridView = it }

            Group("Video")
            ChoiceRow("Default playback speed", listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { "${it}x" to it }, Settings.defaultSpeed) { Settings.defaultSpeed = it }
            ChoiceRow("Orientation", listOf("Auto (follow sensor)" to 0, "Landscape" to 1, "Portrait" to 2), Settings.orientation) { Settings.orientation = it }
            ChoiceRow("Default aspect ratio", listOf("Fit" to 0, "Fill" to 1, "Zoom" to 2), Settings.aspect) { Settings.aspect = it }
            SwitchRow("Gesture controls", "Swipe for volume, brightness and seeking; double-tap to skip; pinch to zoom", Settings.gestures) { Settings.gestures = it }
            ChoiceRow("Double-tap seek interval", listOf(5, 10, 15, 30).map { "$it seconds" to it }, Settings.seekInterval) { Settings.seekInterval = it }
            SwitchRow("Resume playback", "Continue videos where you left off", Settings.resume) { Settings.resume = it }
            SwitchRow("Picture-in-picture", "Keep video playing in a small window when you leave the app", Settings.pip) { Settings.pip = it }

            Group("Audio")
            SwitchRow("Background playback", "Keep music playing when the app is closed or the screen is off", Settings.bgPlayback) { Settings.bgPlayback = it }
            SwitchRow("Pause when headphones disconnect", "Wired and Bluetooth", Settings.pauseOnUnplug) { Settings.pauseOnUnplug = it }
            NavRow("Equalizer", "Presets, bass boost, loudness") { nav.push(Route.Equalizer) }

            Group("Streaming")
            ChoiceRow("Network retries", listOf("Off" to 0, "2 times" to 2, "5 times" to 5), Settings.retryCount) { Settings.retryCount = it }
            ChoiceRow("Buffer size (applies after restarting the app)", listOf("Small (5 s)" to 5000, "Default (15 s)" to 15000, "Large (30 s)" to 30000, "Extra large (60 s)" to 60000), Settings.bufferMs) { Settings.bufferMs = it }

            Group("Storage")
            NavRow("Storage & history", "Thumbnail cache, history, recent links") { nav.push(Route.Storage) }

            Group("Playora")
            NavRow("Donate to developers") { nav.push(Route.Donate) }
            NavRow("About Playora") { nav.push(Route.About) }
            NavRow("Credits") { nav.push(Route.Credits) }
            NavRow("Privacy policy") { nav.push(Route.Privacy) }
            NavRow("Terms and conditions") { nav.push(Route.Terms) }
            NavRow("Rate Playora") { rateApp(ctx) }
            NavRow("Share Playora") { shareApp(ctx) }
            Spacer(Modifier.size(24.dp))
        }
    }
}

fun rateApp(ctx: android.content.Context) {
    val pkg = ctx.packageName
    val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (_: Exception) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun shareApp(ctx: android.content.Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Playora – Play Everything. Anywhere.\nhttps://play.google.com/store/apps/details?id=${ctx.packageName}")
    }
    ctx.startActivity(Intent.createChooser(send, "Share Playora").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(Settings.accent, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    fun applyCustom() { Settings.accent = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) }

    ScreenScaffold("Theme & colours", onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Appearance", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf("System" to 0, "Light" to 1, "Dark" to 2).forEach { (l, m) -> FilterChip(Settings.themeMode == m, { Settings.themeMode = m }, { Text(l) }) }
            }
            Text("Accent colour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                AccentOptions.forEach { (name, c) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        Settings.accent = c.toArgb(); android.graphics.Color.colorToHSV(c.toArgb(), hsv); hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                    }) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(c).border(if (Settings.accent == c.toArgb()) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape), contentAlignment = Alignment.Center) {
                            if (Settings.accent == c.toArgb()) androidx.compose.material3.Icon(Icons.Rounded.Check, name, tint = contentOn(c))
                        }
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("Custom colour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(Settings.accent)))
            Text("Hue"); Slider(hue, { hue = it; applyCustom() }, valueRange = 0f..360f)
            Text("Saturation"); Slider(sat, { sat = it; applyCustom() }, valueRange = 0f..1f)
            Text("Brightness"); Slider(value, { value = it; applyCustom() }, valueRange = 0.2f..1f)
            androidx.compose.material3.Button({ Settings.accent = 0xFF02601F.toInt(); android.graphics.Color.colorToHSV(Settings.accent, hsv); hue = hsv[0]; sat = hsv[1]; value = hsv[2] }) { Text("Reset to Emerald") }
        }
    }
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val cacheSize = remember(tick) { SingletonImageLoader.get(ctx).diskCache?.size ?: 0L }
    var note by remember { mutableStateOf<String?>(null) }
    ScreenScaffold("Storage & history", onBack) { pad ->
        Column(Modifier.padding(pad)) {
            NavRow("Clear thumbnail cache", "Using ${formatSize(cacheSize)}. Thumbnails are regenerated on demand.") {
                ThumbCache.clear(); SingletonImageLoader.get(ctx).let { it.memoryCache?.clear(); it.diskCache?.clear() }; tick++; note = "Thumbnail cache cleared"
            }
            NavRow("Clear playback history", "Also resets resume positions") { Db.clearHistory(); Rev.bump(); note = "Playback history cleared" }
            NavRow("Clear recent links", "Favourite links are kept") { Db.clearUrls(); Rev.bump(); note = "Recent links cleared" }
            note?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Playora reads media in place through Android's media library. Your files are never copied or uploaded.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
