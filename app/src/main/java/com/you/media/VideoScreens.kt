package com.you.media

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

private const val POSTER = 2f / 3f

@Composable
private fun NeedVideoAccess(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("You needs to read the videos on this phone.", color = White, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onAsk,
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        ) { Text("Allow access to videos", color = White) }
    }
}

/** Videos you started and did not finish, newest first. Tap to carry on where you stopped. */
@Composable
private fun ContinueWatching(vm: AppViewModel, movies: Boolean) {
    val entries = ArrayList<Triple<Video, String, String>>()
    val byId = HashMap<Long, Pair<Video, String>>()
    val wide = HashMap<Long, Uri?>()                 // the wide picture for each tile
    if (movies) {
        vm.videos.movies.forEach { byId[it.video.id] = it.video to plainTitle(it.title); wide[it.video.id] = it.heroArt }
    } else {
        vm.videos.shows.forEach { show ->
            show.seasons.forEach { season ->
                season.episodes.forEach {
                    byId[it.video.id] = it.video to "${show.title} \u00b7 ${it.title}"
                    wide[it.video.id] = it.video.poster ?: show.fanart ?: it.video.uri
                }
            }
        }
    }
    vm.store.unfinishedVideoIds().forEach { id ->
        byId[id]?.let { (video, title) -> entries.add(Triple(video, title, "Resume at ${formatTime(vm.store.resumeFor("v$id"))}")) }
    }
    if (entries.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Continue watching")
        TileRow {
            entries.forEachIndexed { i, (video, title, sub) ->
                Box(Modifier.padding(end = 12.dp)) {
                    LandscapeTile(title, sub, wide[video.id] ?: video.uri, null,
                        progress = vm.store.resumeFor("v${video.id}").toFloat() / video.durationMs.coerceAtLeast(1)) {
                        vm.playVideos(entries.map { it.first to it.second }, i, "")
                    }
                }
            }
        }
    }
}

@Composable
fun MoviesBrowseScreen(vm: AppViewModel, videoAccess: Boolean, onAskVideo: () -> Unit) {
    var mode by remember { mutableStateOf(SortMode.Added) }
    var seed by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Movies", null) { LinkText("\u2665") { vm.open(Screen.Favorites) } }
        GraySearchBar(vm)
        val movies = vm.videos.movies
        when {
            !videoAccess -> NeedVideoAccess(onAskVideo)
            movies.isEmpty() -> Message(
                if (vm.loading) "Reading your videos\u2026"
                else "No movies found.\nVideos five minutes or longer, without S01E02 in the name, show up here."
            )
            else -> {
                val sorted = remember(movies, mode, seed) { sortMovies(movies, mode, seed) }
                // The big picture: the newest film with a wide background picture, else the newest.
                val featured = remember(movies) {
                    val newest = movies.sortedByDescending { it.video.dateAdded }
                    newest.firstOrNull { it.video.fanart != null } ?: newest.first()
                }
                val collections = remember(movies) { collectionsOf(movies) }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Hero(
                            title = featured.title,
                            meta = listOf(yearOf(featured.title), lengthLabel(featured.video.durationMs))
                                .filter { it.isNotBlank() }.joinToString("  \u00b7  "),
                            kicker = "FEATURED FILM",
                            description = vm.describe(featured.key),
                            videoUri = featured.heroArt,
                            coverPath = vm.store.covers[featured.key],
                            onPlay = { vm.playVideos(listOf(featured.video to featured.title), 0, "") },
                            onInfo = { vm.open(Screen.MoviePage(featured.video.id)) },
                        )
                    }
                    item { ContinueWatching(vm, true) }
                    // One row of posters per collection (a folder holding two or more films).
                    collections.forEach { c ->
                        item(key = "set:" + c.folder) {
                            val films = c.movies.sortedBy { yearOf(it.title) + sortKey(it.title) }
                            PosterRow("${c.name}  \u00b7  ${films.size} films", films.size) { i ->
                                val m = films[i]
                                PosterTile(vm, m.key, m.title, m.video.art) { vm.open(Screen.MoviePage(m.video.id)) }
                            }
                        }
                    }
                    item { SectionHeader("All movies") }
                    item { SortChips(mode) { if (it == SortMode.Shuffle) seed += 1; mode = it } }
                    posterRows(sorted.size) { i ->
                        val movie = sorted[i]
                        GridCell(
                            path = vm.store.covers[movie.key], albumId = null, title = movie.title,
                            subtitle = lengthLabel(movie.video.durationMs), aspect = POSTER, videoUri = movie.video.art,
                        ) { vm.open(Screen.MoviePage(movie.video.id)) }
                    }
                }
            }
        }
    }
}

