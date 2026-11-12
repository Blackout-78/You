package com.you.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything you write in the app (descriptions, your own covers, hearts) lives in one small
 * file inside the app's private storage. It is never sent anywhere.
 */
class Store(private val context: Context) {
    private val file = File(context.filesDir, "you.json")
    val notes = mutableStateMapOf<String, String>()
    val covers = mutableStateMapOf<String, String>()
    val favorites = mutableStateListOf<Long>()
    val videoFavorites = mutableStateListOf<Long>()
    private val resume = HashMap<String, Long>()
    private val resumeAt = HashMap<String, Long>()
    val playlists = mutableStateMapOf<String, List<Long>>()
    /** How many times each song was played (for Most played). Stays on this phone. */
    val playCounts = mutableStateMapOf<Long, Int>()

    init {
        try {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                root.optJSONObject("notes")?.let { o -> o.keys().forEach { notes[it] = o.getString(it) } }
                root.optJSONObject("covers")?.let { o -> o.keys().forEach { covers[it] = o.getString(it) } }
                root.optJSONObject("resume")?.let { o -> o.keys().forEach { resume[it] = o.getLong(it) } }
                root.optJSONObject("resumeAt")?.let { o -> o.keys().forEach { resumeAt[it] = o.getLong(it) } }
                root.optJSONObject("plays")?.let { o -> o.keys().forEach { k -> k.toLongOrNull()?.let { id -> playCounts[id] = o.getInt(k) } } }
                root.optJSONObject("playlists")?.let { o ->
                    o.keys().forEach { name ->
                        val a = o.getJSONArray(name)
                        playlists[name] = (0 until a.length()).map { a.getLong(it) }
                    }
                }
                root.optJSONArray("videoFavorites")?.let { a -> for (i in 0 until a.length()) videoFavorites.add(a.getLong(i)) }
                root.optJSONArray("favorites")?.let { a -> for (i in 0 until a.length()) favorites.add(a.getLong(i)) }
            }
        } catch (_: Exception) {
            // a damaged file just starts you fresh
        }
    }

    private fun save() {
        try {
            val root = JSONObject()
            root.put("notes", JSONObject(notes.toMap()))
            root.put("covers", JSONObject(covers.toMap()))
            root.put("favorites", JSONArray(favorites.toList()))
            root.put("videoFavorites", JSONArray(videoFavorites.toList()))
            root.put("resume", JSONObject(resume.toMap()))
            root.put("resumeAt", JSONObject(resumeAt.toMap()))
            val pl = JSONObject()
            playlists.forEach { (name, ids) -> pl.put(name, JSONArray(ids)) }
            root.put("playlists", pl)
            val plays = JSONObject()
            playCounts.forEach { (id, n) -> plays.put(id.toString(), n) }
            root.put("plays", plays)
            file.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    /** Forgets everything the app saved: descriptions, covers you picked, favorites and video spots. */
    fun clearAll() {
        covers.values.forEach { runCatching { File(it).delete() } }
        notes.clear(); covers.clear(); favorites.clear(); videoFavorites.clear(); resume.clear(); resumeAt.clear(); playlists.clear(); playCounts.clear()
        save()
    }

    /** Counts one more play for a song. */
    fun played(id: Long) { playCounts[id] = (playCounts[id] ?: 0) + 1; save() }

    fun setNote(key: String, text: String) {
        if (text.isBlank()) notes.remove(key) else notes[key] = text.trim()
        save()
    }

    fun setCover(key: String, path: String?) {
        if (path == null) covers.remove(key) else covers[key] = path
        save()
    }

    /** Ids of videos you started and did not finish, most recent first. */
    fun unfinishedVideoIds(): List<Long> =
        resume.keys.filter { it.startsWith("v") }
            .sortedByDescending { resumeAt[it] ?: 0L }
            .mapNotNull { it.removePrefix("v").toLongOrNull() }

    fun createPlaylist(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || playlists.containsKey(n)) return false
        playlists[n] = emptyList(); save(); return true
    }

    fun deletePlaylist(name: String) { playlists.remove(name); save() }

    fun addToPlaylist(name: String, trackId: Long) {
        val list = playlists[name] ?: return
        if (!list.contains(trackId)) { playlists[name] = list + trackId; save() }
    }

    fun removeFromPlaylist(name: String, trackId: Long) {
        playlists[name]?.let { playlists[name] = it - trackId; save() }
    }

    fun resumeFor(key: String): Long = resume[key] ?: 0L

    /** Remembers where you stopped. Under 10 s is not worth keeping; within 10 s of the end means finished. */
    fun saveResume(key: String, positionMs: Long, durationMs: Long) {
        if (positionMs < 10_000) return
        if (durationMs > 0 && positionMs > durationMs - 10_000) {
            resume.remove(key); resumeAt.remove(key)
        } else {
            resume[key] = positionMs; resumeAt[key] = System.currentTimeMillis()
        }
        save()
    }

    fun isVideoFavorite(id: Long) = videoFavorites.contains(id)

    fun toggleVideoFavorite(id: Long) {
        if (!videoFavorites.remove(id)) videoFavorites.add(id)
        save()
    }

    fun isFavorite(id: Long) = favorites.contains(id)

    fun toggleFavorite(id: Long) {
        if (!favorites.remove(id)) favorites.add(id)
        save()
    }

    /** Copies a picture you chose into the app's own folder, shrunk to a sensible size. Returns its path. */
    fun importCover(uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
            val longest = maxOf(bitmap.width, bitmap.height)
            val scaled = if (longest > 800) {
                val f = 800f / longest
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt(), (bitmap.height * f).toInt(), true)
            } else bitmap
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val out = File(dir, "cover_${System.nanoTime()}.jpg")
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            out.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
