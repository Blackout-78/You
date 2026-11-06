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
                "Browse has a big picture and rows. Grid is a plain grid of covers. Pick one for each section.",
                color = Muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            for ((key, label) in listOf("music" to "Music", "movies" to "Movies", "shows" to "TV Shows")) {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(label, color = Soft, fontSize = 15.sp, modifier = Modifier.width(96.dp))
                    for (theme in listOf("Browse", "Grid")) {
                        val on = vm.themes[key] == theme
                        androidx.compose.foundation.layout.Box(
                            Modifier.padding(end = 8.dp)
                                .background(if (on) White else Panel)
                                .clickable { vm.setTheme(key, theme) }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                theme, color = if (on) YouColors.solidDark else Soft, fontSize = 14.sp,
                                fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else null,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Library", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("The app looks for new files every time you open it.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.refresh() }, border = BorderStroke(1.dp, Line)) {
                Text(if (vm.loading) "Reading\u2026" else "Refresh library", color = White)
            }
            Spacer(Modifier.height(32.dp))
            Text("Lyrics and subtitles folder", color = White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (vm.lyricsFolderSet) "A folder is chosen. ${vm.lyricsIndex.fileCount} lyric files found."
                else "No folder chosen. Pick the folder that holds your music and .lrc files.",
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
