package com.you.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** One line of lyrics. timeMs is -1 for plain (unsynced) lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

// ---- turning text into lyrics ---------------------------------------------------------

private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
private val OFFSET_TAG = Regex("^\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE)

/** Reads .lrc text. Returns null if it has no time tags at all. */
fun parseLrc(text: String): Lyrics? {
    var offset = 0L
    val lines = ArrayList<LyricLine>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val offsetMatch = OFFSET_TAG.find(line)
        if (offsetMatch != null) {
            offset = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
            continue
        }
        var rest = line
        val times = ArrayList<Long>()
        while (true) {
            val m = TIME_TAG.find(rest)
            if (m == null || m.range.first != 0) break
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            val fraction = m.groupValues[3]
            val millis = when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.take(3).toLong()
            }
            times.add(minutes * 60_000 + seconds * 1000 + millis)
            rest = rest.substring(m.range.last + 1).trimStart()
        }
        if (times.isEmpty()) continue                     // [ar:...] [ti:...] tags, or text with no time
        for (t in times) lines.add(LyricLine((t - offset).coerceAtLeast(0L), rest.trim()))
    }
    if (lines.isEmpty()) return null
    return Lyrics(lines.sortedBy { it.timeMs }, synced = true)
}

fun parsePlain(text: String): Lyrics? {
    val lines = text.lines().map { LyricLine(-1L, it.trimEnd()) }
    val trimmed = lines.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
    return if (trimmed.isEmpty()) null else Lyrics(trimmed, synced = false)
}

/** Text files come in UTF-8, UTF-16 or old Windows encodings. */
fun decodeText(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) {
        String(bytes, Charset.forName("windows-1252"))
    }
}

// ---- lyrics stored inside the song file (MP3 and FLAC) ----------------------------------

private fun DataInputStream.skipFully(count: Long) {
    var left = count
    while (left > 0) {
        val n = skip(left)
        if (n <= 0) {
            if (read() < 0) return
            left--
        } else left -= n
    }
}

private fun syncSafe(b: ByteArray, at: Int): Int =
    (b[at].toInt() and 0x7F shl 21) or (b[at + 1].toInt() and 0x7F shl 14) or
        (b[at + 2].toInt() and 0x7F shl 7) or (b[at + 3].toInt() and 0x7F)

private fun int32(b: ByteArray, at: Int): Int =
    (b[at].toInt() and 0xFF shl 24) or (b[at + 1].toInt() and 0xFF shl 16) or
        (b[at + 2].toInt() and 0xFF shl 8) or (b[at + 3].toInt() and 0xFF)

private fun id3Text(encoding: Int, data: ByteArray, from: Int, to: Int): String {
    if (from >= to) return ""
    val charset = when (encoding) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }
    return String(data, from, to - from, charset).trim('\u0000', '﻿')
}

/** The unsynchronised lyrics frame (USLT). */
private fun parseUslt(body: ByteArray): String? {
    if (body.size < 5) return null
    val enc = body[0].toInt()
    var i = 4                                    // skip encoding byte and 3 language bytes
    val wide = enc == 1 || enc == 2
    // skip the short description, which ends with a null (two nulls for 16-bit text)
    while (i < body.size) {
        if (!wide && body[i].toInt() == 0) { i += 1; break }
        if (wide && i + 1 < body.size && body[i].toInt() == 0 && body[i + 1].toInt() == 0 && (i - 4) % 2 == 0) {
            i += 2; break
        }
        i++
    }
    val text = id3Text(enc, body, i.coerceAtMost(body.size), body.size)
    return text.ifBlank { null }
}

