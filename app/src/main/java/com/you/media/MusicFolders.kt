package com.you.media

/**
 * Folder of an artist, like "Music/ALLmusic/Gizmo", from the folder of one of its songs
 * ("Music/ALLmusic/Gizmo/Album/"). With a music folder chosen in Settings the artist is the first
 * folder inside it; otherwise the layout Artist/Album/song is assumed.
 */
fun artistFolder(songDir: String, musicFolders: List<String>): String {
    val dir = songDir.trim('/')
    val root = musicFolders.map { it.trim('/') }
        .filter { it.isNotEmpty() && (dir.equals(it, true) || dir.startsWith("$it/", true)) }
        .maxByOrNull { it.length }
    if (root == null) return dir.substringBeforeLast('/', "")
    val first = dir.substring(root.length).trim('/').substringBefore('/')
    return if (first.isEmpty()) root else "$root/$first"
}
