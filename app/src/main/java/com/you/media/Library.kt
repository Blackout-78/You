package com.you.media

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.net.Uri
import android.provider.MediaStore
import java.text.Normalizer

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val albumId: Long,
    val number: Int,
    val disc: Int,
    val durationMs: Long,
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val volume: String,
)

data class Album(val artist: String, val title: String, val albumId: Long, val tracks: List<Track>) {
    val key: String get() = "album:$artist||$title"
}

data class Artist(val name: String, val albums: List<Album>) {
    val key: String get() = "artist:$name"
}

class Library(val artists: List<Artist>, val tracksById: Map<Long, Track>)

/** A to Z sort key: ignores case, accents, leading punctuation and The / A / An. */
fun sortKey(text: String): String {
    var s = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
    s = s.trimStart { !it.isLetterOrDigit() }
    for (article in listOf("the ", "an ", "a ")) {
        if (s.startsWith(article)) {
            s = s.removePrefix(article).trimStart()
            break
        }
    }
    return s
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun clean(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }

private val audioExtensions = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma")

/**
 * Android's media library only knows files it has indexed. Songs copied over USB or by another
 * app can sit on the phone unindexed, so Refresh library never saw them. This finds audio files
 * in the chosen music folders (or anywhere on internal storage) that the library is missing and
 * asks Android to index them, waiting a short while for it to finish.
 */
private fun indexMissingSongs(context: Context, folders: List<String>) {
    try {
        val root = Environment.getExternalStorageDirectory()
        val known = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) known.add(((c.getString(0) ?: "") + (c.getString(1) ?: "")).lowercase())
        }
        val starts = if (folders.isEmpty() || folders.any { it.isBlank() }) listOf(root)
        else folders.map { java.io.File(root, it) }
        val missing = ArrayList<String>()
        for (start in starts) {
            start.walkTopDown()
                .onEnter { d -> !d.name.startsWith(".") && !(d.parentFile == root && d.name == "Android") }
                .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                .forEach { f ->
                    val rel = f.parentFile?.relativeTo(root)?.path?.let { "$it/" } ?: ""
                    if ((rel + f.name).lowercase() !in known) missing.add(f.absolutePath)
                }
            if (missing.size >= 5000) break
        }
        if (missing.isEmpty()) return
        val done = java.util.concurrent.CountDownLatch(missing.size)
        MediaScannerConnection.scanFile(context, missing.toTypedArray(), null) { _, _ -> done.countDown() }
        done.await(60, java.util.concurrent.TimeUnit.SECONDS)
    } catch (_: Exception) {
    }
}

/** Reads every song on the phone (internal storage and SD card) from Android's own media library. */
fun loadLibrary(context: Context): Library {
    indexMissingSongs(context, LibraryFolders.get(context, LibraryFolders.MUSIC))
    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM_ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.RELATIVE_PATH,
        MediaStore.Audio.Media.VOLUME_NAME,
    )
    val tracks = ArrayList<Track>()
    val folders = LibraryFolders.get(context, LibraryFolders.MUSIC)
    context.contentResolver.query(
        collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
    )?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val iAlbumArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
        val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val iPath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        val iVolume = c.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)
        while (c.moveToNext()) {
            val id = c.getLong(iId)
            val artist = clean(c.getString(iArtist))
            val group = clean(c.getString(iAlbumArtist)) ?: artist ?: "Unknown Artist"
            val raw = c.getInt(iTrack)
            if (!LibraryFolders.allows(folders, c.getString(iPath) ?: "")) continue
            tracks.add(
                Track(
                    id = id,
                    title = clean(c.getString(iTitle)) ?: "Unknown title",
                    artist = artist ?: group,
                    albumArtist = group,
                    album = clean(c.getString(iAlbum)) ?: "Unknown Album",
                    albumId = c.getLong(iAlbumId),
                    number = raw % 1000,
                    disc = if (raw >= 1000) raw / 1000 else 1,
                    durationMs = c.getLong(iDuration),
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = c.getString(iName) ?: "",
                    relativePath = c.getString(iPath) ?: "",
                    volume = c.getString(iVolume) ?: "external_primary",
                )
            )
        }
    }

    return buildLibrary(tracks)
}

/** Groups songs into artists and albums. */
fun buildLibrary(tracks: List<Track>): Library {
    val artists = tracks
        .groupBy { it.albumArtist.lowercase() }
        .values
        .map { artistTracks ->
            val name = artistTracks.first().albumArtist
            val albums = artistTracks
                .groupBy { it.album.lowercase() }
                .values
                .map { albumTracks ->
                    Album(
                        artist = name,
                        title = albumTracks.first().album,
                        albumId = albumTracks.first().albumId,
                        tracks = albumTracks.sortedWith(
                            compareBy<Track>({ it.disc }, { it.number }, { sortKey(it.title) })
                        ),
                    )
                }
                .sortedBy { sortKey(it.title) }
            Artist(name, albums)
        }
        .sortedBy { sortKey(it.name) }

    return Library(artists, tracks.associateBy { it.id })
}


// ---- a copy of the song list on the phone, so the app can show your music the instant it opens ----

private fun cacheFile(context: Context) = java.io.File(context.filesDir, "library_cache.json")

fun saveLibraryCache(context: Context, library: Library) {
    try {
        val arr = org.json.JSONArray()
        for (t in library.tracksById.values) {
            arr.put(org.json.JSONArray().put(t.id).put(t.title).put(t.artist).put(t.albumArtist).put(t.album)
                .put(t.albumId).put(t.number).put(t.disc).put(t.durationMs).put(t.displayName)
                .put(t.relativePath).put(t.volume))
        }
        val tmp = java.io.File(context.filesDir, "library_cache.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(cacheFile(context))
    } catch (_: Exception) {
    }
}

fun loadLibraryCache(context: Context): Library? {
    return try {
        val f = cacheFile(context)
        if (!f.exists()) return null
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val arr = org.json.JSONArray(f.readText())
        val tracks = ArrayList<Track>(arr.length())
        val folders = LibraryFolders.get(context, LibraryFolders.MUSIC)
        for (i in 0 until arr.length()) {
            val a = arr.getJSONArray(i)
            if (!LibraryFolders.allows(folders, a.getString(10))) continue
            tracks.add(
                Track(
                    id = a.getLong(0), title = a.getString(1), artist = a.getString(2), albumArtist = a.getString(3),
                    album = a.getString(4), albumId = a.getLong(5), number = a.getInt(6), disc = a.getInt(7),
                    durationMs = a.getLong(8), uri = ContentUris.withAppendedId(collection, a.getLong(0)),
                    displayName = a.getString(9), relativePath = a.getString(10), volume = a.getString(11),
                )
            )
        }
        if (tracks.isEmpty()) null else buildLibrary(tracks)
    } catch (_: Exception) {
        null
    }
}
