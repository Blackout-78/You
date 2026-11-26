package com.you.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The streaming-style pages: a movie's or a show's own page, and the pieces the home pages share.

private val YEAR_AT_END = Regex("\\s*\\((\\d{4})\\)\\s*$")

/** "Name (2009)" -> "Name". The year goes on the line under the title. */
fun plainTitle(title: String): String = title.replace(YEAR_AT_END, "").ifBlank { title }

fun yearOf(title: String): String = YEAR_AT_END.find(title)?.groupValues?.get(1).orEmpty()

private fun dots(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString("  ·  ")

/** White Play button and grey second button, like a streaming service. */
@Composable
fun StreamButtons(playLabel: String, onPlay: () -> Unit, secondLabel: String?, onSecond: () -> Unit) {
    Row {
        Box(
            Modifier.clip(RoundedCornerShape(4.dp)).background(Accent).clickable(onClick = onPlay)
                .padding(horizontal = 24.dp, vertical = 11.dp),
        ) { Text(playLabel, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        if (secondLabel != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(DarkGray).clickable(onClick = onSecond)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) { Text(secondLabel, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** The wide picture with a dark fade on the left and at the bottom, and words on top of it. */
@Composable
fun Backdrop(
    picture: Uri?, coverPath: String?, label: String, height: Int,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(height.dp).background(Panel)) {
        Cover(coverPath, null, label, Modifier.fillMaxSize(), picture, large = true)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xD9000000), 0.6f to Color(0x55000000), 1f to Color(0x00000000))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x80000000), 0.2f to Color(0x00000000), 0.55f to Color(0x00000000), 1f to Black)))
        Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 18.dp)) { content() }
    }
}

/** A row of tall posters that scrolls sideways. */
@Composable
fun PosterRow(title: String, count: Int, poster: @Composable (Int) -> Unit) {
    if (count == 0) return
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            for (i in 0 until count) Box(Modifier.padding(end = 8.dp)) { poster(i) }
        }
    }
}

@Composable
fun PosterTile(vm: AppViewModel, key: String, title: String, art: Uri?, onClick: () -> Unit) {
    Box(Modifier.width(118.dp).aspectRatio(2f / 3f).background(Panel).clickable(onClick = onClick)) {
        Cover(vm.store.covers[key], null, title, Modifier.fillMaxSize(), art)
        CaptionBar(title, null, Modifier.align(Alignment.BottomStart))
    }
}

/** The caption on the bottom of a poster: a dark bar with the title and a small detail line. */
@Composable
fun CaptionBar(title: String, detail: String?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        androidx.compose.material3.Text(
            title, color = White, fontSize = 11.sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (!detail.isNullOrBlank()) androidx.compose.material3.Text(detail, color = Muted, fontSize = 9.sp, maxLines = 1)
    }
}

/** A thin white line showing how far you got. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().background(AccentHi))
    }
}

@Composable
private fun BackOnPicture(vm: AppViewModel) {
    Box(
        Modifier.statusBarsPadding().padding(12.dp).clip(RoundedCornerShape(4.dp))
            .background(Color(0x99000000)).border(1.dp, Line, RoundedCornerShape(4.dp))
            .clickable { vm.back() }.padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text("←  Back", color = White, fontSize = 14.sp) }
}

/** Change cover and Edit description, small and grey under the buttons. */
@Composable
private fun EditLinks(vm: AppViewModel, key: String) {
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { vm.store.importCover(uri) }
            if (saved != null) vm.store.setCover(key, saved)
        }
    }
    Row(Modifier.padding(top = 10.dp)) {
        LinkText("Change cover") {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (vm.store.covers[key] != null) LinkText("Normal cover") { vm.store.setCover(key, null) }
    }
}

// ---- a movie's page -------------------------------------------------------------------