@Composable
fun MoviesScreen(vm: AppViewModel, videoAccess: Boolean, onAskVideo: () -> Unit) {
    if (vm.isGrid("movies")) MoviesGridScreen(vm, videoAccess, onAskVideo)
    else MoviesBrowseScreen(vm, videoAccess, onAskVideo)
}

@Composable
fun ShowsScreen(vm: AppViewModel, videoAccess: Boolean, onAskVideo: () -> Unit) {
    if (vm.isGrid("shows")) ShowsGridScreen(vm, videoAccess, onAskVideo)
    else ShowsBrowseScreen(vm, videoAccess, onAskVideo)
}

/** The Grid look for Movies: a plain grid of posters. */
@Composable
fun MoviesGridScreen(vm: AppViewModel, videoAccess: Boolean, onAskVideo: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Movies", null) { LinkText("\u2665") { vm.open(Screen.Favorites) } }
        GraySearchBar(vm)
        val movies = vm.videos.movies
        when {
            !videoAccess -> NeedVideoAccess(onAskVideo)
            movies.isEmpty() -> Message(
                if (vm.loading) "Reading your videos\u2026"
                else "No movies found.\nVideos five minutes or longer, without S01E02 in the name, show up here."
            )
            else -> LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                item(span = { GridItemSpan(maxLineSpan) }) { ContinueWatching(vm, true) }
                items(movies, key = { it.video.id }) { movie ->
                    GridCell(
                        path = vm.store.covers[movie.key], albumId = null, title = movie.title,
                        subtitle = formatTime(movie.video.durationMs), aspect = POSTER, videoUri = movie.video.art,
                    ) { vm.open(Screen.MoviePage(movie.video.id)) }
                }
            }
        }
    }
}

/** The Grid look for TV Shows: a plain grid of posters. */
@Composable
fun ShowsGridScreen(vm: AppViewModel, videoAccess: Boolean, onAskVideo: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("TV Shows", null) { LinkText("\u2665") { vm.open(Screen.Favorites) } }
        GraySearchBar(vm)
        val shows = vm.videos.shows
        when {
            !videoAccess -> NeedVideoAccess(onAskVideo)
            shows.isEmpty() -> Message(
                if (vm.loading) "Reading your videos\u2026"
                else "No TV shows found.\nFiles with S01E02 or 1x02 in the name show up here."
            )
            else -> LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                item(span = { GridItemSpan(maxLineSpan) }) { ContinueWatching(vm, false) }
                items(shows, key = { it.key }) { show ->
                    val n = show.seasons.size
                    GridCell(
                        path = vm.store.covers[show.key], albumId = null, title = show.title,
                        subtitle = "$n season${if (n == 1) "" else "s"}", aspect = POSTER,
                        videoUri = show.art,
                    ) { vm.open(Screen.ShowPage(show.title)) }
                }
            }
        }
    }
}

