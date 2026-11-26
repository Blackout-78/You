package com.you.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.setLyricsFolder(uri)
    }
    var confirm by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", null)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ProfileSection(vm)
            Spacer(Modifier.height(32.dp))
            IconColorSection()
            Spacer(Modifier.height(22.dp))
            AppearanceSection(vm)
            Spacer(Modifier.height(32.dp))
            Text("Layout", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Browse has a big picture and rows. Grid is a plain grid of covers. Candy (Music only) has Albums, Artists, Songs and Playlists chips with sorting. Pick one for each section.",
                color = Muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            for ((key, label) in listOf("music" to "Music", "movies" to "Movies", "shows" to "TV Shows")) {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(label, color = Soft, fontSize = 15.sp, modifier = Modifier.width(96.dp))
                    for (theme in if (key == "music") listOf("Candy", "Browse", "Grid") else listOf("Browse", "Grid")) {
                        val on = vm.themes[key] == theme
                        androidx.compose.foundation.layout.Box(
                            Modifier.padding(end = 8.dp)
                                .background(if (on) Accent else Panel)
                                .clickable { vm.setTheme(key, theme) }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                theme, color = if (on) White else Soft, fontSize = 14.sp,
                                fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else null,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Library folders", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick where each kind lives. Add as many folders as you like. With no folder " +
                    "for a kind, the app reads the whole phone. It looks for new files every time you open it.",
                color = Muted, fontSize = 14.sp,
            )
            for ((kind, label) in listOf(
                LibraryFolders.MUSIC to "Music", LibraryFolders.MOVIES to "Movies", LibraryFolders.TV to "TV Shows",
            )) {
                FolderSection(vm, kind, label)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.refresh() }, border = BorderStroke(1.dp, Line)) {
                Text(if (vm.loading) "Reading\u2026" else "Refresh library", color = White)
            }
            Spacer(Modifier.height(32.dp))
            Text("Lyrics, subtitles and details folder", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (vm.lyricsFolderSet) "A folder is chosen. ${vm.lyricsIndex.fileCount} lyric files and " +
                    "${vm.lyricsIndex.nfoCount} .nfo files found."
                else "No folder chosen. Pick the folder that holds your music, movies and TV. " +
                    "The app reads .lrc lyrics, subtitles, .nfo titles and plots, and artist pictures and descriptions from it.",
                color = Muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { chooseFolder.launch(null) }, border = BorderStroke(1.dp, Line)) {
                Text(if (vm.lyricsFolderSet) "Change folder" else "Choose folder", color = White)
            }
            if (vm.lyricsFolderSet) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.rescanFolder() }, border = BorderStroke(1.dp, Line)) {
                    Text(if (vm.lyricsIndexing) "Scanning\u2026" else "Scan folder again", color = White)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Subtitles: put a .srt or .vtt file next to a video with the same name, then scan again.",
                color = Muted, fontSize = 13.sp,
            )

            Spacer(Modifier.height(32.dp))
            Text("Clear my data", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Removes the descriptions, covers you picked, favorites, video resume spots and lyrics folder " +
                    "that the app saved. Your music and videos are not touched.",
                color = Muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { confirm = true }, border = BorderStroke(1.dp, Line)) {
                Text("Clear my data", color = White)
            }
            if (cleared) {
                Spacer(Modifier.height(8.dp))
                Text("Done. Everything the app saved is gone.", color = Soft, fontSize = 14.sp)
            }

            Spacer(Modifier.height(32.dp))
            Text("Privacy", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "The app has no internet permission, so it cannot connect to anything. " +
                    "It collects nothing and shares nothing. Everything it remembers stays on this phone.",
                color = Muted, fontSize = 14.sp,
            )

            Spacer(Modifier.height(32.dp))
            Text("Updates", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Opens the You releases page on GitHub in your browser. Download the newest APK there and " +
                    "install it over this one. The app itself never connects to anything.",
                color = Muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/Blackout-78/You/releases"),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                    }
                },
                border = BorderStroke(1.dp, Line),
            ) {
                Text("Open GitHub Releases", color = White)
            }

            Spacer(Modifier.height(32.dp))
            Text("About", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(Version.full, color = Muted, fontSize = 14.sp)
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear my data?") },
            text = { Text("This cannot be undone. Your music and videos stay on the phone.") },
            confirmButton = {
                TextButton(onClick = { vm.clearMyData(); confirm = false; cleared = true }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** One Library folders block: its folders with Remove, and an Add folder button. */
@Composable
private fun FolderSection(vm: AppViewModel, kind: String, label: String) {
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.addLibraryFolder(kind, uri)
    }
    val folders = vm.libraryFolders[kind].orEmpty()
    Spacer(Modifier.height(16.dp))
    Text(label, color = Soft, fontSize = 15.sp)
    if (folders.isEmpty()) {
        Text("Whole phone", color = Muted, fontSize = 13.sp)
    }
    for (path in folders) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(path.ifEmpty { "(whole storage)" }, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.removeLibraryFolder(kind, path) }) { Text("Remove") }
        }
    }
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = { add.launch(null) }, border = BorderStroke(1.dp, Line)) {
        Text("Add $label folder\u2026", color = White)
    }
}
