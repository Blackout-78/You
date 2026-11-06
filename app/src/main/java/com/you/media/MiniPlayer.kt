package com.you.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The player: title, progress bar and all the controls. Appears once you start a song. */
@Composable
fun MiniPlayer(vm: AppViewModel) {
    val p = vm.player
    val id = p.mediaId?.toLongOrNull()
    val videoId = if (p.isVideo) p.mediaId?.removePrefix("v")?.toLongOrNull() else null

    // The cover of whatever is playing: the album picture for songs, a frame for videos.
    val track = id?.let { vm.library.tracksById[it] }
    val albumKey = remember(id, vm.library) {
        id?.let { tid -> vm.library.artists.firstNotNullOfOrNull { a -> a.albums.find { al -> al.tracks.any { it.id == tid } }?.key } }
    }
    val movie = videoId?.let { vid -> vm.videos.movies.find { it.video.id == vid } }
    val videoUri = videoId?.let { vid ->
        movie?.video?.uri ?: vm.videos.shows.firstNotNullOfOrNull { sh ->
            sh.seasons.firstNotNullOfOrNull { se -> se.episodes.find { it.video.id == vid }?.video?.uri }
        }
    }
    val coverPath = (albumKey ?: movie?.key)?.let { vm.store.covers[it] }

    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(p.mediaId, p.isPlaying) {
        while (true) {
            if (!dragging) position = p.position()
            delay(300)
        }
    }

    val max = p.durationMs.coerceAtLeast(1L).toFloat()
    val shown = if (dragging) dragValue else position.coerceIn(0L, p.durationMs.coerceAtLeast(1L)).toFloat()
    val hasTrack = p.mediaId != null

    Column(Modifier.fillMaxWidth().background(Black)) {
        HorizontalDivider(color = Line)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (track != null || videoUri != null) {
                Cover(coverPath, track?.albumId, p.title, Modifier.size(56.dp), videoUri)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    p.title.ifEmpty { "Nothing playing" }, color = White, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (p.artist.isNotEmpty()) {
                    Text(p.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (p.isVideo && !vm.showVideo) LinkText("Watch") { vm.showVideo = true }
            LyricsButton(active = vm.showLyrics, enabled = hasTrack && !p.isVideo, onClick = { vm.showLyrics = !vm.showLyrics })
            Spacer(Modifier.width(8.dp))
            HeartButton(
                active = (id != null && vm.store.isFavorite(id)) || (videoId != null && vm.store.isVideoFavorite(videoId)),
                enabled = id != null || videoId != null,
                onClick = {
                    id?.let { vm.store.toggleFavorite(it) }
                    videoId?.let { vm.store.toggleVideoFavorite(it) }
                },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatTime(shown.toLong()), color = Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
            Slider(
                value = shown.coerceAtMost(max),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { p.seekTo(dragValue.toLong()); position = dragValue.toLong(); dragging = false },
                valueRange = 0f..max,
                enabled = hasTrack,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = White,
                    activeTrackColor = White,
                    inactiveTrackColor = Line,
                    disabledThumbColor = Line,
                    disabledActiveTrackColor = Line,
                    disabledInactiveTrackColor = Panel,
                ),
            )
            Text(
                formatTime(p.durationMs), color = Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShuffleButton(active = p.shuffle, onClick = p::toggleShuffle)
            PreviousButton(enabled = hasTrack, onClick = p::previous)
            SkipButton(forward = false, enabled = hasTrack, onClick = { p.skip(-10_000) })
            PlayPauseButton(playing = p.isPlaying, enabled = hasTrack, onClick = p::toggle)
            StopButton(enabled = hasTrack, onClick = vm::stopPlayback)
            SkipButton(forward = true, enabled = hasTrack, onClick = { p.skip(10_000) })
            NextButton(enabled = hasTrack, onClick = p::next)
            RepeatButton(mode = p.repeat.let { if (it == androidx.media3.common.Player.REPEAT_MODE_ONE) 2 else if (it == androidx.media3.common.Player.REPEAT_MODE_ALL) 1 else 0 }, onClick = p::cycleRepeat)
        }
    }
}
