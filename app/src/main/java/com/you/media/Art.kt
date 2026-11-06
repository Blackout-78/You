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

private val cache = LruCache<String, ImageBitmap>(80)

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
            path != null -> BitmapFactory.decodeFile(path)
            albumId != null -> context.contentResolver.loadThumbnail(
                ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId),
                Size(side, side),
                null,
            )
            videoUri != null -> context.contentResolver.loadThumbnail(videoUri, Size(side, side), null)
            else -> null
        }
    } catch (_: Exception) {
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
