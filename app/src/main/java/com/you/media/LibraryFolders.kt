package com.you.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Settings > Library folders: separate folder lists for music, movies and TV shows.
 * A kind with no folders reads the whole phone (the old behavior). Folders are kept as
 * storage paths like "BnM/TV & Movie/Movie", matched against MediaStore's RELATIVE_PATH.
 */
object LibraryFolders {
    const val MUSIC = "music"
    const val MOVIES = "movies"
    const val TV = "tv"

    private fun prefs(context: Context) =
        context.getSharedPreferences("you_ui", Context.MODE_PRIVATE)

    fun get(context: Context, kind: String): List<String> =
        (prefs(context).getString("folders_$kind", "") ?: "").split('\n').filter { it.isNotBlank() }

    fun set(context: Context, kind: String, folders: List<String>) {
        prefs(context).edit().putString("folders_$kind", folders.joinToString("\n")).apply()
    }

    /** "content://.../tree/primary%3ABnM%2FTV" -> "BnM/TV" (storage root gives ""). */
    fun pathOf(uri: Uri): String =
        DocumentsContract.getTreeDocumentId(uri).substringAfter(':', "").trim('/')

    /** True when [relativePath] sits inside one of [folders]; no folders means everything. */
    fun allows(folders: List<String>, relativePath: String): Boolean {
        if (folders.isEmpty()) return true
        val path = relativePath.trim('/').lowercase()
        return folders.any {
            val f = it.trim('/').lowercase()
            f.isEmpty() || path == f || path.startsWith("$f/")
        }
    }
}