@Composable
fun MovieScreen(vm: AppViewModel, id: Long) {
    val movie = vm.videos.movies.find { it.video.id == id }
    Column(Modifier.fillMaxSize()) {
        TopBar(movie?.title ?: "Movie", onBack = vm::back)
        if (movie == null) {
            Message("Movie not found.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column {
                    CoverSection(vm, movie.key, null, movie.title, movie.video.art, POSTER, 0.7f)
                    Description(vm, movie.key)
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Line)
                }
            }
            item {
                val current = vm.player.mediaId == "v${movie.video.id}"
                TrackRow(
                    label = if (current && vm.player.isPlaying) "▶" else "01",
                    title = movie.video.displayName.substringBeforeLast('.'),
                    subtitle = if (vm.store.resumeFor("v${movie.video.id}") > 0)
                        "Resume at ${formatTime(vm.store.resumeFor("v${movie.video.id}"))}" else null,
                    right = formatTime(movie.video.durationMs),
                    current = current,
                ) { vm.playVideos(listOf(movie.video to movie.title), 0, "") }
            }
        }
    }
}

@Composable
fun ShowsBrowseScreen(vm: AppViewModel, videoAccess: Boolean, onAskVideo: () -> Unit) {
    var mode by remember { mutableStateOf(SortMode.Added) }
    var seed by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize()) {
        TopBar("TV Shows", null) { LinkText("\u2665") { vm.open(Screen.Favorites) } }
        GraySearchBar(vm)
        val shows = vm.videos.shows
        when {
            !videoAccess -> NeedVideoAccess(onAskVideo)
            shows.isEmpty() -> Message(
                if (vm.loading) "Reading your videos\u2026"
                else "No TV shows found.\nFiles with S01E02 or 1x02 in the name show up here."
            )
            else -> {
                fun added(show: Show) = show.seasons.maxOf { s -> s.episodes.maxOf { it.video.dateAdded } }
                fun first(show: Show) = show.seasons.first().episodes.first()
                val sorted = remember(shows, mode, seed) {
                    when (mode) {
                        SortMode.Added -> shows.sortedByDescending { added(it) }
                        SortMode.Oldest -> shows.sortedBy { added(it) }
                        SortMode.Title -> shows.sortedBy { sortKey(it.title) }
                        SortMode.Shuffle -> shows.shuffled(java.util.Random(seed.toLong()))
                    }
                }
                val newest = remember(shows) { shows.sortedByDescending { added(it) } }
                val featured = newest.firstOrNull { it.fanart != null } ?: newest.first()
                val episodeCount = featured.seasons.sumOf { it.episodes.size }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Hero(
                            title = featured.title,
                            meta = "${featured.seasons.size} season${if (featured.seasons.size == 1) "" else "s"}  \u00b7  $episodeCount episodes",
                            kicker = "FEATURED SHOW",
                            description = vm.describe(featured.key),
                            videoUri = featured.heroArt,
                            coverPath = vm.store.covers[featured.key],
                            onPlay = {
                                val eps = featured.seasons.flatMap { it.episodes }
                                val resumeAt = eps.indexOfFirst { vm.store.resumeFor("v${it.video.id}") > 0 }.coerceAtLeast(0)
                                vm.playVideos(eps.map { it.video to it.title }, resumeAt, featured.title)
                            },
                            onInfo = { vm.open(Screen.ShowPage(featured.title)) },
                        )
                    }
                    item { ContinueWatching(vm, false) }
                    if (shows.size > 1) item {
                        val recent = newest.take(12)
                        PosterRow("Recently added", recent.size) { i ->
                            val sh = recent[i]
                            PosterTile(vm, sh.key, sh.title, sh.art) { vm.open(Screen.ShowPage(sh.title)) }
                        }
                    }
                    item { SectionHeader("All shows") }
                    item { SortChips(mode) { if (it == SortMode.Shuffle) seed += 1; mode = it } }
                    posterRows(sorted.size) { i ->
                        val show = sorted[i]
                        val n = show.seasons.size
                        GridCell(
                            path = vm.store.covers[show.key], albumId = null, title = show.title,
                            subtitle = "$n season${if (n == 1) "" else "s"}", aspect = POSTER,
                            videoUri = show.art,
                        ) { vm.open(Screen.ShowPage(show.title)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ShowScreen(vm: AppViewModel, title: String) {
    val show = vm.videos.shows.find { it.title == title }
    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack = vm::back)
        if (show == null) {
            Message("Show not found.")
            return@Column
        }
        val firstVideo = show.art
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    CoverSection(vm, show.key, null, show.title, firstVideo, POSTER, 0.7f)
                    Description(vm, show.key)
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Line)
                }
            }
            items(show.seasons, key = { it.number }) { season ->
                val n = season.episodes.size
                GridCell(
                    path = vm.store.covers[show.seasonKey(season.number)], albumId = null, title = season.label,
                    subtitle = "$n episode${if (n == 1) "" else "s"}", aspect = POSTER,
                    videoUri = show.seasonArt(season),
                ) { vm.open(Screen.SeasonPage(show.title, season.number)) }
            }
        }
    }
}

