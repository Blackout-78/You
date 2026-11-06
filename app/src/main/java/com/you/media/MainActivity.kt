package com.you.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Every time you come back to the app, look for new files on the phone. */
    override fun onResume() {
        super.onResume()
        if (hasAudioAccess(this)) vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) vm.restoreTab()
        setContent {
            YouTheme {
                Box(Modifier.fillMaxSize().background(YouColors.solidDark)) {
                    BackgroundPicture()
                    Surface(Modifier.fillMaxSize(), color = Black) { App(vm) }
                }
            }
        }
    }
}

private fun audioPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasVideoAccess(context: Context) =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

private fun hasAudioAccess(context: Context) =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

@Composable
fun App(vm: AppViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioAccess(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudioAccess(context)
    }

    var videoAccess by remember { mutableStateOf(hasVideoAccess(context)) }
    val askVideo = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        videoAccess = hasVideoAccess(context)
        if (videoAccess) vm.refresh()
    }

    if (!vm.welcomed) {
        WelcomeScreen(vm)
        return
    }

    LaunchedEffect(granted) {
        if (granted) {
            vm.refresh()
            if (vm.lyricsIndex.needsRescan) vm.rescanFolder()
        }
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("You", color = White, fontSize = 28.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "To show your music, the app needs to read the music on this phone.\n" +
                    "It never connects to the internet and never shares anything.",
                color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = {
                val wanted = if (Build.VERSION.SDK_INT >= 33)
                    arrayOf(audioPermission(), Manifest.permission.POST_NOTIFICATIONS)
                else arrayOf(audioPermission())
                ask.launch(wanted)
            }) { Text("Allow access to music", color = White) }
        }
        return
    }

    val screen = vm.stack.last()
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    BackHandler(enabled = vm.showLyrics) { vm.showLyrics = false }
    BackHandler(enabled = vm.showVideo) { vm.showVideo = false }
    BackHandler(enabled = vm.fullscreen) { vm.fullscreen = false }

    // Full screen: turn the phone sideways and hide the bars.
    val activity = context as? android.app.Activity
    LaunchedEffect(vm.fullscreen) {
        activity?.let { a ->
            val controller = androidx.core.view.WindowCompat.getInsetsController(a.window, a.window.decorView)
            if (vm.fullscreen) {
                a.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                a.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    // Leaving the picture or stopping the video leaves full screen.
    LaunchedEffect(vm.showVideo) { if (!vm.showVideo) vm.fullscreen = false }

    // Remember where you stopped in a video.
    LaunchedEffect(vm.player.mediaId, vm.player.isPlaying) {
        while (vm.player.isVideo) {
            kotlinx.coroutines.delay(5000)
            vm.saveVideoPosition()
        }
    }
    LaunchedEffect(vm.player.mediaId, vm.library.tracksById.size) { vm.loadLyricsFor(vm.player.mediaId) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            if (vm.showVideo && vm.player.isVideo) {
                VideoScreen(vm)
            } else if (vm.showLyrics && vm.player.mediaId != null && !vm.player.isVideo) {
                LyricsScreen(vm)
            } else when (screen) {
                Screen.Settings -> SettingsScreen(vm)
                Screen.Search -> SearchScreen(vm)
                Screen.Playlists -> PlaylistsScreen(vm)
                is Screen.CollectionPage -> CollectionScreen(vm, screen.folder)
                is Screen.PlaylistPage -> PlaylistScreen(vm, screen.name)
                Screen.Movies -> MoviesScreen(vm, videoAccess) { askVideo.launch(Manifest.permission.READ_MEDIA_VIDEO) }
                Screen.Shows -> ShowsScreen(vm, videoAccess) { askVideo.launch(Manifest.permission.READ_MEDIA_VIDEO) }
                is Screen.MoviePage -> MovieScreen(vm, screen.id)
                is Screen.ShowPage -> ShowScreen(vm, screen.title)
                is Screen.SeasonPage -> SeasonScreen(vm, screen.show, screen.season)
                Screen.Artists -> ArtistsScreen(vm)
                Screen.Favorites -> FavoritesScreen(vm)
                is Screen.ArtistPage -> ArtistScreen(vm, screen.name)
                is Screen.AlbumPage -> AlbumScreen(vm, screen.artist, screen.album)
            }
        }
        if (!vm.fullscreen) {
            if (vm.player.mediaId != null) MiniPlayer(vm)
            TabBar(vm)
        }
    }
}

@Composable
private fun TabBar(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.material3.HorizontalDivider(color = Line)
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(52.dp)) {
            for ((tab, label) in listOf(Tab.Movies to "Movies", Tab.Shows to "TV Shows", Tab.Music to "Music", Tab.Settings to "Settings")) {
                val on = vm.tab == tab
                Box(
                    Modifier.weight(1f).fillMaxSize().clickable { vm.selectTab(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) White else Muted, fontSize = 14.sp,
                        fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else null)
                }
            }
        }
    }
}
