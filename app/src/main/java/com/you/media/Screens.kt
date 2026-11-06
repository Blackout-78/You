package com.you.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- small pieces --------------------------------------------------------------------

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, right: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("←", color = White, fontSize = 22.sp) }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title, color = White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            right()
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
fun GridCell(
    path: String?, albumId: Long?, title: String, subtitle: String?,
    aspect: Float = 1f, videoUri: Uri? = null, onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                drawLine(Line, Offset(size.width, 0f), Offset(size.width, size.height), 2f)
                drawLine(Line, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            }
            .padding(14.dp),
    ) {
        Cover(path, albumId, title, Modifier.fillMaxWidth().aspectRatio(aspect), videoUri)
        Spacer(Modifier.height(8.dp))
        Text(title, color = White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun LinkText(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = Muted, fontSize = 13.sp) }
}

/** The description under a cover, with a button to write or change it. */
@Composable
fun Description(vm: AppViewModel, key: String) {
    val text = vm.store.notes[key].orEmpty()
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (text.isEmpty()) Text("No description yet.", color = Muted, fontSize = 14.sp)
        else Text(text, color = Soft, fontSize = 14.sp)
        LinkText("Edit description") { editing = true }
    }
    if (editing) {
        var draft by remember { mutableStateOf(text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = Panel,
            title = { Text("Description", color = White) },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, minLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White, unfocusedTextColor = White,
                        focusedBorderColor = White, unfocusedBorderColor = Line, cursorColor = White,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.store.setNote(key, draft); editing = false }) { Text("Save", color = White) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel", color = Muted) } },
        )
    }
}