private fun readId3Lyrics(input: DataInputStream): String? {
    val header = ByteArray(10)
    input.readFully(header)
    if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
    val major = header[3].toInt()
    val flags = header[5].toInt()
    var remaining = syncSafe(header, 6).toLong()
    if (major < 2 || major > 4) return null
    if (flags and 0x40 != 0 && major >= 3) {            // extended header: skip it
        val ext = ByteArray(4)
        input.readFully(ext)
        val size = if (major == 4) syncSafe(ext, 0) - 4 else int32(ext, 0)
        input.skipFully(size.toLong().coerceAtLeast(0))
        remaining -= 4 + size.coerceAtLeast(0)
    }
    val frameHeader = if (major == 2) 6 else 10
    while (remaining > frameHeader) {
        val fh = ByteArray(frameHeader)
        input.readFully(fh)
        remaining -= frameHeader
        if (fh[0].toInt() == 0) break                    // padding
        val id = String(fh, 0, if (major == 2) 3 else 4, Charsets.ISO_8859_1)
        val size = when (major) {
            2 -> (fh[3].toInt() and 0xFF shl 16) or (fh[4].toInt() and 0xFF shl 8) or (fh[5].toInt() and 0xFF)
            4 -> syncSafe(fh, 4)
            else -> int32(fh, 4)
        }
        if (size < 0 || size > remaining) break
        if (id == "USLT" || id == "ULT") {
            val body = ByteArray(size)
            input.readFully(body)
            parseUslt(body)?.let { return it }
        } else {
            input.skipFully(size.toLong())
        }
        remaining -= size
    }
    return null
}

private fun readFlacLyrics(input: DataInputStream): String? {
    val magic = ByteArray(4)
    input.readFully(magic)
    if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null
    while (true) {
        val h = input.readUnsignedByte()
        val last = h and 0x80 != 0
        val type = h and 0x7F
        val length = (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
        if (type == 4) {
            val block = ByteArray(length)
            input.readFully(block)
            return vorbisLyrics(block)
        }
        input.skipFully(length.toLong())
        if (last) return null
    }
}

private fun le32(b: ByteArray, at: Int): Int =
    (b[at].toInt() and 0xFF) or (b[at + 1].toInt() and 0xFF shl 8) or
        (b[at + 2].toInt() and 0xFF shl 16) or (b[at + 3].toInt() and 0xFF shl 24)

private fun vorbisLyrics(b: ByteArray): String? {
    if (b.size < 8) return null
    var i = 4 + le32(b, 0)
    if (i + 4 > b.size) return null
    val count = le32(b, i)
    i += 4
    var plain: String? = null
    repeat(count) {
        if (i + 4 > b.size) return plain
        val len = le32(b, i)
        i += 4
        if (len < 0 || i + len > b.size) return plain
        val entry = String(b, i, len, Charsets.UTF_8)
        i += len
        val eq = entry.indexOf('=')
        if (eq > 0) {
            val key = entry.substring(0, eq).uppercase()
            if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS") {
                val value = entry.substring(eq + 1)
                if (value.isNotBlank()) {
                    if (TIME_TAG.containsMatchIn(value)) return value
                    if (plain == null) plain = value
                }
            }
        }
    }
    return plain
}

/** Lyrics text stored in the song itself, or null. */
fun readEmbeddedLyrics(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { raw ->
        val input = DataInputStream(BufferedInputStream(raw, 64 * 1024))
        input.mark(8)
        val sig = ByteArray(4)
        input.readFully(sig)
        input.reset()
        when (String(sig, Charsets.ISO_8859_1).take(3)) {
            "ID3" -> readId3Lyrics(input)
            else -> if (String(sig, Charsets.ISO_8859_1) == "fLaC") readFlacLyrics(input) else null
        }
    }
} catch (_: Exception) {
    null
}

// ---- lyrics files next to the songs (.lrc / .txt) ---------------------------------------

/**
 * Android only lets an app see non-music files in a folder you pick. You pick your music folder
 * once; we list the .lrc and .txt files in it and remember where they are.
 */
class LyricsIndex(private val context: Context) {
    private val file = File(context.filesDir, "lyrics_index.json")
    var treeUri: String? = null
        private set
    private var lrc: Map<String, String> = emptyMap()
    private var txt: Map<String, String> = emptyMap()
    private var srt: Map<String, String> = emptyMap()
    /** True when the saved list was made before subtitles were supported. */
    var needsRescan = false
        private set

