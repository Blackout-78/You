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
        "music" to (prefs.getString("theme_music", "Candy") ?: "Candy"),
        "movies" to (prefs.getString("theme_movies", "Browse") ?: "Browse"),
        "shows" to (prefs.getString("theme_shows", "Browse") ?: "Browse"),
    )

    fun isGrid(section: String) = themes[section] == "Grid"

    init {
        // One time: the new Black Candy style becomes the Music look. Browse and Grid stay in Settings > Theme.
        if (!prefs.getBoolean("candy_applied", false)) {
            themes["music"] = "Candy"
            prefs.edit().putString("theme_music", "Candy").putBoolean("candy_applied", true).apply()
        }
        YouColors.load(prefs)
        // Which part of the app is showing decides whose colors are used (a section can have its own).
        YouColors.sectionProvider = {
            when (tab) { Tab.Movies -> "movies"; Tab.Shows -> "shows"; Tab.Music -> "music"; else -> "" }
        }
    }

    /** Remembered sort for a Music chip: the field and whether it runs A to Z. */
    fun musicSort(chip: String, default: String): Pair<String, Boolean> {
        val v = sortState[chip] ?: prefs.getString("sort_$chip", null) ?: "$default|A"
        val (f, d) = v.split("|").let { it[0] to (it.getOrNull(1) != "Z") }
        return f to d
    }

    private val sortState = androidx.compose.runtime.mutableStateMapOf<String, String>()

    fun setMusicSort(chip: String, field: String, asc: Boolean) {
        val v = "$field|${if (asc) "A" else "Z"}"
        sortState[chip] = v
        prefs.edit().putString("sort_$chip", v).apply()
    }

    fun setTheme(section: String, theme: String) {
        themes[section] = theme
        prefs.edit().putString("theme_$section", theme).apply()
    }

    // ---- Your colors ----
    var welcomed by mutableStateOf(prefs.getBoolean("welcomed", false))

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

    fun finishWelcome() {
        welcomed = true
        prefs.edit().putBoolean("welcomed", true).apply()
    }

    var profileName by mutableStateOf(prefs.getString("profile_name", "") ?: "")
    var profilePicture by mutableStateOf(prefs.getString("profile_picture", null)?.takeIf { java.io.File(it).exists() })

    fun setProfilePicture(uri: Uri?) {
        profilePicture?.let { java.io.File(it).delete() }
        profilePicture = if (uri == null) null
        else importPicture(getApplication(), uri, "profile_${System.currentTimeMillis()}.jpg", 512, true)
        prefs.edit().putString("profile_picture", profilePicture).apply()
    }

    fun changeProfileName(name: String) {
        profileName = name.take(30)
        prefs.edit().putString("profile_name", profileName).apply()
    }

    val store = Store(app)
    val player = PlayerHolder(app)

    var library by mutableStateOf(Library(emptyList(), emptyMap()))
    var videos by mutableStateOf(VideoLibrary(emptyList(), emptyList()))
    var tab by mutableStateOf(Tab.Music)
    var showVideo by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var subtitlesOn by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var loadedOnce by mutableStateOf(false)
    val stack = mutableStateListOf<Screen>(Screen.Artists)

    /** Library folders per kind (music / movies / tv); empty means the whole phone. */
    val libraryFolders = androidx.compose.runtime.mutableStateMapOf(
        *listOf(LibraryFolders.MUSIC, LibraryFolders.MOVIES, LibraryFolders.TV)
            .map { it to LibraryFolders.get(app, it) }.toTypedArray()
    )

    fun addLibraryFolder(kind: String, uri: Uri?) {
        if (uri == null) return
        val path = LibraryFolders.pathOf(uri)
        val list = libraryFolders[kind].orEmpty()
        if (list.any { it.equals(path, true) }) return
        libraryFolders[kind] = list + path
        LibraryFolders.set(getApplication(), kind, libraryFolders[kind].orEmpty())
        refresh()
    }

    fun removeLibraryFolder(kind: String, path: String) {
        libraryFolders[kind] = libraryFolders[kind].orEmpty() - path
        LibraryFolders.set(getApplication(), kind, libraryFolders[kind].orEmpty())
        refresh()
    }

    val lyricsIndex = LyricsIndex(app)
    var lyrics by mutableStateOf<Lyrics?>(null)
    var lyricsLoading by mutableStateOf(false)
    var lyricsIndexing by mutableStateOf(false)
    var lyricsFolderSet by mutableStateOf(lyricsIndex.treeUri != null)
    var showLyrics by mutableStateOf(false)
    var showNowPlaying by mutableStateOf(false)

    // ---- sleep timer: pauses the music after the time you choose ----
    var sleepMinutes by mutableStateOf(0)
    private var sleepJob: Job? = null

    fun cycleSleep() {
        val steps = listOf(0, 15, 30, 60, 90, -1)
        setSleep(steps[(steps.indexOf(sleepMinutes) + 1).mod(steps.size)])
    }

    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepMinutes = minutes
        if (minutes == -1) {          // end of this song: wait until the song that is playing now finishes
            val startId = player.mediaId
            sleepJob = viewModelScope.launch {
                while (player.mediaId == startId) {
                    val left = player.durationMs - player.position()
                    if (player.durationMs > 0 && left < 600) {
                        player.pause()
                        break
                    }
                    kotlinx.coroutines.delay(250)
                }
                sleepMinutes = 0
            }
        } else if (minutes > 0) {
            sleepJob = viewModelScope.launch {
                kotlinx.coroutines.delay(minutes * 60_000L)
                player.pause()
                sleepMinutes = 0
            }
        }
    }

    // ---- subtitle size for videos ----
    var subtitleSize by mutableStateOf(prefs.getString("sub_size", "Medium") ?: "Medium")

    fun cycleSubtitleSize() {
        val steps = subtitleFractions.keys.toList()
        subtitleSize = steps[(steps.indexOf(subtitleSize) + 1).mod(steps.size)]
        prefs.edit().putString("sub_size", subtitleSize).apply()
    }

    /** Called when a new song starts, so Home can show what you played lately. */
    fun recordPlayed(mediaId: String?) {
        val id = mediaId ?: return
        if (id.startsWith("v")) return
        id.toLongOrNull()?.let { store.played(it) }
    }
    private var lyricsJob: Job? = null

    init {
        player.connect()
    }

    fun refresh() {
        if (loading) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            loading = true
            try {
                // Show the saved copy of your music right away while the real scan runs.
                if (library.tracksById.isEmpty()) {
                    withContext(Dispatchers.IO) { loadLibraryCache(app) }?.let { library = it; loadedOnce = true }
                }
                // Music and videos are read at the same time, and each shows up as soon as it is ready.
                // If Android's media library fails (an SD card pulled out, say), what is on screen stays.
                val music = launch {
                    try {
                        val fresh = withContext(Dispatchers.IO) { loadLibrary(app) }
                        library = fresh
                        loadedOnce = true
                        withContext(Dispatchers.IO) { saveLibraryCache(app, fresh) }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        loadedOnce = true
                    }
                }
                val films = launch {
                    try {
                        videos = withContext(Dispatchers.IO) { loadVideos(app, lyricsIndex) }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                }
                music.join()
                films.join()
            } finally {
                loading = false
            }
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
    /** Wipes everything You saved. Your music and videos are not touched. */
    fun clearMyData() {
        store.clearAll()
        lyricsIndex.clear()
        lyricsFolderSet = false
        lyrics = null
        java.io.File(getApplication<Application>().filesDir, "library_cache.json").delete()
        for (scope in listOf("all", "music", "movies", "shows")) {
            paletteFor(scope).picture?.let { java.io.File(it).delete() }
        }
        YouColors.reset(prefs)
        prefs.edit().clear().putBoolean("welcomed", true).apply()
        libraryFolders.keys.toList().forEach { libraryFolders[it] = emptyList() }
        themes["music"] = "Candy"; themes["movies"] = "Browse"; themes["shows"] = "Browse"
        profilePicture?.let { java.io.File(it).delete() }
        profilePicture = null
        profileName = ""
        subtitleSize = "Medium"
    }

    /** Stop: remember where a video was, end playback and remove the notification card. */
    fun stopPlayback() {
        saveVideoPosition()
        player.stopAndClear()
        showVideo = false
        fullscreen = false
        showLyrics = false
        showNowPlaying = false
        setSleep(0)
    }

    fun toggleSubtitles() {
        subtitlesOn = !subtitlesOn
        player.setSubtitlesOn(subtitlesOn)
    }

    /** Counts up each time the chosen folder is read again, so what it holds refreshes on screen. */
    var folderTick by mutableStateOf(0)

    /** Your own description first, else the plot from tinyMediaManager, else artist.nfo / album.nfo. */
    fun describe(key: String): String {
        store.notes[key]?.let { return it }
        videos.plots[key]?.let { return it }
        return folderNote(key)
    }

    /** artist.nfo or album.nfo in the music folder (read only), or "". */
    private fun folderNote(key: String): String {
        if (folderTick < 0) return ""
        val isArtist = key.startsWith("artist:")
        if (!isArtist && !key.startsWith("album:")) return ""
        val dir: Pair<String, String>? = if (isArtist) {
            val name = key.removePrefix("artist:")
            library.artists.firstOrNull { it.name == name }?.let { artistDirOf(it) }
        } else {
            val parts = key.removePrefix("album:").split("||", limit = 2)
            val title = parts.getOrElse(1) { "" }
            library.artists.firstOrNull { it.name == parts[0] }
                ?.albums?.firstOrNull { it.title == title }?.let { albumDirOf(it) }
        }
        if (dir == null) return ""
        return lyricsIndex.read(dir.first, dir.second, if (isArtist) "artist" else "album")?.plot.orEmpty()
    }

    private fun artistDirOf(artist: Artist): Pair<String, String>? {
        val track = artist.albums.firstOrNull()?.tracks?.firstOrNull() ?: return null
        return track.volume to artistFolder(track.relativePath, libraryFolders[LibraryFolders.MUSIC].orEmpty())
    }

    private fun albumDirOf(album: Album): Pair<String, String>? {
        val track = album.tracks.firstOrNull() ?: return null
        return track.volume to track.relativePath.trim('/')
    }

    /** The cover shown for an artist: the one you picked, else artist.jpg (or band, folder, photo) in its folder. */
    fun artistCover(artist: Artist): String? {
        store.covers[artist.key]?.let { return it }
        if (folderTick < 0) return null
        val dir = artistDirOf(artist) ?: return null
        return lyricsIndex.image(dir.first, dir.second, "artist", "band", "folder", "photo")?.toString()
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
            try {
                withContext(Dispatchers.IO) { lyricsIndex.rebuild(uri) }
                lyricsFolderSet = true
                lyricsIndexing = false
                folderTick++
                loadLyricsFor(player.mediaId)
                // New .nfo files change movie and show titles and plots.
                videos = withContext(Dispatchers.IO) { loadVideos(getApplication(), lyricsIndex) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                lyricsIndexing = false
            }
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
        showNowPlaying = false
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

/** How big the subtitles are, as a fraction of the picture height. */
val subtitleFractions = linkedMapOf("Small" to 0.04f, "Medium" to 0.0533f, "Large" to 0.07f, "Extra large" to 0.09f)
