package com.you.media

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/** What the cover of the current song or video is made from. */
class NowCover(val coverPath: String?, val albumId: Long?, val videoUri: Uri?, val hasArt: Boolean)

/** Finds the key of the album a song belongs to. */
fun albumKeyOf(vm: AppViewModel, trackId: Long): String? =
    vm.library.artists.firstNotNullOfOrNull { a -> a.albums.find { al -> al.tracks.any { it.id == trackId } }?.key }

@Composable
fun rememberNowCover(vm: AppViewModel): NowCover {
    val p = vm.player
    val id = p.mediaId?.toLongOrNull()
    val videoId = if (p.isVideo) p.mediaId?.removePrefix("v")?.toLongOrNull() else null
    val track = id?.let { vm.library.tracksById[it] }
    val albumKey = remember(id, vm.library) { id?.let { albumKeyOf(vm, it) } }
    val movie = videoId?.let { vid -> vm.videos.movies.find { it.video.id == vid } }
    val videoUri = videoId?.let { vid ->
        movie?.video?.uri ?: vm.videos.shows.firstNotNullOfOrNull { sh ->
            sh.seasons.firstNotNullOfOrNull { se -> se.episodes.find { it.video.id == vid }?.video?.uri }
        }
    }
    val path = (albumKey ?: movie?.key)?.let { vm.store.covers[it] }
    return NowCover(path, track?.albumId, videoUri, track != null || videoUri != null)
}

/** The full-screen player: big cover, progress, controls, and what is coming up next. */
@Composable
fun NowPlayingScreen(vm: AppViewModel) {
    val p = vm.player
    val id = p.mediaId?.toLongOrNull()
    val videoId = if (p.isVideo) p.mediaId?.removePrefix("v")?.toLongOrNull() else null
    val cover = rememberNowCover(vm)
    val hasTrack = p.mediaId != null

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

    val queue = remember(p.queueVersion, p.mediaId) { p.queue() }

    Column(Modifier.fillMaxSize().background(Black)) {
        TopBar("Now playing", onBack = { vm.showNowPlaying = false })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)) {
                    Cover(cover.coverPath, cover.albumId, p.title, Modifier.fillMaxWidth().aspectRatio(1f), cover.videoUri, large = true)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title.ifEmpty { "Nothing playing" }, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (p.artist.isNotEmpty()) Text(p.artist, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HeartButton(
                        active = (id != null && vm.store.isFavorite(id)) || (videoId != null && vm.store.isVideoFavorite(videoId)),
                        enabled = id != null || videoId != null,
                        onClick = { id?.let { vm.store.toggleFavorite(it) }; videoId?.let { vm.store.toggleVideoFavorite(it) } },
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(shown.toLong()), color = Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                    Slider(
                        value = shown.coerceAtMost(max),
                        onValueChange = { dragging = true; dragValue = it },
                        onValueChangeFinished = { p.seekTo(dragValue.toLong()); position = dragValue.toLong(); dragging = false },
                        valueRange = 0f..max, enabled = hasTrack, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = White, activeTrackColor = White, inactiveTrackColor = Line,
                            disabledThumbColor = Line, disabledActiveTrackColor = Line, disabledInactiveTrackColor = Panel,
                        ),
                    )
                    Text(formatTime(p.durationMs), color = Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ShuffleButton(active = p.shuffle, onClick = p::toggleShuffle)
                    PreviousButton(enabled = hasTrack, onClick = p::previous)
                    PlayPauseButton(playing = p.isPlaying, enabled = hasTrack, onClick = p::toggle, size = 64.dp)
                    NextButton(enabled = hasTrack, onClick = p::next)
                    RepeatButton(mode = when (p.repeat) { Player.REPEAT_MODE_ONE -> 2; Player.REPEAT_MODE_ALL -> 1; else -> 0 }, onClick = p::cycleRepeat)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!p.isVideo) LinkText("Lyrics") { vm.showNowPlaying = false; vm.showLyrics = true }
                    LinkText(
                        when (vm.sleepMinutes) { 0 -> "Sleep timer"; -1 -> "Sleep: end of song"; else -> "Sleep ${vm.sleepMinutes} min" }
                    ) { vm.cycleSleep() }
                }
            }
            item { HorizontalDivider(color = Line, modifier = Modifier.padding(top = 4.dp)) }
            val lyr = vm.lyrics
            if (!p.isVideo && lyr != null) {
                item { SectionHeader("Lyrics") }
                items(lyr.lines.size) { i ->
                    Text(lyr.lines[i].text.ifEmpty { " " }, color = Soft, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
                }
                item { HorizontalDivider(color = Line, modifier = Modifier.padding(top = 12.dp)) }
            }
            val curAlbum = if (!p.isVideo && id != null) vm.library.artists.firstNotNullOfOrNull { a -> a.albums.find { al -> al.tracks.any { it.id == id } } } else null
            if (curAlbum != null) {
                item { SectionHeader("More from " + curAlbum.title) }
                itemsIndexed(curAlbum.tracks) { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { p.playQueue(curAlbum.tracks, i) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", color = Muted, fontSize = 13.sp, modifier = Modifier.width(32.dp))
                        Text(t.title, color = if (t.id == id) White else Soft, fontWeight = if (t.id == id) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                val others = vm.library.artists.find { it.name == curAlbum.artist }?.albums?.filter { it.key != curAlbum.key }.orEmpty()
                if (others.isNotEmpty()) {
                    item { SectionHeader("More by " + curAlbum.artist) }
                    item {
                        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(others.size) { i ->
                                val al = others[i]
                                Column(Modifier.width(110.dp).clickable { vm.showNowPlaying = false; vm.open(Screen.AlbumPage(al.artist, al.title)) }) {
                                    Cover(vm.store.covers[al.key], al.albumId, al.title, Modifier.size(110.dp))
                                    Text(al.title, color = White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (p.shuffle) {
                item { Text("Shuffle is on, so the order is a surprise.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
            } else if (queue.size > 1) {
                item { SectionHeader("Up next") }
                itemsIndexed(queue) { i, q ->
                    if (i > p.queueIndex) {
                        Row(
                            Modifier.fillMaxWidth().clickable { p.jumpTo(q.index) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", color = Muted, fontSize = 13.sp, modifier = Modifier.width(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(q.title, color = White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (q.artist.isNotEmpty()) Text(q.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            LinkText("Remove") { p.removeFromQueue(q.index) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
