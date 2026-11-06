package com.you.media

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { Movies, Shows, Music, Settings }

sealed interface Screen {
    data object Settings : Screen
    data object Search : Screen
    data object Playlists : Screen
    data class CollectionPage(val folder: String) : Screen
    data class PlaylistPage(val name: String) : Screen
    data object Movies : Screen
    data object Shows : Screen
    data class MoviePage(val id: Long) : Screen
    data class ShowPage(val title: String) : Screen
    data class SeasonPage(val show: String, val season: Int) : Screen
    data object Artists : Screen
    data object Favorites : Screen
    data class ArtistPage(val name: String) : Screen
    data class AlbumPage(val artist: String, val album: String) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("you_ui", android.content.Context.MODE_PRIVATE)

    /** Which look each section uses: "Browse" (big picture and rows) or "Grid" (plain grid of covers). */
    val themes = androidx.compose.runtime.mutableStateMapOf(
        "music" to (prefs.getString("theme_music", "Browse") ?: "Browse"),
        "movies" to (prefs.getString("theme_movies", "Browse") ?: "Browse"),
        "shows" to (prefs.getString("theme_shows", "Browse") ?: "Browse"),
    )

    fun isGrid(section: String) = themes[section] == "Grid"

    fun setTheme(section: String, theme: String) {
        themes[section] = theme
        prefs.edit().putString("theme_$section", theme).apply()
    }

    val store = Store(app)
    val player = PlayerHolder(app)

    // ---- Your colors and profile ----
    /** Your name and picture. The picture is a private copy kept inside the app. */
    var profileName by mutableStateOf(prefs.getString("profile_name", "") ?: "")
    var profilePicture by mutableStateOf(
        prefs.getString("profile_picture", null)?.takeIf { java.io.File(it).exists() })
    var welcomed by mutableStateOf(prefs.getBoolean("welcomed", false))

    init {
        YouColors.load(prefs)
        // Which part of the app is showing decides whose colors are used (a section can have its own).
        YouColors.sectionProvider = {
            when (tab) { Tab.Movies -> "movies"; Tab.Shows -> "shows"; Tab.Music -> "music"; else -> "" }
        }
    }

    fun paletteFor(scope: String): Palette = if (scope == "all") YouColors.global else (YouColors.own[scope] ?: YouColors.global)
    fun hasOwnLook(scope: String) = YouColors.own.containsKey(scope)

    fun savePalette(scope: String, p: Palette) {
        if (scope == "all") YouColors.global = p else YouColors.own[scope] = p
        prefs.edit().putString("palette_$scope", p.toJson().toString()).apply()
    }

    fun useOwnLook(scope: String, on: Boolean) {
        if (on) savePalette(scope, YouColors.global)
        else {
            YouColors.own.remove(scope)
            prefs.edit().remove("palette_$scope").apply()
        }
    }

    fun resetLook(scope: String) {
        if (scope == "all") savePalette("all", Palette.Default) else useOwnLook(scope, false)
    }

    fun setBackground(scope: String, uri: Uri?) {
        val ctx = getApplication<Application>()
        val current = paletteFor(scope)
        if (uri == null) {
            current.picture?.let { java.io.File(it).delete() }
            savePalette(scope, current.copy(picture = null))
            return
        }
        val saved = importPicture(ctx, uri, "bg_${scope}_${System.currentTimeMillis()}.jpg", 1600, false) ?: return
        current.picture?.let { java.io.File(it).delete() }
        savePalette(scope, current.copy(picture = saved))
    }

    fun setProfilePicture(uri: Uri?) {
        val ctx = getApplication<Application>()
        profilePicture?.let { java.io.File(it).delete() }
        profilePicture = if (uri == null) null
        else importPicture(ctx, uri, "profile_${System.currentTimeMillis()}.jpg", 512, true)
        prefs.edit().putString("profile_picture", profilePicture).apply()
    }

    fun setProfileName(name: String) {
        profileName = name.take(30)
        prefs.edit().putString("profile_name", profileName).apply()
    }

    fun finishWelcome() {
        welcomed = true
        prefs.edit().putBoolean("welcomed", true).apply()
    }

    var library by mutableStateOf(Library(emptyList(), emptyMap()))
    var videos by mutableStateOf(VideoLibrary(emptyList(), emptyList()))
    var tab by mutableStateOf(Tab.Music)
    var showVideo by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var subtitlesOn by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var loadedOnce by mutableStateOf(false)
    val stack = mutableStateListOf<Screen>(Screen.Artists)

