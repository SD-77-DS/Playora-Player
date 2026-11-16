package com.playora.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

/** Build-time facts about the team. Fill these in before publishing. */
object AppInfo {
    const val DEVELOPER = "Playora Team"
    const val COPYRIGHT = "© 2026 Playora. All rights reserved."
    /** Contact e-mail; leave blank to hide the "Contact developer" row. */
    const val CONTACT_EMAIL = ""
}

@Composable
fun Onboarding(granted: Boolean, onFinishIntro: () -> Unit, onRequest: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Rounded.PlayArrow, "Play everything", "MP4, MKV, AVI, MOV, WebM and more — with hardware decoding wherever your device supports it."),
        Triple(Icons.Rounded.MusicNote, "Music, anywhere", "A dedicated music player with background playback, lock-screen controls, an equalizer and headphone-only mode."),
        Triple(Icons.Rounded.VideoLibrary, "Gestures & Picture-in-Picture", "Swipe for volume and brightness, double-tap to skip, pinch to zoom, and keep watching in a floating window."),
    )
    var page by remember { mutableIntStateOf(if (com.playora.app.data.Settings.onboarded) pages.size else 0) }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (page < pages.size) {
            val (icon, title, text) = pages[page]
            LogoTile(icon, 120.dp)
            Spacer(Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(text, Modifier.padding(top = 12.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(32.dp))
            Button({ page++ }, Modifier.fillMaxWidth()) { Text(if (page == pages.size - 1) "Continue" else "Next") }
            if (page < pages.size - 1) androidx.compose.material3.TextButton({ page = pages.size }) { Text("Skip") }
        } else {
            LogoTile(Icons.Rounded.Folder, 96.dp)
            Spacer(Modifier.size(24.dp))
            Text("Allow access to your media", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text("Playora needs permission to find videos and music on your device so it can list and play them. Your files stay on your device — nothing is uploaded.\n\nWe'll also ask to show playback notifications so you can control music from the lock screen.",
                Modifier.padding(top = 12.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(24.dp))
            Button({ onFinishIntro(); onRequest() }, Modifier.fillMaxWidth()) { Text(if (granted) "Continue" else "Grant access") }
        }
    }
}

@Composable
fun LogoTile(icon: androidx.compose.ui.graphics.vector.ImageVector, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size / 2))
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, nav: Nav) {
    val ctx = LocalContext.current
    val pi = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }
    ScreenScaffold("About Playora", onBack) { pad ->
        Column(Modifier.padding(pad).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            LogoTile(Icons.Rounded.PlayArrow, 96.dp)
            Text("Playora", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Play Everything. Anywhere.", color = MaterialTheme.colorScheme.primary)
            Text("Playora is a universal multimedia player designed to bring your videos and music together in one place. Enjoy seamless local playback, online streaming, background audio, and a personalised multimedia experience.",
                Modifier.padding(vertical = 16.dp), textAlign = TextAlign.Center)
            InfoLine("Version", pi.versionName ?: "1.0")
            InfoLine("Build", pi.longVersionCode.toString())
            InfoLine("Developer", AppInfo.DEVELOPER)
            Text(AppInfo.COPYRIGHT, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ nav.push(Route.Credits) }, Modifier.fillMaxWidth()) { Text("Credits & open-source licences") }
                OutlinedButton({ nav.push(Route.Privacy) }, Modifier.fillMaxWidth()) { Text("Privacy policy") }
                OutlinedButton({ nav.push(Route.Terms) }, Modifier.fillMaxWidth()) { Text("Terms and conditions") }
                if (AppInfo.CONTACT_EMAIL.isNotBlank()) OutlinedButton({
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${AppInfo.CONTACT_EMAIL}")).putExtra(android.content.Intent.EXTRA_SUBJECT, "Playora feedback"))
                }, Modifier.fillMaxWidth()) { Text("Contact developer") }
                OutlinedButton({ rateApp(ctx) }, Modifier.fillMaxWidth()) { Text("Rate Playora") }
                OutlinedButton({ shareApp(ctx) }, Modifier.fillMaxWidth()) { Text("Share Playora") }
                Button({ nav.push(Route.Donate) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Favorite, null); Spacer(Modifier.size(8.dp)); Text("Donate to developers") }
            }
        }
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v)
    }
}