@Composable
fun SeasonScreen(vm: AppViewModel, showTitle: String, seasonNumber: Int) {
    val show = vm.videos.shows.find { it.title == showTitle }
    val season = show?.seasons?.find { it.number == seasonNumber }
    Column(Modifier.fillMaxSize()) {
        TopBar(season?.label ?: "Season", onBack = vm::back)
        if (show == null || season == null) {
            Message("Season not found.")
            return@Column
        }
        val items = season.episodes.map { it.video to it.title }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column {
                    CoverSection(
                        vm, show.seasonKey(season.number), null, "${show.title} · ${season.label}",
                        show.seasonArt(season), POSTER, 0.7f,
                    )
                    Description(vm, show.seasonKey(season.number))
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { vm.playVideos(items, 0, show.title) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                        ) { Text("Play all", color = White) }
                        Spacer(Modifier.width(14.dp))
                        Text("${season.episodes.size} episodes", color = Muted, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = Line)
                }
            }
            itemsIndexed(season.episodes, key = { _, e -> e.video.id }) { index, episode ->
                val current = vm.player.mediaId == "v${episode.video.id}"
                val resume = vm.store.resumeFor("v${episode.video.id}")
                TrackRow(
                    label = if (current && vm.player.isPlaying) "▶" else "%02d".format(episode.number),
                    title = episode.title,
                    subtitle = if (resume > 0) "Resume at ${formatTime(resume)}" else null,
                    right = formatTime(episode.video.durationMs),
                    current = current,
                ) { vm.playVideos(items, index, show.title) }
            }
        }
    }
}

