package com.you.media

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Covers kept in memory, limited by size (an eighth of the app's memory) rather than by count.
private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(16 shl 20)) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Opens a picture file at no more than the size needed, instead of at full size. */
private fun decodeScaled(path: String, side: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Opens a picture from the folder chosen in Settings, shrunk to the size needed. */
private fun decodeDocument(context: Context, uri: Uri, side: Int): android.graphics.Bitmap =
    android.graphics.ImageDecoder.decodeBitmap(
        android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
    ) { decoder, info, _ ->
        val w = info.size.width; val h = info.size.height
        val scale = minOf(1f, side.toFloat() / maxOf(1, minOf(w, h)))
        decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
    }

private fun loadArt(context: Context, path: String?, albumId: Long?, videoUri: Uri?, large: Boolean): ImageBitmap? {
    val side = if (large) 1080 else 512
    val key = when {
        path != null -> "file:$path"
        albumId != null -> "album:$albumId"
        else -> "video:$videoUri"
    } + if (large) ":L" else ""
    cache.get(key)?.let { return it }
    val bitmap = try {
        when {
            path != null && path.startsWith("content://") -> decodeDocument(context, Uri.parse(path), side)
            path != null -> decodeScaled(path, side)
            albumId != null -> context.contentResolver.loadThumbnail(
                ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId),
                Size(side, side),
                null,
            )
            // Pictures from the folder chosen in Settings are plain files: decode them, shrunk.
            videoUri != null && videoUri.authority?.endsWith(".documents") == true ->
                decodeDocument(context, videoUri, side)
            videoUri != null -> context.contentResolver.loadThumbnail(videoUri, Size(side, side), null)
            else -> null
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
    val image = bitmap?.asImageBitmap()
    if (image != null) cache.put(key, image)
    return image
}

/** Cover art in full color. If there is none, a dark tile with the name on it. */
@Composable
fun Cover(path: String?, albumId: Long?, label: String, modifier: Modifier = Modifier, videoUri: Uri? = null, large: Boolean = false) {
    val context = LocalContext.current
    val image = produceState<ImageBitmap?>(initialValue = null, path, albumId, videoUri, large) {
        value = withContext(Dispatchers.IO) { loadArt(context, path, albumId, videoUri, large) }
    }.value
    if (image != null) {
        Image(bitmap = image, contentDescription = label, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(Panel), contentAlignment = Alignment.Center) {
            Text(
                text = label, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                maxLines = 3, modifier = Modifier.padding(10.dp),
            )
        }
    }
}
