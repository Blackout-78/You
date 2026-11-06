package com.you.media

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** The app's remote control for the playback service. The screens just read these values. */
class PlayerHolder(private val context: Context) {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    var controllerState by mutableStateOf<Player?>(null)
    var isPlaying by mutableStateOf(false)
    var mediaId by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var durationMs by mutableStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(Player.REPEAT_MODE_OFF)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }
    }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                controllerState = c
                c.addListener(listener)
                sync(c)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerState = null
        future = null
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        val item = p.currentMediaItem
        mediaId = item?.mediaId
        title = item?.mediaMetadata?.title?.toString() ?: ""
        artist = item?.mediaMetadata?.artist?.toString() ?: ""
        durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration.coerceAtLeast(0L)
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    )
                    .build()
            )
            .build()

    val isVideo: Boolean get() = mediaId?.startsWith("v") == true

    fun playQueue(tracks: List<Track>, index: Int) {
        playItems(tracks.map { it.toMediaItem() }, index, 0L)
    }

    fun playVideos(videos: List<Video>, index: Int, subtitle: String, titles: List<String>, startMs: Long,
        subtitles: List<Uri?> = emptyList()) {
        val items = videos.mapIndexed { i, v ->
            MediaItem.Builder()
                .setMediaId("v${v.id}")
                .setUri(v.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(titles.getOrElse(i) { v.displayName }).setArtist(subtitle).build()
                )
                .apply {
                    subtitles.getOrNull(i)?.let { sub ->
                        val vtt = sub.toString().lowercase().endsWith(".vtt")
                        setSubtitleConfigurations(
                            listOf(
                                MediaItem.SubtitleConfiguration.Builder(sub)
                                    .setMimeType(if (vtt) androidx.media3.common.MimeTypes.TEXT_VTT else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP)
                                    .setLanguage("und")
                                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                    .build()
                            )
                        )
                    }
                }
                .build()
        }
        playItems(items, index, startMs)
    }

    /** Turns subtitles on or off for whatever is playing. */
    fun setSubtitlesOn(on: Boolean) {
        val c = controller ?: return
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on).build()
    }

    /** Stops, forgets the queue and lets the notification and lock screen card go away. */
    fun stopAndClear() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
    }

    private fun playItems(items: List<MediaItem>, index: Int, startMs: Long) {
        val c = controller ?: return
        c.setMediaItems(items, index, startMs)
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> {
                c.seekToDefaultPosition()
                c.play()
            }
            else -> c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun skip(deltaMs: Long) {
        val c = controller ?: return
        var target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        if (c.duration != C.TIME_UNSET) target = target.coerceAtMost((c.duration - 500).coerceAtLeast(0L))
        c.seekTo(target)
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
}