private val CREDITS = listOf(
    Triple("Playback engine", "AndroidX Media3 (ExoPlayer, MediaSession, HLS, DASH)", "Apache License 2.0 — Google / The Android Open Source Project"),
    Triple("User interface", "Jetpack Compose & Material 3, Material Icons", "Apache License 2.0 — Google"),
    Triple("Image loading & thumbnails", "Coil 3 (coil-compose, coil-video)", "Apache License 2.0 — Coil Contributors"),
    Triple("Concurrency", "Kotlin Coroutines", "Apache License 2.0 — JetBrains"),
    Triple("Language", "Kotlin", "Apache License 2.0 — JetBrains"),
)

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    ScreenScaffold("Credits", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Text("Application development & design", Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("${AppInfo.DEVELOPER} — development, UI/UX design and icon design.", Modifier.padding(16.dp))
            Text("Open-source libraries & third-party dependencies", Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            CREDITS.forEach { (area, lib, lic) ->
                ListItem({ Text(lib) }, overlineContent = { Text(area) }, supportingContent = { Text(lic) })
            }
            Text("Special thanks", Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("Thank you to the open-source community and to everyone who tests and shares Playora. Playora is inspired by the usability of classic media players but uses its own name, design and assets.", Modifier.padding(16.dp))
        }
    }
}

@Composable
fun DocScreen(title: String, body: String, onBack: () -> Unit) {
    ScreenScaffold(title, onBack) { pad -> Text(body, Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) }
}

@Composable
fun DonateScreen(onBack: () -> Unit) {
    ScreenScaffold("Donate to Developers", onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Support Playora Development", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Playora is built to provide a simple, powerful, and enjoyable multimedia experience. If you appreciate our work, consider supporting the developers. Your contribution helps us maintain, improve, and introduce new features.",
                Modifier.padding(vertical = 12.dp))
            listOf("Small contribution", "Medium contribution", "Large contribution", "Custom contribution").forEach {
                OutlinedButton({}, Modifier.fillMaxWidth().padding(vertical = 4.dp), enabled = false) { Text(it) }
            }
            Card(Modifier.padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text("Donations aren't available in this build yet. They use Google Play Billing, which becomes available once Playora is published on Google Play with its one-time products configured. Donations are always optional — every Playora feature is free.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

const val PRIVACY_TEXT = """Playora Privacy Policy

Last updated: 2026

Playora is a media player. It is designed to work without collecting any personal information.

What stays on your device
• Your videos, music, playlists, favourites, playback history, resume positions, settings and recently played links are stored only on your device.
• Playora reads your media in place through Android's media library. Files are never copied or uploaded.

Permissions
• Photos/videos and music/audio (or storage on older Android versions): to list and play the media on your device.
• Notifications: to show playback controls on the lock screen and notification shade.
• Internet: only to play links that you enter.

Network
• When you play a link, Playora connects to the server that hosts it, and that server may see your IP address as with any media player. Playora does not send any other data.

Analytics & tracking
• Playora contains no analytics, advertising or tracking.

Your control
• You can clear playback history, recent links and the thumbnail cache at any time in Settings → Storage & history. Uninstalling the app removes all of its data.

Children
• Playora does not knowingly collect information from anyone, including children.

Changes
• If this policy changes, the updated version will be included in a new app release."""

const val TERMS_TEXT = """Playora Terms and Conditions

By using Playora you agree to these terms.

1. Use of the app
Playora is provided to play media that you have the right to access. You are responsible for the content you play and for complying with applicable copyright laws.

2. Streaming
Playora plays direct media links. It does not bypass DRM, authentication, paywalls or access restrictions, and it is not responsible for third-party content or servers.

3. Compatibility
Playback depends on your device's codecs and hardware. Some formats may not play on every device.

4. No warranty
Playora is provided "as is", without warranties of any kind. To the extent permitted by law, the developers are not liable for any loss arising from the use of the app.

5. Donations
Donations are voluntary, do not unlock features, and are subject to the store's payment and refund policies.

6. Changes
These terms may be updated in future releases."""