    val lyricsIndex = LyricsIndex(app)
    var lyrics by mutableStateOf<Lyrics?>(null)
    var lyricsLoading by mutableStateOf(false)
    var lyricsIndexing by mutableStateOf(false)
    var lyricsFolderSet by mutableStateOf(lyricsIndex.treeUri != null)
    var showLyrics by mutableStateOf(false)
    private var lyricsJob: Job? = null

    init {
        player.connect()
    }

    fun refresh() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            library = withContext(Dispatchers.IO) { loadLibrary(getApplication<Application>()) }
            videos = withContext(Dispatchers.IO) { loadVideos(getApplication<Application>()) }
            loading = false
            loadedOnce = true
        }
    }

    /** Looks for lyrics for the song that is playing now. */
    fun loadLyricsFor(mediaId: String?) {
        lyricsJob?.cancel()
        lyrics = null
        val track = mediaId?.toLongOrNull()?.let { library.tracksById[it] }
        if (track == null) {
            lyricsLoading = false
            return
        }
        lyricsLoading = true
        lyricsJob = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { findLyrics(getApplication<Application>(), lyricsIndex, track) }
            lyrics = found
            lyricsLoading = false
        }
    }

    /** You picked the folder that holds your music (and your .lrc / .txt files). */
    /** Wipes everything the app saved. Your music and videos are not touched. */
    fun clearMyData() {
        store.clearAll()
        lyricsIndex.clear()
        lyricsFolderSet = false
        lyrics = null
        for (scope in listOf("all", "music", "movies", "shows")) {
            paletteFor(scope).picture?.let { java.io.File(it).delete() }
        }
        profilePicture?.let { java.io.File(it).delete() }
        profilePicture = null
        profileName = ""
        YouColors.reset(prefs)
        prefs.edit().clear().putBoolean("welcomed", true).apply()
        themes["music"] = "Browse"; themes["movies"] = "Browse"; themes["shows"] = "Browse"
    }

    /** Stop: remember where a video was, end playback and remove the notification card. */
    fun stopPlayback() {
        saveVideoPosition()
        player.stopAndClear()
        showVideo = false
        fullscreen = false
        showLyrics = false
    }

    fun toggleSubtitles() {
        subtitlesOn = !subtitlesOn
        player.setSubtitlesOn(subtitlesOn)
    }

    /** Looks through the chosen folder again for new lyric and subtitle files. */
    fun rescanFolder() {
        lyricsIndex.treeUri?.let { setLyricsFolder(Uri.parse(it)) }
    }

    fun setLyricsFolder(uri: Uri?) {
        if (uri == null) return
        try {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        viewModelScope.launch {
            lyricsIndexing = true
            withContext(Dispatchers.IO) { lyricsIndex.rebuild(uri) }
            lyricsFolderSet = true
            lyricsIndexing = false
            loadLyricsFor(player.mediaId)
        }
    }

    /** Opens on the tab you used last. */
    fun restoreTab() {
        val saved = runCatching { Tab.valueOf(prefs.getString("tab", "Music") ?: "Music") }.getOrDefault(Tab.Music)
        if (saved != Tab.Music) selectTab(saved)
    }

    fun selectTab(newTab: Tab) {
        prefs.edit().putString("tab", newTab.name).apply()
        tab = newTab
        showLyrics = false
        showVideo = false
        fullscreen = false
        stack.clear()
        stack.add(
            when (newTab) {
                Tab.Movies -> Screen.Movies
                Tab.Shows -> Screen.Shows
                Tab.Music -> Screen.Artists
                Tab.Settings -> Screen.Settings
            }
        )
    }

    /** Starts a video (and the ones after it) and opens the picture. */
    fun playVideos(items: List<Pair<Video, String>>, index: Int, subtitle: String) {
        val first = items.getOrNull(index) ?: return
        player.playVideos(
            videos = items.map { it.first },
            index = index,
            subtitle = subtitle,
            titles = items.map { it.second },
            startMs = store.resumeFor("v${first.first.id}"),
            subtitles = items.map { lyricsIndex.subtitleFor(it.first) },
        )
        player.setSubtitlesOn(subtitlesOn)
        showLyrics = false
        showVideo = true
    }

    /** Called every few seconds while a video plays so you can pick up where you stopped. */
    fun saveVideoPosition() {
        val id = player.mediaId ?: return
        if (!id.startsWith("v")) return
        store.saveResume(id, player.position(), player.durationMs)
    }

    fun open(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    override fun onCleared() {
        player.release()
    }
}