/** A big cover with buttons to use your own picture. */
@Composable
fun CoverSection(
    vm: AppViewModel, key: String, albumId: Long?, label: String,
    videoUri: Uri? = null, aspect: Float = 1f, widthFraction: Float = 1f,
) {
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { vm.store.importCover(uri) }
            if (saved != null) vm.store.setCover(key, saved)
        }
    }
    val custom = vm.store.covers[key]
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Cover(custom, albumId, label, Modifier.fillMaxWidth(widthFraction).aspectRatio(aspect), videoUri)
        }
        Row {
            LinkText("Change cover") {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (custom != null) LinkText("Use original") { vm.store.setCover(key, null) }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun TrackRow(
    label: String, title: String, subtitle: String?, right: String, current: Boolean,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().combinedClickable(onLongClick = onLongClick, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (current) White else Muted, fontSize = 14.sp, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (current) White else Soft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(right, color = Muted, fontSize = 13.sp)
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

// ---- the screens ---------------------------------------------------------------------

@Composable
fun ArtistsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopBar(vm.profileName.ifBlank { "You" }, null) {
            Avatar(vm.profilePicture, 32.dp, Modifier.clickable { vm.selectTab(Tab.Settings) })
            Spacer(Modifier.width(6.dp))
            LinkText("Search") { vm.open(Screen.Search) }
            LinkText("Playlists") { vm.open(Screen.Playlists) }
            LinkText("\u2665") { vm.open(Screen.Favorites) }
        }
        val artists = vm.library.artists
        if (artists.isEmpty()) {
            Message(
                if (vm.loading) "Reading your music…"
                else "No music found.\nCopy music onto your phone, then open Settings and press Refresh library."
            )
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                items(artists, key = { it.name }) { artist ->
                    val n = artist.albums.size
                    GridCell(
                        path = vm.store.covers[artist.key],
                        albumId = artist.albums.firstOrNull()?.albumId,
                        title = artist.name,
                        subtitle = "$n album${if (n == 1) "" else "s"}",
                    ) { vm.open(Screen.ArtistPage(artist.name)) }
                }
            }
        }
    }
}

@Composable
fun ArtistBrowseScreen(vm: AppViewModel, name: String) {
    val artist = vm.library.artists.find { it.name == name }
    var showAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TopBar(name, onBack = vm::back)
        if (artist == null) {
            Message("Artist not found.")
            return@Column
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch {
                val saved = withContext(Dispatchers.IO) { vm.store.importCover(uri) }
                if (saved != null) vm.store.setCover(artist.key, saved)
            }
        }
        val allSongs = remember(artist, vm.store.favorites.size) {
            val all = artist.albums.flatMap { it.tracks }
            all.filter { vm.store.isFavorite(it.id) } + all.filter { !vm.store.isFavorite(it.id) }
        }
        val shown = if (showAll) allSongs else allSongs.take(5)
        val (singles, albums) = artist.albums.partition { it.tracks.size <= 4 }
        val customCover = vm.store.covers[artist.key]

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Hero(
                    title = artist.name,
                    meta = "${artist.albums.size} album${if (artist.albums.size == 1) "" else "s"} \u00b7 ${allSongs.size} songs",
                    description = vm.store.notes[artist.key].orEmpty(),
                    videoUri = null,
                    coverPath = customCover,
                    albumId = artist.albums.firstOrNull()?.albumId,
                    playLabel = "Shuffle",
                    infoLabel = "Play",
                    onPlay = { vm.player.playQueue(allSongs.shuffled(), 0) },
                    onInfo = { vm.player.playQueue(artist.albums.flatMap { it.tracks }, 0) },
                )
            }
            item {
                Row {
                    LinkText("Change picture") {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (customCover != null) LinkText("Use original") { vm.store.setCover(artist.key, null) }
                }
                Description(vm, artist.key)
            }
            item { SectionHeader("Songs") }
            itemsIndexed(shown, key = { _, t -> "t${t.id}" }) { index, track ->
                val current = vm.player.mediaId == track.id.toString()
                Column(
                    Modifier.fillMaxWidth().clickable { vm.player.playQueue(allSongs, index) }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(null, track.albumId, track.album, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (current && vm.player.isPlaying) "\u25b6 " else "") + track.title,
                                color = if (current) White else Soft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(track.album, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(formatTime(track.durationMs), color = Muted, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = Line)
                }
            }
            if (allSongs.size > 5) {
                item { LinkText(if (showAll) "Show fewer" else "Show all ${allSongs.size}") { showAll = !showAll } }
            }
            if (albums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                item { AlbumRow(vm, artist.name, albums) }
            }
            if (singles.isNotEmpty()) {
                item { SectionHeader("Singles and EPs") }
                item { AlbumRow(vm, artist.name, singles) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ArtistScreen(vm: AppViewModel, name: String) {
    if (vm.isGrid("music")) ArtistGridScreen(vm, name) else ArtistBrowseScreen(vm, name)
}

/** The Grid look: cover and description on top, albums in a plain grid. */
@Composable
fun ArtistGridScreen(vm: AppViewModel, name: String) {
    val artist = vm.library.artists.find { it.name == name }
    Column(Modifier.fillMaxSize()) {
        TopBar(name, onBack = vm::back)
        if (artist == null) {
            Message("Artist not found.")
            return@Column
        }
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    CoverSection(vm, artist.key, artist.albums.firstOrNull()?.albumId, artist.name)
                    Description(vm, artist.key)
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Line)
                }
            }
            items(artist.albums, key = { it.title }) { album ->
                val n = album.tracks.size
                GridCell(
                    path = vm.store.covers[album.key],
                    albumId = album.albumId,
                    title = album.title,
                    subtitle = "$n track${if (n == 1) "" else "s"}",
                ) { vm.open(Screen.AlbumPage(artist.name, album.title)) }
            }
        }
    }
}

@Composable
private fun AlbumRow(vm: AppViewModel, artistName: String, albums: List<Album>) {
    TileRow {
        albums.forEach { album ->
            val n = album.tracks.size
            Column(
                Modifier.width(140.dp).padding(end = 14.dp)
                    .clickable { vm.open(Screen.AlbumPage(artistName, album.title)) }
            ) {
                Cover(vm.store.covers[album.key], album.albumId, album.title, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(6.dp))
                Text(album.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$n track${if (n == 1) "" else "s"}", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AlbumScreen(vm: AppViewModel, artistName: String, albumTitle: String) {
    var addTo by remember { mutableStateOf<Long?>(null) }
    addTo?.let { AddToPlaylistDialog(vm, it) { addTo = null } }
    val album = vm.library.artists.find { it.name == artistName }?.albums?.find { it.title == albumTitle }
    Column(Modifier.fillMaxSize()) {
        TopBar(albumTitle, onBack = vm::back)
        if (album == null) {
            Message("Album not found.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column {
                    CoverSection(vm, album.key, album.albumId, album.title)
                    Description(vm, album.key)
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { vm.player.playQueue(album.tracks, 0) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                        ) { Text("Play album", color = White) }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "${album.tracks.size} tracks · ${formatTime(album.tracks.sumOf { it.durationMs })}",
                            color = Muted, fontSize = 13.sp,
                        )
                    }
                    HorizontalDivider(color = Line)
                }
            }
            itemsIndexed(album.tracks, key = { _, t -> t.id }) { index, track ->
                val current = vm.player.mediaId == track.id.toString()
                val label = when {
                    current && vm.player.isPlaying -> "▶"
                    current -> "‖"
                    track.number > 0 -> "%02d".format(track.number)
                    else -> "–"
                }
                TrackRow(label, track.title, null, formatTime(track.durationMs), current, onLongClick = { addTo = track.id }) {
                    vm.player.playQueue(album.tracks, index)
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel) {
    var addTo by remember { mutableStateOf<Long?>(null) }
    addTo?.let { AddToPlaylistDialog(vm, it) { addTo = null } }
    val tracks = vm.store.favorites.mapNotNull { vm.library.tracksById[it] }
    val known = HashMap<Long, Pair<Video, String>>()
    vm.videos.movies.forEach { known[it.video.id] = it.video to it.title }
    vm.videos.shows.forEach { show ->
        show.seasons.forEach { season ->
            season.episodes.forEach { known[it.video.id] = it.video to "${show.title} \u00b7 ${it.title}" }
        }
    }
    val videos = vm.store.videoFavorites.mapNotNull { known[it] }
    Column(Modifier.fillMaxSize()) {
        TopBar("\u2665 Favorites", onBack = vm::back)
        if (tracks.isEmpty() && videos.isEmpty()) {
            Message("No favorites yet.\nPress the heart on the player while a song, movie or episode plays.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (tracks.isNotEmpty()) {
                    item {
                        TextButton(onClick = { vm.player.playQueue(tracks, 0) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("Play all songs", color = White)
                        }
                    }
                    itemsIndexed(tracks, key = { _, t -> "s${t.id}" }) { index, track ->
                        val current = vm.player.mediaId == track.id.toString()
                        TrackRow(
                            if (current && vm.player.isPlaying) "\u25b6" else "%02d".format(index + 1),
                            track.title, "${track.artist} \u00b7 ${track.album}", formatTime(track.durationMs), current,
                            onLongClick = { addTo = track.id },
                        ) { vm.player.playQueue(tracks, index) }
                    }
                }
                if (videos.isNotEmpty()) {
                    item {
                        Text(
                            "MOVIES & EPISODES", color = Muted, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
                        )
                    }
                    itemsIndexed(videos, key = { _, v -> "v${v.first.id}" }) { index, (video, title) ->
                        val current = vm.player.mediaId == "v${video.id}"
                        TrackRow(
                            if (current && vm.player.isPlaying) "\u25b6" else "%02d".format(index + 1),
                            title, null, formatTime(video.durationMs), current,
                        ) { vm.playVideos(videos, index, "") }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsScreen(vm: AppViewModel) {
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.setLyricsFolder(uri)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(vm.player.title.ifEmpty { "Lyrics" }, onBack = { vm.showLyrics = false })
        val lyrics = vm.lyrics
        when {
            lyrics != null && lyrics.synced -> SyncedLyrics(vm, lyrics)
            lyrics != null -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                item { Spacer(Modifier.height(16.dp)) }
                items(lyrics.lines.size) { i ->
                    Text(lyrics.lines[i].text.ifEmpty { " " }, color = Soft, fontSize = 16.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
            vm.lyricsLoading || vm.lyricsIndexing -> Message(if (vm.lyricsIndexing) "Looking through your folder\u2026" else "Looking for lyrics\u2026")
            else -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No lyrics found for this song.", color = White, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (vm.lyricsFolderSet)
                        "The app looks for a .lrc or .txt file with the same name as the song, or lyrics inside the song file."
                    else
                        "To use .lrc and .txt files, choose the folder that holds your music. Lyrics stored inside song files work without this.",
                    color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = { chooseFolder.launch(null) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                ) { Text(if (vm.lyricsFolderSet) "Choose a different music folder" else "Choose music folder", color = White) }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(vm: AppViewModel, lyrics: Lyrics) {
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vm.player.mediaId) {
        while (true) {
            position = vm.player.position()
            delay(200)
        }
    }
    val current = lyrics.lines.indexOfLast { it.timeMs <= position }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 3).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item { Spacer(Modifier.height(24.dp)) }
        items(lyrics.lines.size) { i ->
            val line = lyrics.lines[i]
            val isCurrent = i == current
            Text(
                text = line.text.ifEmpty { "\u266a" },
                color = if (isCurrent) White else Muted,
                fontSize = if (isCurrent) 22.sp else 18.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.player.seekTo(line.timeMs) }
                    .padding(vertical = 9.dp),
            )
        }
        item { Spacer(Modifier.height(200.dp)) }
    }
}
