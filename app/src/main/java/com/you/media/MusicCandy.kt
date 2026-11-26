package com.you.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Music in the Black Candy style: chips (Albums, Artists, Songs, Playlists), a sort row, and a 2-column grid.

private enum class Chip(val label: String, val sorts: List<String>) {
    Albums("Albums", listOf("Title", "Artist")),
    Artists("Artists", listOf("Name")),
    Songs("Songs", listOf("Title", "Artist", "Album")),
    Playlists("Playlists", listOf("Name")),
}

/** The sort choice (field and direction) is remembered for each chip. */
@Composable
fun MusicCandyScreen(vm: AppViewModel) {
    var chip by remember { mutableStateOf(Chip.Albums) }
    val artists = vm.library.artists
    Column(Modifier.fillMaxSize()) {
        TopBar("Music", null) {
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
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
            for (c in Chip.entries) {
                val on = c == chip
                Box(
                    Modifier.padding(end = 8.dp).background(if (on) Accent else Panel)
                        .clickable { chip = c }.padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text(c.label, color = if (on) White else Soft, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else null, maxLines = 1, softWrap = false) }
            }
        }
        val (field, asc) = vm.musicSort(chip.label, chip.sorts.first())
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chip.sorts.size > 1) {
                Text("Sort by", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                for (s in chip.sorts) {
                    val on = s == field
                    Box(
                        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50))
                            .border(1.dp, if (on) Soft else Line, RoundedCornerShape(50))
                            .background(if (on) DarkGray else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { vm.setMusicSort(chip.label, s, asc) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) { Text(s, color = if (on) White else Soft, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(AccentHi)
                    .clickable { vm.setMusicSort(chip.label, field, !asc) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) { Text(if (asc) "A to Z ↓" else "Z to A ↑", color = White, fontSize = 12.sp) }
        }
        when (chip) {
            Chip.Albums -> AlbumsGrid(vm, field, asc)
            Chip.Artists -> ArtistsGrid(vm, asc)
            Chip.Songs -> SongsList(vm, field, asc)
            Chip.Playlists -> PlaylistsList(vm, asc)
        }
    }
}

private fun <T> List<T>.dir(asc: Boolean) = if (asc) this else this.reversed()

@Composable
private fun AlbumsGrid(vm: AppViewModel, field: String, asc: Boolean) {
    val albums = remember(vm.library, field, asc) {
        val all = vm.library.artists.flatMap { it.albums }
        val sorted = if (field == "Artist") all.sortedWith(compareBy({ sortKey(it.artist) }, { sortKey(it.title) }))
        else all.sortedBy { sortKey(it.title) }
        sorted.dir(asc)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.key }) { album ->
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { vm.open(Screen.AlbumPage(album.artist, album.title)) }) {
                Cover(vm.store.covers[album.key], album.albumId, album.title, Modifier.fillMaxSize())
                CaptionBar(album.title, album.artist, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
private fun ArtistsGrid(vm: AppViewModel, asc: Boolean) {
    val artists = remember(vm.library, asc) { vm.library.artists.sortedBy { sortKey(it.name) }.dir(asc) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(artists, key = { it.name }) { artist ->
            val n = artist.albums.size
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { vm.open(Screen.ArtistPage(artist.name)) }) {
                Cover(vm.artistCover(artist), artist.albums.firstOrNull()?.albumId, artist.name, Modifier.fillMaxSize())
                CaptionBar(artist.name, "$n album${if (n == 1) "" else "s"}", Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
private fun SongsList(vm: AppViewModel, field: String, asc: Boolean) {
    val songs = remember(vm.library, field, asc) {
        val all = vm.library.artists.flatMap { a -> a.albums.flatMap { it.tracks } }
        val sorted = when (field) {
            "Artist" -> all.sortedWith(compareBy({ sortKey(it.artist) }, { sortKey(it.album) }, { it.disc }, { it.number }))
            "Album" -> all.sortedWith(compareBy({ sortKey(it.album) }, { it.disc }, { it.number }))
            else -> all.sortedBy { sortKey(it.title) }
        }
        sorted.dir(asc)
    }
    val albumOf = remember(vm.library) {
        vm.library.artists.flatMap { it.albums }.associateBy { it.artist.lowercase() to it.title.lowercase() }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs, key = { _, t -> t.id }) { index, track ->
            val album = albumOf[track.albumArtist.lowercase() to track.album.lowercase()]
            Row(
                Modifier.fillMaxWidth().clickable { vm.player.playQueue(songs, index) }.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(album?.let { vm.store.covers[it.key] }, track.albumId, track.title,
                    Modifier.size(44.dp).clip(RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist} · ${track.album}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatTime(track.durationMs), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PlaylistsList(vm: AppViewModel, asc: Boolean) {
    val names = vm.store.playlists.keys.sortedBy { sortKey(it) }.dir(asc)
    if (names.isEmpty()) {
        Message("No playlists yet.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(names, key = { it }) { name ->
            val n = vm.store.playlists[name]?.size ?: 0
            Column(Modifier.fillMaxWidth().clickable { vm.open(Screen.PlaylistPage(name)) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(name, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("$n song${if (n == 1) "" else "s"}", color = Muted, fontSize = 12.sp)
            }
        }
    }
}