/** The picture. Small with the normal player controls below it, or full screen with the built-in controls. */
@Composable
fun VideoScreen(vm: AppViewModel) {
    val fullscreen = vm.fullscreen
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var hud by remember { mutableStateOf("") }
    var hudStamp by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    var overlayStamp by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableStateOf(-1f) }
    var volumeAcc by remember { mutableStateOf(0f) }
    var brightAcc by remember { mutableStateOf(0f) }
    val p = vm.player

    fun show(text: String) { hud = text; hudStamp++ }
    androidx.compose.runtime.LaunchedEffect(hudStamp) { if (hud.isNotEmpty()) { kotlinx.coroutines.delay(900); hud = "" } }
    androidx.compose.runtime.LaunchedEffect(overlayStamp, overlay, p.isPlaying) {
        if (overlay && p.isPlaying) { kotlinx.coroutines.delay(3500); overlay = false }
    }
    // Give the screen brightness back when leaving the picture.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w -> val lp = w.attributes; lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = lp }
        }
    }

    Column(Modifier.fillMaxSize().background(Black)) {
        if (!fullscreen) {
            TopBar(p.title.ifEmpty { "Video" }, onBack = { vm.showVideo = false }) {
                LinkText(if (vm.subtitlesOn) "Subtitles: on" else "Subtitles: off") { vm.toggleSubtitles() }
                LinkText("Stop") { vm.stopPlayback() }
                LinkText("Full screen") { vm.fullscreen = true }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                LinkText("Speed " + speedLabel(p.speed)) { p.cycleSpeed() }
                LinkText("Text size: " + vm.subtitleSize) { vm.cycleSubtitleSize() }
            }
        }
        Box(
            Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        useController = false
                    }
                },
                update = { view ->
                    view.player = p.controllerState
                    view.useController = false
                    view.subtitleView?.setFractionalTextSize(subtitleFractions[vm.subtitleSize] ?: 0.0533f)
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Touch layer: double-tap the left or right half to jump 10 s, drag up or down for brightness (left) or volume (right).
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { overlay = !overlay; overlayStamp++ },
                            onDoubleTap = { pos ->
                                if (pos.x < size.width / 2f) { p.skip(-10_000); show("\u221210 s") }
                                else { p.skip(10_000); show("+10 s") }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        var left = true
                        detectVerticalDragGestures(
                            onDragStart = { pos -> left = pos.x < size.width / 2f },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                val amount = -dy / size.height * 1.4f
                                if (left) {
                                    val cur = if (brightness < 0f) (activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f) else brightness
                                    brightAcc = (cur + amount).coerceIn(0.02f, 1f)
                                    brightness = brightAcc
                                    activity?.window?.let { w -> val lp = w.attributes; lp.screenBrightness = brightAcc; w.attributes = lp }
                                    show("Brightness " + (brightAcc * 100).toInt() + "%")
                                } else {
                                    val maxV = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    volumeAcc += amount * maxV
                                    val steps = volumeAcc.toInt()
                                    if (steps != 0) {
                                        volumeAcc -= steps
                                        val now = (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) + steps).coerceIn(0, maxV)
                                        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, now, 0)
                                    }
                                    show("Volume " + (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 / maxV) + "%")
                                }
                            },
                        )
                    }
            )
            if (hud.isNotEmpty()) {
                Box(Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(hud, color = White, fontSize = 18.sp)
                }
            }
            if (fullscreen && overlay) FullscreenControls(vm, Modifier.align(Alignment.BottomCenter)) { overlayStamp++ }
        }
    }
}

/** Controls on top of the picture in full screen: progress, play, jump, speed, text size. */
@Composable
private fun FullscreenControls(vm: AppViewModel, modifier: Modifier, onTouch: () -> Unit) {
    val p = vm.player
    var position by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(p.mediaId, p.isPlaying) {
        while (true) { if (!dragging) position = p.position(); kotlinx.coroutines.delay(300) }
    }
    val max = p.durationMs.coerceAtLeast(1L).toFloat()
    val shown = if (dragging) dragValue else position.coerceIn(0L, p.durationMs.coerceAtLeast(1L)).toFloat()
    Column(modifier.fillMaxWidth().background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(p.title, color = White, fontSize = 14.sp, maxLines = 1)
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(shown.toLong()), color = Soft, fontSize = 12.sp, modifier = Modifier.width(44.dp))
            androidx.compose.material3.Slider(
                value = shown.coerceAtMost(max),
                onValueChange = { dragging = true; dragValue = it; onTouch() },
                onValueChangeFinished = { p.seekTo(dragValue.toLong()); position = dragValue.toLong(); dragging = false },
                valueRange = 0f..max, modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = White, activeTrackColor = AccentHi, inactiveTrackColor = Line),
            )
            Text(formatTime(p.durationMs), color = Soft, fontSize = 12.sp, modifier = Modifier.width(44.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            LinkText("Speed " + speedLabel(p.speed)) { p.cycleSpeed(); onTouch() }
            SkipButton(forward = false, enabled = true, onClick = { p.skip(-10_000); onTouch() })
            PlayPauseButton(playing = p.isPlaying, enabled = true, onClick = { p.toggle(); onTouch() })
            SkipButton(forward = true, enabled = true, onClick = { p.skip(10_000); onTouch() })
            LinkText("Text size: " + vm.subtitleSize) { vm.cycleSubtitleSize(); onTouch() }
            LinkText(if (vm.subtitlesOn) "Subtitles: on" else "Subtitles: off") { vm.toggleSubtitles(); onTouch() }
            LinkText("Exit") { vm.fullscreen = false }
        }
    }
}
