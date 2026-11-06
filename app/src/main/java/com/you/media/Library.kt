package com.you.media

import android.content.ContentUris
import android.content.Context
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

/** Reads every song on the phone (internal storage and SD card) from Android's own media library. */
fun loadLibrary(context: Context): Library {
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
