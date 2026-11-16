package com.playora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.playora.app.data.Settings
import com.playora.app.playback.Playback
import com.playora.app.theme.PlayoraTheme
import com.playora.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlayoraTheme { AppRoot() } }
    }

    override fun onStop() {
        super.onStop()
        // "Background playback" off: audio pauses when the app leaves the foreground.
        val c = Playback.controller
        if (!Settings.bgPlayback && !isChangingConfigurations && c != null) {
            val item = c.currentMediaItem
            if (item != null && !Playback.isVideo(item)) c.pause()
        }
    }
}
