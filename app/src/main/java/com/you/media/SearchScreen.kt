package com.you.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp))
}

@Composable
private fun ResultRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Looks through your songs, albums, artists, movies and shows by name. Everything stays on the phone. */
@Composable
fun SearchScreen(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val artists = if (q.isEmpty()) emptyList() else vm.library.artists.filter { it.name.lowercase().contains(q) }
    val albums = if (q.isEmpty()) emptyList() else vm.library.artists.flatMap { a ->
        a.albums.filter { it.title.lowercase().contains(q) }.map { a to it }
    }
    val songs = if (q.isEmpty()) emptyList() else vm.library.tracksById.values
        .filter { it.title.lowercase().contains(q) }.sortedBy { it.title.lowercase() }.take(50)
    val movies = if (q.isEmpty()) emptyList() else vm.videos.movies.filter { it.title.lowercase().contains(q) }
    val shows = if (q.isEmpty()) emptyList() else vm.videos.shows.filter { it.title.lowercase().contains(q) }
    val none = q.isNotEmpty() && artists.isEmpty() && albums.isEmpty() && songs.isEmpty() && movies.isEmpty() && shows.isEmpty()

    Column(Modifier.fillMaxSize()) {
        TopBar("Search", onBack = vm::back)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Songs, albums, artists, movies, shows", color = Muted) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White, unfocusedTextColor = White,
                focusedBorderColor = White, unfocusedBorderColor = Line, cursorColor = White,
            ),
        )
        if (none) {
            Message("Nothing found for “${query.trim()}”.")
        } else LazyColumn(Modifier.fillMaxSize()) {
            if (artists.isNotEmpty()) {
                item { SectionTitle("ARTISTS") }
                items(artists.size) { i ->
                    val a = artists[i]
                    ResultRow(a.name, "${a.albums.size} album${if (a.albums.size == 1) "" else "s"}") {
                        vm.open(Screen.ArtistPage(a.name))
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item { SectionTitle("ALBUMS") }
                items(albums.size) { i ->
                    val (a, al) = albums[i]
                    ResultRow(al.title, a.name) { vm.open(Screen.AlbumPage(a.name, al.title)) }
                }
            }
            if (songs.isNotEmpty()) {
                item { SectionTitle("SONGS") }
                items(songs.size) { i ->
                    val t = songs[i]
                    ResultRow(t.title, "${t.artist} · ${t.album}") { vm.player.playQueue(songs, i) }
                }
            }
            if (movies.isNotEmpty()) {
                item { SectionTitle("MOVIES") }
                items(movies.size) { i ->
                    val m = movies[i]
                    ResultRow(m.title, formatTime(m.video.durationMs)) { vm.open(Screen.MoviePage(m.video.id)) }
                }
            }
            if (shows.isNotEmpty()) {
                item { SectionTitle("TV SHOWS") }
                items(shows.size) { i ->
                    val s = shows[i]
                    ResultRow(s.title, "${s.seasons.size} season${if (s.seasons.size == 1) "" else "s"}") {
                        vm.open(Screen.ShowPage(s.title))
                    }
                }
            }
        }
    }
}
