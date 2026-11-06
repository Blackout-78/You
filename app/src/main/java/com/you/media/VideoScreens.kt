package com.you.media

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
        Text("The app needs to read the videos on this phone.", color = White, fontSize = 16.sp, textAlign = TextAlign.Center)
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
    if (movies) {
        vm.videos.movies.forEach { byId[it.video.id] = it.video to it.title }
    } else {
        vm.videos.shows.forEach { show ->
            show.seasons.forEach { season ->
                season.episodes.forEach { byId[it.video.id] = it.video to "${show.title} \u00b7 ${it.title}" }
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
                    LandscapeTile(title, sub, video.uri, null) {
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
        TopBar("Movies", null) { LinkText("\u2665") { vm.open(Screen.Favorites) }; LinkText("Search") { vm.open(Screen.Search) } }
        val movies = vm.videos.movies
        when {
            !videoAccess -> NeedVideoAccess(onAskVideo)
            movies.isEmpty() -> Message(
                if (vm.loading) "Reading your videos\u2026"
                else "No movies found.\nVideos five minutes or longer, without S01E02 in the name, show up here."
            )
            else -> {
                val sorted = remember(movies, mode, seed) { sortMovies(movies, mode, seed) }
                val featured = sorted.first()
                val collections = remember(movies) { collectionsOf(movies) }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Hero(
                            title = featured.title,
                            meta = lengthLabel(featured.video.durationMs),
                            description = vm.store.notes[featured.key].orEmpty(),
                            videoUri = featured.video.uri,
                            coverPath = vm.store.covers[featured.key],
                            onPlay = { vm.playVideos(listOf(featured.video to featured.title), 0, "") },
                            onInfo = { vm.open(Screen.MoviePage(featured.video.id)) },
                        )
                    }
                    item { SortChips(mode) { if (it == SortMode.Shuffle) seed += 1; mode = it } }
                    item { ContinueWatching(vm, true) }
                    if (collections.isNotEmpty()) {
                        item { SectionHeader("Collections") }
                        item {
                            TileRow {
                                collections.forEach { c ->
                                    Box(Modifier.padding(end = 12.dp)) {
                                        LandscapeTile(
                                            c.name, "${c.movies.size} films", c.movies.first().video.uri,
                                            vm.store.covers[c.movies.first().key],
                                        ) { vm.open(Screen.CollectionPage(c.folder)) }
                                    }
                                }
                            }
                        }
                    }
                    item { SectionHeader("All movies") }
                    posterRows(sorted.size) { i ->
                        val movie = sorted[i]
                        GridCell(
                            path = vm.store.covers[movie.key], albumId = null, title = movie.title,
                            subtitle = lengthLabel(movie.video.durationMs), aspect = POSTER, videoUri = movie.video.uri,
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
        TopBar("Movies", null) { LinkText("\u2665") { vm.open(Screen.Favorites) }; LinkText("Search") { vm.open(Screen.Search) } }
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
                        subtitle = formatTime(movie.video.durationMs), aspect = POSTER, videoUri = movie.video.uri,
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
        TopBar("TV Shows", null) { LinkText("\u2665") { vm.open(Screen.Favorites) }; LinkText("Search") { vm.open(Screen.Search) } }
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
                        videoUri = show.seasons.firstOrNull()?.episodes?.firstOrNull()?.video?.uri,
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
                    CoverSection(vm, movie.key, null, movie.title, movie.video.uri, POSTER, 0.7f)
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
        TopBar("TV Shows", null) { LinkText("\u2665") { vm.open(Screen.Favorites) }; LinkText("Search") { vm.open(Screen.Search) } }
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
                val featured = sorted.first()
                val episodeCount = featured.seasons.sumOf { it.episodes.size }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Hero(
                            title = featured.title,
                            meta = "${featured.seasons.size} season${if (featured.seasons.size == 1) "" else "s"} \u00b7 $episodeCount episodes",
                            description = vm.store.notes[featured.key].orEmpty(),
                            videoUri = first(featured).video.uri,
                            coverPath = vm.store.covers[featured.key],
                            onPlay = {
                                val eps = featured.seasons.flatMap { it.episodes }
                                val resumeAt = eps.indexOfFirst { vm.store.resumeFor("v${it.video.id}") > 0 }.coerceAtLeast(0)
                                vm.playVideos(eps.map { it.video to it.title }, resumeAt, featured.title)
                            },
                            onInfo = { vm.open(Screen.ShowPage(featured.title)) },
                        )
                    }
                    item { SortChips(mode) { if (it == SortMode.Shuffle) seed += 1; mode = it } }
                    item { ContinueWatching(vm, false) }
                    item { SectionHeader("All shows") }
                    posterRows(sorted.size) { i ->
                        val show = sorted[i]
                        val n = show.seasons.size
                        GridCell(
                            path = vm.store.covers[show.key], albumId = null, title = show.title,
                            subtitle = "$n season${if (n == 1) "" else "s"}", aspect = POSTER,
                            videoUri = first(show).video.uri,
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
        val firstVideo = show.seasons.firstOrNull()?.episodes?.firstOrNull()?.video?.uri
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
                    videoUri = season.episodes.firstOrNull()?.video?.uri,
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
                        season.episodes.firstOrNull()?.video?.uri, POSTER, 0.7f,
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
    Column(Modifier.fillMaxSize().background(Black)) {
        if (!fullscreen) {
            TopBar(vm.player.title.ifEmpty { "Video" }, onBack = { vm.showVideo = false }) {
                LinkText(if (vm.subtitlesOn) "Subtitles: on" else "Subtitles: off") { vm.toggleSubtitles() }
                LinkText("Stop") { vm.stopPlayback() }
                LinkText("Full screen") { vm.fullscreen = true }
            }
        }
        Box(
            Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.player = vm.player.controllerState
                    view.useController = fullscreen
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
