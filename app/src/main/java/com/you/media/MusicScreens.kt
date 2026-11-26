package com.you.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Music in the streaming look: home, and the lyrics card on the album page.

private enum class AlbumSort(val label: String) { Added("Recently added"), Title("A–Z") }

/**
 * Floating shuffle button for the Music tab (every Music look). It is only there while nothing is loaded
 * in the player; once a song is playing or paused (the mini player shows) it is gone, and it comes back
 * when playback is stopped.
 */
@Composable
fun BoxScope.MusicShuffleFab(vm: AppViewModel) {
    if (vm.player.mediaId != null || vm.library.artists.isEmpty()) return
    Box(
        Modifier.align(Alignment.BottomEnd).padding(16.dp)
            .semantics { contentDescription = "Shuffle all music" },
    ) {
        ShuffleFabButton {
            vm.player.playQueue(vm.library.artists.flatMap { it.albums }.flatMap { it.tracks }.shuffled(), 0)
        }
    }
}

/** The Browse look for Music: newest album big, quick picks, Recently played, Artists, Albums, all artists. */
@Composable
fun MusicHomeScreen(vm: AppViewModel) {
    val artists = vm.library.artists
    var sort by remember { mutableStateOf(AlbumSort.Added) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Music", null) {
            LinkText("Playlists") { vm.open(Screen.Playlists) }
            LinkText("♥") { vm.open(Screen.Favorites) }
            Avatar(vm.profilePicture, 32.dp, Modifier.clickable { vm.selectTab(Tab.Settings) })
        }
        GraySearchBar(vm)
        if (artists.isEmpty()) {
            Message(
                if (vm.loading) "Reading your music…"
                else "No music found.\nCopy music onto your phone, then open Settings and press Refresh library."
            )
            return@Column
        }
        // Newer files have higher ids on the phone, so the highest id is the newest album.
        val albums = remember(artists) { artists.flatMap { it.albums } }
        val newestFirst = remember(albums) { albums.sortedByDescending { a -> a.tracks.maxOf { it.id } } }
        val featured = newestFirst.first()
        val recentAlbums = vm.store.recentTracks.mapNotNull { vm.library.tracksById[it] }
            .map { it.albumId }.distinct()
            .mapNotNull { id -> albums.find { it.albumId == id } }
        val mostPlayed = albums
            .map { al -> al to al.tracks.sumOf { vm.store.playCounts[it.id] ?: 0 } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
        val sortedAlbums = when (sort) {
            AlbumSort.Added -> newestFirst
            AlbumSort.Title -> albums.sortedBy { sortKey(it.title) }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                val n = featured.tracks.size
                Hero(
                    title = featured.title,
                    meta = listOf(featured.artist, "$n song${if (n == 1) "" else "s"}",
                        lengthLabel(featured.tracks.sumOf { it.durationMs })).joinToString("  ·  "),
                    description = vm.describe(featured.key),
                    videoUri = null,
                    coverPath = vm.store.covers[featured.key],
                    albumId = featured.albumId,
                    kicker = "JUST ADDED",
                    infoLabel = "Shuffle",
                    infoMark = "⇄",
                    tall = true,
                    onPlay = { vm.player.playQueue(featured.tracks, 0) },
                    onInfo = { vm.player.playQueue(featured.tracks.shuffled(), 0) },
                )
            }
            item { QuickPicks(vm, recentAlbums.firstOrNull(), albums) }
            if (recentAlbums.isNotEmpty()) item {
                SectionHeader("Recently played")
                AlbumTiles(vm, recentAlbums.take(14))
            }
            if (mostPlayed.isNotEmpty()) item {
                SectionHeader("Most played")
                AlbumTiles(vm, mostPlayed.take(14))
            }
            item {
                SectionHeader("Artists")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    artists.take(40).forEach { artist ->
                        Column(
                            Modifier.width(112.dp).padding(end = 12.dp).clickable { vm.open(Screen.ArtistPage(artist.name)) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Cover(vm.artistCover(artist), artist.albums.firstOrNull()?.albumId, artist.name,
                                Modifier.size(100.dp).clip(CircleShape))
                            Spacer(Modifier.height(6.dp))
                            Text(artist.name, color = White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            item {
                SectionHeader("Albums")
                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
                    for (s in AlbumSort.entries) {
                        val on = s == sort
                        Box(
                            Modifier.padding(end = 8.dp).background(if (on) Accent else Panel).clickable { sort = s }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(s.label, color = if (on) White else Soft, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else null) }
                    }
                }
                AlbumTiles(vm, sortedAlbums.take(60))
            }
            item { SectionHeader("All artists") }
            posterRows(artists.size) { i ->
                val artist = artists[i]
                val n = artist.albums.size
                GridCell(
                    path = vm.artistCover(artist), albumId = artist.albums.firstOrNull()?.albumId,
                    title = artist.name, subtitle = "$n album${if (n == 1) "" else "s"}",
                ) { vm.open(Screen.ArtistPage(artist.name)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AlbumTiles(vm: AppViewModel, albums: List<Album>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        albums.forEach { album ->
            Column(Modifier.width(140.dp).padding(end = 12.dp).clickable { vm.open(Screen.AlbumPage(album.artist, album.title)) }) {
                Cover(vm.store.covers[album.key], album.albumId, album.title, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(6.dp))
                Text(album.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Two columns of quick tiles: Favorites, Shuffle everything, playlists, the album and artist you played last. */
@Composable
private fun QuickPicks(vm: AppViewModel, lastAlbum: Album?, albums: List<Album>) {
    data class Pick(val title: String, val mark: String, val album: Album?, val action: () -> Unit)
    val picks = ArrayList<Pick>()
    picks += Pick("Favorites", "♥", null) { vm.open(Screen.Favorites) }
    picks += Pick("Shuffle everything", "⇄", null) {
        vm.player.playQueue(albums.flatMap { it.tracks }.shuffled(), 0)
    }
    vm.store.playlists.keys.sorted().take(2).forEach { name ->
        picks += Pick(name, "≡", null) { vm.open(Screen.PlaylistPage(name)) }
    }
    if (lastAlbum != null) {
        picks += Pick(lastAlbum.title, "", lastAlbum) { vm.open(Screen.AlbumPage(lastAlbum.artist, lastAlbum.title)) }
        picks += Pick(lastAlbum.artist, "", lastAlbum) { vm.open(Screen.ArtistPage(lastAlbum.artist)) }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        picks.take(6).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                pair.forEachIndexed { i, p ->
                    if (i == 1) Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(4.dp)).background(Panel)
                            .clickable(onClick = p.action),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (p.album != null) {
                            Cover(vm.store.covers[p.album.key], p.album.albumId, p.title, Modifier.size(52.dp))
                        } else {
                            Box(Modifier.size(52.dp).background(DarkGray), contentAlignment = Alignment.Center) {
                                Text(p.mark, color = White, fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(p.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    }
                }
                if (pair.size == 1) { Spacer(Modifier.width(8.dp)); Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** On the album page: the lyrics of the song playing, the current line large and white. Tap to open full lyrics. */
@Composable
fun AlbumLyricsCard(vm: AppViewModel, album: Album) {
    val playingHere = album.tracks.any { vm.player.mediaId == it.id.toString() }
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(4.dp)).background(Panel)
            .clickable(enabled = playingHere && vm.lyrics != null) { vm.showLyrics = true }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LYRICS", color = Soft, fontSize = 11.sp, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
            if (playingHere && vm.lyrics != null) Text("Full screen ›", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        val lyrics = vm.lyrics
        when {
            !playingHere -> Text("Play a song from this album and its lyrics show here.", color = Muted, fontSize = 14.sp)
            lyrics == null -> Text(if (vm.lyricsLoading) "Looking for lyrics…" else "This song has no lyrics file.",
                color = Muted, fontSize = 14.sp)
            !lyrics.synced -> Text(lyrics.lines.take(8).joinToString("\n") { it.text }, color = Soft, fontSize = 15.sp, lineHeight = 22.sp)
            else -> {
                var position by remember { mutableLongStateOf(0L) }
                LaunchedEffect(vm.player.mediaId) {
                    while (true) { position = vm.player.position(); delay(250) }
                }
                val current = lyrics.lines.indexOfLast { it.timeMs <= position }
                val from = (current - 1).coerceAtLeast(0)
                lyrics.lines.drop(from).take(6).forEachIndexed { i, line ->
                    val index = from + i
                    Text(
                        line.text.ifEmpty { "♪" },
                        color = when { index == current -> White; index < current -> Line; else -> Muted },
                        fontSize = if (index == current) 22.sp else 18.sp,
                        fontWeight = if (index == current) FontWeight.ExtraBold else FontWeight.SemiBold,
                        lineHeight = 28.sp,
                        modifier = Modifier.padding(vertical = 3.dp).clickable { vm.player.seekTo(line.timeMs) },
                    )
                }
            }
        }
    }
}