@Composable
fun MovieTitleScreen(vm: AppViewModel, id: Long) {
    val movie = vm.videos.movies.find { it.video.id == id }
    if (movie == null) {
        Column(Modifier.fillMaxSize()) { TopBar("Movie", onBack = vm::back); Message("Movie not found.") }
        return
    }
    val set = remember(vm.videos) { collectionsOf(vm.videos.movies).find { c -> c.movies.any { it.video.id == id } } }
    val vid = "v${movie.video.id}"
    val resume = vm.store.resumeFor(vid)
    val playing = vm.player.mediaId == vid
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box {
                Backdrop(movie.heroArt, null, movie.title, 440) {
                    Column {
                        Text(plainTitle(movie.title), color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                            lineHeight = 34.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(dots(yearOf(movie.title), lengthLabel(movie.video.durationMs), set?.name.orEmpty()),
                            color = Soft, fontSize = 14.sp)
                    }
                }
                BackOnPicture(vm)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (resume > 0) {
                    ProgressLine(resume.toFloat() / movie.video.durationMs.coerceAtLeast(1))
                    Text("${lengthLabel(movie.video.durationMs - resume)} left", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
                StreamButtons(
                    if (playing && vm.player.isPlaying) "‖   Playing" else if (resume > 0) "▶   Resume" else "▶   Play",
                    { vm.playVideos(listOf(movie.video to movie.title), 0, "") },
                    if (resume > 0) "↺   From the start" else null,
                ) {
                    vm.store.forgetResume(vid)
                    vm.playVideos(listOf(movie.video to movie.title), 0, "")
                }
                EditLinks(vm, movie.key)
            }
        }
        item { Spacer(Modifier.height(8.dp)); Description(vm, movie.key) }
        if (set != null) {
            item {
                val others = set.movies.filter { it.video.id != id }
                PosterRow("More in this set", others.size) { i ->
                    val m = others[i]
                    PosterTile(vm, m.key, m.title, m.video.art) { vm.open(Screen.MoviePage(m.video.id)) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---- a show's page: season picker and episodes ---------------------------------------

@Composable
fun ShowTitleScreen(vm: AppViewModel, title: String) {
    val show = vm.videos.shows.find { it.title == title }
    if (show == null) {
        Column(Modifier.fillMaxSize()) { TopBar(title, onBack = vm::back); Message("Show not found.") }
        return
    }
    // Specials go last; open on the season you were watching, else the first real season.
    val seasons = remember(show) { show.seasons.filter { it.number != 0 } + show.seasons.filter { it.number == 0 } }
    val all = remember(show) { seasons.flatMap { s -> s.episodes.map { s to it } } }
    val resumeAt = all.indexOfFirst { (_, e) -> vm.store.resumeFor("v${e.video.id}") > 0 }
    var selected by remember(show) {
        mutableIntStateOf(if (resumeAt >= 0) all[resumeAt].first.number else seasons.first().number)
    }
    val season = seasons.find { it.number == selected } ?: seasons.first()
    val episodeCount = all.size

    fun playFrom(s: Season, index: Int) =
        vm.playVideos(s.episodes.map { it.video to it.title }, index, show.title)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box {
                Backdrop(show.heroArt, vm.store.covers[show.key].takeIf { show.fanart == null }, show.title, 440) {
                    Column {
                        Text(show.title, color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                            lineHeight = 34.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(dots("${seasons.size} season${if (seasons.size == 1) "" else "s"}",
                            "$episodeCount episode${if (episodeCount == 1) "" else "s"}"), color = Soft, fontSize = 14.sp)
                    }
                }
                BackOnPicture(vm)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val next = if (resumeAt >= 0) all[resumeAt] else null
                if (next != null) {
                    Text("${next.first.label}  ·  Episode ${next.second.number}: ${next.second.title}",
                        color = Soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 10.dp))
                }
                StreamButtons(
                    if (next != null) "▶   Resume" else "▶   Play",
                    {
                        if (next != null) playFrom(next.first, next.first.episodes.indexOf(next.second))
                        else playFrom(seasons.first(), 0)
                    },
                    if (next != null) "↺   From the start" else null,
                ) {
                    val first = seasons.first()
                    first.episodes.firstOrNull()?.let { vm.store.forgetResume("v${it.video.id}") }
                    playFrom(first, 0)
                }
                EditLinks(vm, show.key)
            }
        }
        item { Spacer(Modifier.height(8.dp)); Description(vm, show.key) }
        item {
            SectionHeader("Episodes")
            if (seasons.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    for (s in seasons) {
                        val on = s.number == season.number
                        Box(
                            Modifier.padding(end = 8.dp).background(if (on) Accent else Panel)
                                .clickable { selected = s.number }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(s.label, color = if (on) White else Soft, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else null) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(season.episodes, key = { _, e -> e.video.id }) { index, ep ->
            EpisodeRow(vm, ep, show.seasonArt(season)) { playFrom(season, index) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EpisodeRow(vm: AppViewModel, ep: Episode, fallback: Uri?, onClick: () -> Unit) {
    val vid = "v${ep.video.id}"
    val current = vm.player.mediaId == vid
    val resume = vm.store.resumeFor(vid)
    Column(
        Modifier.fillMaxWidth().background(if (current) Panel else Black).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(132.dp).aspectRatio(16f / 9f).background(Panel)) {
                Cover(null, null, ep.title, Modifier.fillMaxSize(), ep.video.art)
                Box(
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color(0x99000000))
                        .border(1.dp, White, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp),
                ) { Text(if (current && vm.player.isPlaying) "‖" else "▶", color = White, fontSize = 12.sp) }
                if (resume > 0) ProgressLine(resume.toFloat() / ep.video.durationMs.coerceAtLeast(1),
                    Modifier.align(Alignment.BottomStart))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${ep.number}. ${ep.title}", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(lengthLabel(ep.video.durationMs), color = Muted, fontSize = 12.sp)
            }
        }
        if (ep.plot.isNotBlank()) {
            Text(ep.plot, color = Soft, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}
