package com.you.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Video(
    val id: Long,
    val durationMs: Long,
    val uri: Uri,
    val displayName: String,
    val relativePath: String = "",
    val volume: String = "external_primary",
    val dateAdded: Long = 0L,
    /** A poster picture saved beside the video (tinyMediaManager: "<name>-poster.jpg"). */
    val poster: Uri? = null,
    /** A wide background picture saved beside the video (tinyMediaManager: "<name>-fanart.jpg"). */
    val fanart: Uri? = null,
) {
    /** What to show as this video's picture: its poster, or else a frame from the video. */
    val art: Uri get() = poster ?: uri
}

/** [keyName] is the name from the file, so hearts, covers and notes survive a new .nfo title. */
data class Movie(val title: String, val video: Video, val plot: String = "", val keyName: String = title) {
    val key: String get() = "movie:${keyName.lowercase()}|${video.id}"
    /** The wide picture for the big hero: fanart, or else the poster/frame. */
    val heroArt: Uri get() = video.fanart ?: video.art
}

data class Episode(val number: Int, val title: String, val video: Video, val plot: String = "")

data class Season(val number: Int, val episodes: List<Episode>, val poster: Uri? = null, val plot: String = "") {
    val label: String get() = if (number == 0) "Specials" else "Season $number"
}

data class Show(
    val title: String, val seasons: List<Season>, val poster: Uri? = null,
    val fanart: Uri? = null, val plot: String = "", val keyName: String = title,
) {
    val key: String get() = "show:${keyName.lowercase()}"
    val heroArt: Uri? get() = fanart ?: art
    /** The show's poster, or else a frame from its first episode. */
    val art: Uri? get() = poster ?: seasons.firstOrNull()?.episodes?.firstOrNull()?.video?.uri
    /** A season's own poster, or else the show's. */
    fun seasonArt(season: Season): Uri? = season.poster ?: art
    fun seasonKey(number: Int) = "season:${keyName.lowercase()}|$number"
}

class VideoLibrary(val movies: List<Movie>, val shows: List<Show>) {
    /** Plots from tinyMediaManager .nfo files, by movie/show/season key. */
    val plots: Map<String, String> by lazy {
        val out = HashMap<String, String>()
        movies.forEach { if (it.plot.isNotBlank()) out[it.key] = it.plot }
        shows.forEach { sh ->
            if (sh.plot.isNotBlank()) out[sh.key] = sh.plot
            sh.seasons.forEach { if (it.plot.isNotBlank()) out[sh.seasonKey(it.number)] = it.plot }
        }
        out
    }
}

// ---- names ---------------------------------------------------------------------------

private val JUNK = Regex(
    "(?i)\\b(480p|576p|720p|1080p|2160p|4k|x264|x265|h[ .]?26[45]|hevc|web[- ]?dl|webrip|bluray|brrip|bdrip|hdtv|dvdrip|aac|ac3|dts|remux)\\b.*$"
)
private val SXE = Regex("(?i)(?:^|[^a-z0-9])s(\\d{1,4})[ ._-]*e(\\d{1,4})(?:[^0-9]|$)")
private val NXM = Regex("(?i)(?:^|[^a-z0-9])(\\d{1,2})x(\\d{2,3})(?:[^0-9]|$)")
private val SEASON_FOLDER = Regex("(?i)^(?:(?:season|series)[ ._-]*(\\d{1,2})(?![0-9]).*|s[ ._-]*(\\d{1,2}))$")
private val LEADING_NUMBER = Regex("^(\\d{1,3})(?=[ ._-]|$)[ ._-]*(.*)$")
private val GENERIC_FOLDERS = setOf(
    "movies", "movie", "tv", "tv shows", "shows", "series", "video", "videos",
    "download", "downloads", "media", "storage", "sdcard",
)
private val SKIP_PATHS = listOf("dcim/", "pictures/", "android/", "whatsapp", "telegram", "snapchat", "recordings", "screenrecord")

/** "Billy.and.Mandy.2001.1080p.x264" -> "Billy and Mandy" */
fun cleanName(raw: String): String {
    var s = JUNK.replace(raw, " ")
    s = s.replace(Regex("\\[[^\\]]*]"), " ").replace(Regex("\\((?:19|20)\\d{2}\\)"), " ")
    s = s.replace('.', ' ').replace('_', ' ')
    return s.replace(Regex("\\s+"), " ").trim(' ', '-')
}

