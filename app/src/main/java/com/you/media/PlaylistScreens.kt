package com.you.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun nameFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White, unfocusedTextColor = White,
    focusedBorderColor = White, unfocusedBorderColor = Line, cursorColor = White,
)

/** Pops up when you press and hold a song: pick a playlist, or make a new one. */
@Composable
fun AddToPlaylistDialog(vm: AppViewModel, trackId: Long, onDismiss: () -> Unit) {
    val song = vm.library.tracksById[trackId]
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (song != null) "This song" else "Add to playlist") },
        text = {
            Column {
                if (song != null) {
                    TextButton(onClick = { vm.player.playNext(song); onDismiss() }) { Text("Play next") }
                    TextButton(onClick = { vm.player.addToQueue(song); onDismiss() }) { Text("Add to queue") }
                    Text("Add to playlist", color = Muted, modifier = Modifier.padding(top = 6.dp))
                }
                vm.store.playlists.keys.sortedBy { it.lowercase() }.forEach { name ->
                    TextButton(onClick = { vm.store.addToPlaylist(name, trackId); onDismiss() }) { Text(name) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    placeholder = { Text("New playlist name", color = Muted) },
                    colors = nameFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = {
                    if (vm.store.createPlaylist(newName)) vm.store.addToPlaylist(newName.trim(), trackId)
                    onDismiss()
                },
            ) { Text("Create and add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlaylistsScreen(vm: AppViewModel) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Playlists", onBack = vm::back) { LinkText("New") { creating = true } }
        val names = vm.store.playlists.keys.sortedBy { it.lowercase() }
        if (names.isEmpty()) {
            Message("No playlists yet.\nPress New to make one. Then press and hold any song to add it.")
        } else LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(names, key = { _, n -> n }) { _, name ->
                val count = vm.store.playlists[name]?.size ?: 0
                TrackRow("", name, null, "$count song${if (count == 1) "" else "s"}", false) {
                    vm.open(Screen.PlaylistPage(name))
                }
            }
        }
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    placeholder = { Text("Name", color = Muted) }, colors = nameFieldColors(),
                )
            },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    vm.store.createPlaylist(newName); newName = ""; creating = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PlaylistScreen(vm: AppViewModel, name: String) {
    val ids = vm.store.playlists[name]
    var removeId by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val tracks = (ids ?: emptyList()).mapNotNull { vm.library.tracksById[it] }

    Column(Modifier.fillMaxSize()) {
        TopBar(name, onBack = vm::back) { LinkText("Delete") { deleting = true } }
        if (ids == null) {
            Message("Playlist not found.")
        } else if (tracks.isEmpty()) {
            Message("This playlist is empty.\nPress and hold a song anywhere to add it.")
        } else LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { vm.player.playQueue(tracks, 0) },
                        border = BorderStroke(1.dp, Line),
                    ) { Text("Play all", color = White) }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "${tracks.size} songs · ${formatTime(tracks.sumOf { it.durationMs })}",
                        color = Muted, fontSize = 13.sp,
                    )
                }
                HorizontalDivider(color = Line)
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                val current = vm.player.mediaId == track.id.toString()
                TrackRow(
                    if (current && vm.player.isPlaying) "▶" else "%02d".format(index + 1),
                    track.title, "${track.artist} · ${track.album}", formatTime(track.durationMs), current,
                    onLongClick = { removeId = track.id },
                ) { vm.player.playQueue(tracks, index) }
            }
        }
    }
    removeId?.let { id ->
        AlertDialog(
            onDismissRequest = { removeId = null },
            title = { Text("Remove from playlist?") },
            text = { Text("The song stays on your phone.") },
            confirmButton = { TextButton(onClick = { vm.store.removeFromPlaylist(name, id); removeId = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text("Cancel") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “$name”?") },
            text = { Text("Your songs stay on the phone.") },
            confirmButton = {
                TextButton(onClick = { vm.store.deletePlaylist(name); deleting = false; vm.back() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}
