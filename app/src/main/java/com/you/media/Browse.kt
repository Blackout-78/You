package com.you.media

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Short length like "30m" or "1h 32m". */
fun lengthLabel(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(1)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

enum class SortMode(val label: String) { Added("Recently added"), Title("Title A–Z"), Oldest("Oldest"), Shuffle("Shuffle") }

/** Big picture at the top with the name, a few words and the two main buttons. Black and white around the picture. */
@Composable
fun Hero(
    title: String, meta: String, description: String, videoUri: Uri?, coverPath: String?,
    onPlay: () -> Unit, onInfo: () -> Unit,
    albumId: Long? = null, playLabel: String = "Play", infoLabel: String = "More info",
) {
    Box(Modifier.fillMaxWidth().height(340.dp).background(Panel)) {
        Cover(coverPath, albumId, title, Modifier.fillMaxSize(), videoUri, large = true)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to YouColors.solidDark.copy(alpha = 0.4f), 0.45f to YouColors.solidDark.copy(alpha = 0f), 1.0f to Black,
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(title, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(meta, color = Soft, fontSize = 14.sp)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(description, color = Soft, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Box(
                    Modifier.background(White).clickable(onClick = onPlay).padding(horizontal = 26.dp, vertical = 11.dp),
                ) { Text(playLabel, color = YouColors.solidDark, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.background(YouColors.solidDark.copy(alpha = 0.8f)).clickable(onClick = onInfo).padding(horizontal = 22.dp, vertical = 11.dp),
                ) { Text(infoLabel, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SortChips(selected: SortMode, onSelect: (SortMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SORT", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.width(14.dp))
        for (mode in SortMode.entries) {
            val on = mode == selected
            Box(
                Modifier.padding(end = 8.dp)
                    .background(if (on) White else Panel)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text(mode.label, color = if (on) YouColors.solidDark else Soft, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else null) }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 10.dp))
}

/** A wide picture with the name written over the bottom of it. */
@Composable
fun LandscapeTile(title: String, subtitle: String, videoUri: Uri?, coverPath: String?, onClick: () -> Unit) {
    Box(Modifier.width(240.dp).aspectRatio(16f / 9f).background(Panel).clickable(onClick = onClick)) {
        Cover(coverPath, null, title, Modifier.fillMaxSize(), videoUri)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to YouColors.solidDark.copy(alpha = 0f), 1.0f to YouColors.solidDark.copy(alpha = 0.9f))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Soft, fontSize = 12.sp)
        }
    }
}

@Composable
fun TileRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) { content() }
}

/** Collections are folders on the phone that hold two or more movies. */
data class MovieCollection(val folder: String, val name: String, val movies: List<Movie>)

private val GENERIC_FOLDERS = setOf("movies", "movie", "video", "videos", "download", "downloads", "dcim", "documents", "")

fun collectionsOf(movies: List<Movie>): List<MovieCollection> =
    movies.groupBy { it.video.relativePath }
        .filter { (folder, list) ->
            list.size >= 2 && folder.trim('/').substringAfterLast('/').lowercase() !in GENERIC_FOLDERS
        }
        .map { (folder, list) ->
            MovieCollection(folder, folder.trim('/').substringAfterLast('/'), list.sortedBy { sortKey(it.title) })
        }
        .sortedBy { sortKey(it.name) }

fun sortMovies(movies: List<Movie>, mode: SortMode, seed: Int): List<Movie> = when (mode) {
    SortMode.Added -> movies.sortedByDescending { it.video.dateAdded }
    SortMode.Oldest -> movies.sortedBy { it.video.dateAdded }
    SortMode.Title -> movies.sortedBy { sortKey(it.title) }
    SortMode.Shuffle -> movies.shuffled(java.util.Random(seed.toLong()))
}

/** Poster cells two across, usable inside a LazyColumn. */
fun androidx.compose.foundation.lazy.LazyListScope.posterRows(
    count: Int,
    cell: @Composable (Int) -> Unit,
) {
    val rows = (count + 1) / 2
    items(rows) { r ->
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { cell(r * 2) }
            Box(Modifier.weight(1f)) { if (r * 2 + 1 < count) cell(r * 2 + 1) }
        }
    }
}

@Composable
fun CollectionScreen(vm: AppViewModel, folder: String) {
    val collection = collectionsOf(vm.videos.movies).find { it.folder == folder }
    Column(Modifier.fillMaxSize()) {
        TopBar(collection?.name ?: "Collection", onBack = vm::back)
        if (collection == null) {
            Message("Collection not found.")
        } else LazyColumn(Modifier.fillMaxSize()) {
            posterRows(collection.movies.size) { i ->
                val m = collection.movies[i]
                GridCell(
                    path = vm.store.covers[m.key], albumId = null, title = m.title,
                    subtitle = lengthLabel(m.video.durationMs), aspect = 2f / 3f, videoUri = m.video.uri,
                ) { vm.open(Screen.MoviePage(m.video.id)) }
            }
        }
    }
}
