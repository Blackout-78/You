package com.you.media

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Only You itself and the phone's trusted system (lock screen, notification, Bluetooth) may control playback. */
private class OwnControllersOnly : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
        if (controller.isTrusted || controller.packageName == "com.you.media") MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        else MediaSession.ConnectionResult.reject()
}

/** Keeps music playing when the screen is off or the app is closed, with lock-screen controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player)
            .setCallback(OwnControllersOnly())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