private fun seasonNumber(folder: String): Int? {
    if (folder.equals("specials", true) || folder.equals("special", true)) return 0
    val m = SEASON_FOLDER.find(folder.trim()) ?: return null
    return (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
}

private class Parsed(val show: String, val season: Int, val number: Int, val title: String)

private fun showFromFolders(folders: List<String>, seasonIndex: Int): String {
    val candidate = when {
        seasonIndex > 0 -> folders[seasonIndex - 1]
        else -> folders.lastOrNull { it.lowercase() !in GENERIC_FOLDERS && seasonNumber(it) == null }
    }
    return cleanName(candidate ?: "").ifBlank { "Unknown show" }
}

private fun parseEpisode(base: String, folders: List<String>): Parsed? {
    val seasonIndex = folders.indexOfLast { seasonNumber(it) != null }
    val match = SXE.find(base) ?: NXM.find(base)
    if (match != null) {
        val before = cleanName(base.substring(0, match.range.first))
        val after = cleanName(base.substring(minOf(match.range.last + 1, base.length)))
        return Parsed(
            show = before.ifBlank { showFromFolders(folders, seasonIndex) },
            season = match.groupValues[1].toInt(),
            number = match.groupValues[2].toInt(),
            title = after,
        )
    }
    if (seasonIndex >= 0) {                              // "Season 1/03 - Title.mkv"
        val lead = LEADING_NUMBER.find(base)
        if (lead != null) {
            return Parsed(
                show = showFromFolders(folders, seasonIndex),
                season = seasonNumber(folders[seasonIndex]) ?: 1,
                number = lead.groupValues[1].toInt(),
                title = cleanName(lead.groupValues[2]),
            )
        }
    }
    return null
}

// ---- posters saved beside the videos (tinyMediaManager, Kodi, Jellyfin) ---------------

private val IMAGE_EXTS = listOf("jpg", "jpeg", "png", "webp")

/** Every picture on the phone, by "folder/name" in lower case, without the extension. */
private class Pictures(private val byName: Map<String, Uri>) {
    fun find(folder: String, vararg bases: String): Uri? {
        val dir = folder.trim('/').lowercase()
        for (base in bases) byName["$dir/${base.lowercase()}"]?.let { return it }
        return null
    }

    fun has(folder: String, vararg bases: String) = find(folder, *bases) != null

    companion object {
        fun load(context: Context): Pictures {
            val out = HashMap<String, Uri>()
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
            )
            try {
                context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                    while (c.moveToNext()) {
                        val name = c.getString(iName) ?: continue
                        if (name.substringAfterLast('.', "").lowercase() !in IMAGE_EXTS) continue
                        val path = (c.getString(iPath) ?: "").trim('/').lowercase()
                        if (SKIP_PATHS.any { path.contains(it) }) continue
                        val key = "$path/${name.substringBeforeLast('.').lowercase()}"
                        out.putIfAbsent(key, ContentUris.withAppendedId(collection, c.getLong(iId)))
                    }
                }
            } catch (_: Exception) {
                // No picture access: posters fall back to a frame from the video.
            }
            return Pictures(out)
        }
    }
}

private fun parentOf(path: String): String = path.trim('/').substringBeforeLast('/', "")

private fun seasonPosterNames(number: Int): Array<String> =
    if (number == 0) arrayOf("season-specials-poster", "season00-poster")
    else arrayOf("season%02d-poster".format(number), "season$number-poster")

/**
 * Reads every video on the phone. File names like S01E02 or 01x02 become TV episodes, the rest are movies.
 * Titles and plots come from tinyMediaManager .nfo files when [details] can see them.
 */
