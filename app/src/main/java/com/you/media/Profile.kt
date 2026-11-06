package com.you.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File

/** Reads a picture the person chose, shrinks it and keeps a private copy inside the app. Nothing leaves the phone. */
fun importPicture(context: Context, uri: Uri, name: String, maxSide: Int, square: Boolean): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        if (square) {
            val side = minOf(bmp.width, bmp.height)
            bmp = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        }
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) {
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1), true)
        }
        val out = File(context.filesDir, name)
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        out.path
    } catch (_: Exception) {
        null
    }
}

private fun roundBitmap(src: Bitmap, size: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(Bitmap.createScaledBitmap(src, size, size, true), 0f, 0f, paint)
    return out
}

/** The default picture: a person shape, drawn in the person's own light and dark tones. */
private fun defaultAvatar(size: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = YouColors.solidDark.let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = YouColors.white.let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
    canvas.drawCircle(size / 2f, size * 0.38f, size * 0.17f, paint)
    val body = android.graphics.RectF(size * 0.22f, size * 0.60f, size * 0.78f, size * 1.12f)
    canvas.save()
    canvas.clipPath(android.graphics.Path().apply { addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW) })
    canvas.drawOval(body, paint)
    canvas.restore()
    return out
}

/** Puts a shortcut with the person's picture and the name "You" on the phone's home screen (the phone asks first). */
fun pinShortcut(context: Context, picturePath: String?): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
    val bmp = picturePath?.let { BitmapFactory.decodeFile(it) }?.let { roundBitmap(it, 192) } ?: defaultAvatar(192)
    val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
    val info = ShortcutInfoCompat.Builder(context, "you-" + System.currentTimeMillis())
        .setShortLabel("You")
        .setIcon(IconCompat.createWithBitmap(bmp))
        .setIntent(intent)
        .build()
    return ShortcutManagerCompat.requestPinShortcut(context, info, null)
}

/** The round profile picture. Without a chosen picture it shows the plain person shape. */
@Composable
fun Avatar(path: String?, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { path?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap() }
    val base = modifier.size(size).clip(CircleShape).border(1.dp, Line, CircleShape)
    if (bitmap != null) {
        Image(bitmap, contentDescription = "Profile picture", modifier = base, contentScale = ContentScale.Crop)
    } else {
        Box(base.background(YouColors.solidDark)) {
            ComposeCanvas(Modifier.fillMaxSize()) {
                val w = this.size.width
                drawCircle(White, radius = w * 0.17f, center = Offset(w / 2f, w * 0.38f))
                drawOval(White, topLeft = Offset(w * 0.22f, w * 0.60f),
                    size = androidx.compose.ui.geometry.Size(w * 0.56f, w * 0.52f))
            }
        }
    }
}

/** The background picture, with a layer on top that darkens it by the amount the person chose. */
@Composable
fun BackgroundPicture() {
    val p = YouColors.palette
    val path = p.picture ?: return
    val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() } ?: return
    Box(Modifier.fillMaxSize()) {
        Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(YouColors.solidDark.copy(alpha = 0.25f * p.dim)))
    }
}