    init {
        try {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                treeUri = root.optString("tree").ifEmpty { null }
                lrc = readMap(root.optJSONObject("lrc"))
                txt = readMap(root.optJSONObject("txt"))
                srt = readMap(root.optJSONObject("srt"))
                needsRescan = treeUri != null && !root.has("srt")
            }
        } catch (_: Exception) {
        }
    }

    private fun readMap(o: JSONObject?): Map<String, String> {
        val out = HashMap<String, String>()
        o?.keys()?.forEach { out[it] = o.getString(it) }
        return out
    }

    fun clear() {
        treeUri?.let { t ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(t), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        treeUri = null
        lrc = emptyMap()
        txt = emptyMap()
        srt = emptyMap()
        needsRescan = false
        runCatching { file.delete() }
    }

    val fileCount: Int get() = lrc.size + txt.size

    /** Lists every .lrc and .txt under the chosen folder. Slow on big libraries, so run it off the main thread. */
    fun rebuild(tree: Uri) {
        val newLrc = HashMap<String, String>()
        val newTxt = HashMap<String, String>()
        val newSrt = HashMap<String, String>()
        val resolver = context.contentResolver
        val pending = ArrayDeque<String>()
        pending.add(DocumentsContract.getTreeDocumentId(tree))
        while (pending.isNotEmpty()) {
            val parent = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val found = ArrayList<Triple<String, String, String>>()
            try {
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) found.add(Triple(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: ""))
                }
            } catch (_: Exception) {
            }
            for ((id, name, mime) in found) {
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    pending.add(id)
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext == "srt" || ext == "vtt") {
                        val key = id.substringBeforeLast('.').lowercase()
                        newSrt[key] = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                    } else if (ext == "lrc" || ext == "txt") {
                        val key = id.substringBeforeLast('.').lowercase()
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                        if (ext == "lrc") newLrc[key] = uri else newTxt[key] = uri
                    }
                }
            }
        }
        treeUri = tree.toString()
        lrc = newLrc
        txt = newTxt
        srt = newSrt
        needsRescan = false
        try {
            val root = JSONObject()
            root.put("tree", treeUri)
            root.put("lrc", JSONObject(lrc))
            root.put("txt", JSONObject(txt))
            root.put("srt", JSONObject(srt))
            file.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    fun key(track: Track): String {
        val volume = if (track.volume == "external_primary") "primary" else track.volume
        val base = track.displayName.substringBeforeLast('.')
        return "$volume:${track.relativePath}$base".lowercase()
    }

    private fun readText(uri: String): String? = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { decodeText(it.readLimited(2_000_000)) }
    } catch (_: Exception) {
        null
    }

    /** A .srt or .vtt next to the video with the same name, if you chose that folder. */
    fun subtitleFor(video: Video): Uri? {
        val volume = if (video.volume == "external_primary") "primary" else video.volume
        val key = "$volume:${video.relativePath}${video.displayName.substringBeforeLast('.')}".lowercase()
        return srt[key]?.let { Uri.parse(it) }
    }

    fun lrcText(track: Track): String? = lrc[key(track)]?.let { readText(it) }
    fun txtText(track: Track): String? = txt[key(track)]?.let { readText(it) }
}

private fun InputStream.readLimited(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buffer)
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** Order: .lrc file, timed lyrics inside the song, .txt file, plain lyrics inside the song. */
fun findLyrics(context: Context, index: LyricsIndex, track: Track): Lyrics? {
    index.lrcText(track)?.let { text -> parseLrc(text)?.let { return it } }
    val embedded = readEmbeddedLyrics(context, track.uri)
    if (embedded != null) parseLrc(embedded)?.let { return it }
    index.txtText(track)?.let { text -> parsePlain(text)?.let { return it } }
    return embedded?.let { parsePlain(it) }
}