fun loadVideos(context: Context, details: NfoReader? = null): VideoLibrary {
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.Video.Media.VOLUME_NAME,
        MediaStore.Video.Media.DATE_ADDED,
    )
    val photoIndex = Pictures.load(context)
    // Posters come from the phone's photo index, or else from the folder chosen in Settings
    // (that one works even when You was only given access to some photos).
    val pictures = object {
        fun find(volume: String, folder: String, vararg bases: String): Uri? =
            photoIndex.find(folder, *bases) ?: details?.image(volume, folder, *bases)
        fun has(volume: String, folder: String, vararg bases: String) = find(volume, folder, *bases) != null
    }
    val movieFolders = LibraryFolders.get(context, LibraryFolders.MOVIES)
    val tvFolders = LibraryFolders.get(context, LibraryFolders.TV)
    val movies = ArrayList<Movie>()
    val episodes = ArrayList<Triple<Parsed, Video, Int>>()
    try {
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            val iVolume = c.getColumnIndexOrThrow(MediaStore.Video.Media.VOLUME_NAME)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val name = c.getString(iName) ?: continue
                val duration = c.getLong(iDuration)
                val path = c.getString(iPath) ?: ""
                if (SKIP_PATHS.any { path.lowercase().contains(it) }) continue
                val base = name.substringBeforeLast('.')
                val folders = path.trim('/').split('/').filter { it.isNotBlank() }
                val video = Video(id, duration, ContentUris.withAppendedId(collection, id), name,
                    path, c.getString(iVolume) ?: "external_primary", c.getLong(iAdded))
                val parsed = parseEpisode(base, folders)
                val volume = video.volume
                if (parsed != null) {
                    if (!LibraryFolders.allows(tvFolders, path)) continue
                    val own = pictures.find(volume, path, "$base-poster", "$base-thumb", base)
                    if (duration >= 60_000) episodes.add(Triple(parsed, video.copy(poster = own), 0))
                } else if (duration >= 5 * 60_000) {
                    if (!LibraryFolders.allows(movieFolders, path)) continue
                    // One folder can hold several films, so "<name>-poster.jpg" comes first; plain
                    // poster.jpg is only used when the folder has no per-film picture.
                    val own = pictures.find(volume, path, "$base-poster", base, "poster", "folder", "cover", "movie-poster")
                    val fan = pictures.find(volume, path, "$base-fanart", "fanart")
                    val nfo = details?.read(volume, path, base) ?: details?.read(volume, path, "movie")
                    val name = cleanName(base).ifBlank { base }
                    movies.add(Movie(nfo?.titleWithYear() ?: name, video.copy(poster = own, fanart = fan),
                        nfo?.plot.orEmpty(), keyName = name))
                }
            }
        }
    } catch (_: SecurityException) {
        return VideoLibrary(emptyList(), emptyList())
    }

    // The show folder holds tvshow.nfo, poster.jpg and season01-poster.jpg. Episodes sit in it,
    // in "Season N (years)", or deeper; walk up until the show's .nfo or poster turns up.
    val showDirs = HashMap<String, String?>()
    fun showDirOf(v: Video): String? = showDirs.getOrPut(v.volume + ":" + v.relativePath) {
        var dir = v.relativePath.trim('/')
        repeat(4) {
            if (dir.isEmpty()) return@getOrPut null
            if (details?.read(v.volume, dir, "tvshow") != null ||
                pictures.has(v.volume, dir, "poster", "fanart")) return@getOrPut dir
            dir = parentOf(dir)
        }
        null
    }

    val shows = episodes
        .groupBy { (p, v, _) -> showDirOf(v)?.let { "dir:${v.volume}:${it.lowercase()}" } ?: p.show.lowercase() }
        .values
        .map { group ->
            // The name most episodes agree on keeps hearts and notes stable.
            val title = group.groupingBy { it.first.show }.eachCount().maxByOrNull { it.value }!!.key
            val volume = group.first().second.volume
            val firstPath = group.first().second.relativePath.trim('/')
            val showDir = showDirOf(group.first().second)
                ?: if (seasonNumber(firstPath.substringAfterLast('/')) != null) parentOf(firstPath) else firstPath
            val showNfo = details?.read(volume, showDir, "tvshow")
            val seasons = group
                .groupBy { it.first.season }
                .map { (number, items) ->
                    val seasonDir = items.first().second.relativePath
                    val names = seasonPosterNames(number)
                    Season(
                        number,
                        items.map { (p, v, _) ->
                            val ep = details?.read(v.volume, v.relativePath, v.displayName.substringBeforeLast('.'))
                            Episode(p.number, ep?.title?.ifBlank { null } ?: p.title.ifBlank { "Episode ${p.number}" },
                                v, ep?.plot.orEmpty())
                        }.sortedWith(compareBy({ it.number }, { sortKey(it.title) })),
                        pictures.find(volume, showDir, *names) ?: pictures.find(volume, seasonDir, *names),
                        details?.read(volume, seasonDir, "season")?.plot.orEmpty(),
                    )
                }
                .sortedBy { it.number }
            Show(showNfo?.title?.ifBlank { null } ?: title, seasons,
                pictures.find(volume, showDir, "poster", "folder", "cover", "show"),
                pictures.find(volume, showDir, "fanart"), showNfo?.plot.orEmpty(), keyName = title)
        }
        .sortedBy { sortKey(it.title) }

    return VideoLibrary(movies.sortedBy { sortKey(it.title) }, shows)
}

// ---- tinyMediaManager .nfo files -------------------------------------------------------

class NfoInfo(val title: String, val year: String, val plot: String) {
    fun titleWithYear(): String? = when {
        title.isBlank() -> null
        year.isNotBlank() && !title.contains(year) -> "$title ($year)"
        else -> title
    }
}

private fun tag(text: String, name: String): String {
    val m = Regex("(?is)<$name>(.*?)</$name>").find(text) ?: return ""
    val v = m.groupValues[1].replace(Regex("(?s)<!\\[CDATA\\[(.*?)]]>"), "$1")
    return v.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").trim()
}

/** Title, year and plot from .nfo text. Broken files give null. */
fun parseNfo(text: String): NfoInfo? {
    if (!text.contains('<')) return null
    val info = NfoInfo(tag(text, "title"), tag(text, "year").ifBlank { tag(text, "premiered").take(4) },
        tag(text, "plot").ifBlank { tag(text, "outline") }.ifBlank { tag(text, "biography") }
            .ifBlank { tag(text, "review") }.ifBlank { tag(text, "overview") })
    return if (info.title.isBlank() && info.plot.isBlank()) null else info
}

/** Finds and reads .nfo files in the folder you chose in Settings. */
interface NfoReader {
    /** [folder] is a path like "You/TV/Show/", [base] the file name without ".nfo". */
    fun read(volume: String, folder: String, base: String): NfoInfo?
    /** The first picture "<base>.jpg/.png/..." in [folder], trying [bases] in order. */
    fun image(volume: String, folder: String, vararg bases: String): Uri?
}
