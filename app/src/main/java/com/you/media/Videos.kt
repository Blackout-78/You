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
)

data class Movie(val title: String, val video: Video) {
    val key: String get() = "movie:${title.lowercase()}|${video.id}"
}

data class Episode(val number: Int, val title: String, val video: Video)

data class Season(val number: Int, val episodes: List<Episode>) {
    val label: String get() = if (number == 0) "Specials" else "Season $number"
}

data class Show(val title: String, val seasons: List<Season>) {
    val key: String get() = "show:${title.lowercase()}"
    fun seasonKey(number: Int) = "season:${title.lowercase()}|$number"
}

class VideoLibrary(val movies: List<Movie>, val shows: List<Show>)

// ---- names ---------------------------------------------------------------------------

private val JUNK = Regex(
    "(?i)\\b(480p|576p|720p|1080p|2160p|4k|x264|x265|h[ .]?26[45]|hevc|web[- ]?dl|webrip|bluray|brrip|bdrip|hdtv|dvdrip|aac|ac3|dts|remux)\\b.*$"
)
private val SXE = Regex("(?i)(?:^|[^a-z0-9])s(\\d{1,2})[ ._-]*e(\\d{1,3})(?:[^0-9]|$)")
private val NXM = Regex("(?i)(?:^|[^a-z0-9])(\\d{1,2})x(\\d{2,3})(?:[^0-9]|$)")
private val SEASON_FOLDER = Regex("(?i)^(?:season|series|s)[ ._-]*(\\d{1,2})$")
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
    return SEASON_FOLDER.find(folder.trim())?.groupValues?.get(1)?.toIntOrNull()
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

/** Reads every video on the phone. File names like S01E02 or 1x02 become TV episodes, the rest are movies. */
fun loadVideos(context: Context): VideoLibrary {
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.Video.Media.VOLUME_NAME,
        MediaStore.Video.Media.DATE_ADDED,
    )
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
                if (parsed != null) {
                    if (duration >= 60_000) episodes.add(Triple(parsed, video, 0))
                } else if (duration >= 5 * 60_000) {
                    movies.add(Movie(cleanName(base).ifBlank { base }, video))
                }
            }
        }
    } catch (_: SecurityException) {
        return VideoLibrary(emptyList(), emptyList())
    }

    val shows = episodes
        .groupBy { it.first.show.lowercase() }
        .values
        .map { group ->
            val title = group.first().first.show
            val seasons = group
                .groupBy { it.first.season }
                .map { (number, items) ->
                    Season(
                        number,
                        items.map { (p, v, _) ->
                            Episode(p.number, p.title.ifBlank { "Episode ${p.number}" }, v)
                        }.sortedWith(compareBy({ it.number }, { sortKey(it.title) })),
                    )
                }
                .sortedBy { it.number }
            Show(title, seasons)
        }
        .sortedBy { sortKey(it.title) }

    return VideoLibrary(movies.sortedBy { sortKey(it.title) }, shows)
}
