package com.playora.app.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.playora.app.data.Settings

/**
 * Single owner of the ExoPlayer instance. Runs as a foreground media service so audio keeps playing with
 * the screen off, exposes a MediaSession (lock-screen / notification / headset buttons / Bluetooth) and
 * is shared by the video player, audio player and audio-only mode.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON) // use software decoders if bundled
            .setEnableDecoderFallback(true) // fall back to another decoder if the preferred one fails
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(Settings.bufferMs, maxOf(Settings.bufferMs * 3, 50_000), 1_500, 3_000)
            .build()
        player = ExoPlayer.Builder(this, renderers)
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_UNKNOWN).build(),
                /* handleAudioFocus = */ true,
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        applyNoisyHandling()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { EqualizerManager.attach(audioSessionId) }
        })
        EqualizerManager.attach(player.audioSessionId)
        session = MediaSession.Builder(this, player).build()
    }

    /** Pause when headphones are unplugged / Bluetooth disconnects, if the user wants that. */
    fun applyNoisyHandling() { player.setHandleAudioBecomingNoisy(Settings.pauseOnUnplug) }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away stops the service unless something is actively playing.
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        instance = null
        EqualizerManager.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: PlaybackService? = null
    }
}
