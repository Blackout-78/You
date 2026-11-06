package com.you.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// All icons are drawn on a 44 x 44 grid and scaled to the button, outline only.

private fun DrawScope.stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.polygon(color: Color, w: Float, points: List<Pair<Float, Float>>, filled: Boolean = false) {
    val path = Path()
    points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
    path.close()
    drawPath(path, color, style = if (filled) Fill else stroke(w))
}

private fun DrawScope.arcArrow(color: Color, w: Float, cx: Float, cy: Float, r: Float, from: Float, to: Float) {
    val steps = 28
    val pts = (0..steps).map { i ->
        val a = (from + (to - from) * i / steps) * PI / 180.0
        Pair((cx + r * cos(a)).toFloat(), (cy - r * sin(a)).toFloat())
    }
    val path = Path()
    pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
    drawPath(path, color, style = stroke(w))
    val (tx, ty) = pts.last()
    val (bx, by) = pts[pts.size - 2]
    var dx = tx - bx
    var dy = ty - by
    val len = hypot(dx, dy).let { if (it == 0f) 1f else it }
    dx /= len
    dy /= len
    for (sign in listOf(1, -1)) {
        val turn = 150.0 * sign * PI / 180.0
        val hx = (dx * cos(turn) - dy * sin(turn)).toFloat()
        val hy = (dx * sin(turn) + dy * cos(turn)).toFloat()
        line(color, tx, ty, tx + hx * 5.5f, ty + hy * 5.5f, w)
    }
}

private fun DrawScope.ten(color: Color, w: Float) {
    line(color, 17.5f, 20f, 19.5f, 18.5f, w)
    line(color, 19.5f, 18.5f, 19.5f, 26.5f, w)
    polygon(color, w, listOf(22.5f to 19f, 26.5f to 19f, 26.5f to 26f, 22.5f to 26f))
}

/** A round outline button. [draw] paints the icon on the 44 x 44 grid. */
@Composable
fun RoundButton(
    onClick: () -> Unit,
    size: Dp = 40.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    draw: DrawScope.(color: Color, width: Float) -> Unit,
) {
    val color = when {
        !enabled -> Line
        active -> White
        else -> Soft
    }
    val width = if (active && enabled) 2.2f else 1.6f
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, color, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            scale(this.size.width / 44f, Offset.Zero) { draw(color, width) }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private val PLAY = listOf(
    listOf(18f to 14f, 18f to 30f, 24.5f to 26.5f, 24.5f to 17.5f),
    listOf(24.5f to 17.5f, 24.5f to 26.5f, 31f to 22f, 31f to 22f),
)
private val PAUSE = listOf(
    listOf(15f to 15f, 15f to 29f, 20f to 29f, 20f to 15f),
    listOf(24f to 15f, 24f to 29f, 29f to 29f, 29f to 15f),
)

/** One button that melts between play and pause. */
@Composable
fun PlayPauseButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit, size: Dp = 52.dp) {
    val t by animateFloatAsState(if (playing) 1f else 0f, tween(200), label = "playpause")
    RoundButton(onClick, size, active = playing, enabled = enabled) { color, w ->
        for (i in PLAY.indices) {
            val pts = PLAY[i].indices.map { k ->
                lerp(PLAY[i][k].first, PAUSE[i][k].first, t) to lerp(PLAY[i][k].second, PAUSE[i][k].second, t)
            }
            polygon(color, w, pts)
        }
    }
}

@Composable
fun HeartButton(active: Boolean, enabled: Boolean, onClick: () -> Unit, size: Dp = 40.dp) {
    RoundButton(onClick, size, active = active, enabled = enabled) { color, w ->
        val pts = (0..36).map { i ->
            val a = i * 10 * PI / 180.0
            val x = 16 * sin(a).let { it * it * it }
            val y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
            (22 + x * 0.62).toFloat() to (21.5 - y * 0.62).toFloat()
        }
        polygon(color, w, pts, filled = active)
    }
}

@Composable
fun PreviousButton(enabled: Boolean, onClick: () -> Unit) =
    RoundButton(onClick, enabled = enabled) { color, w ->
        line(color, 14f, 15f, 14f, 29f, w)
        polygon(color, w, listOf(30f to 15f, 30f to 29f, 19f to 22f))
    }

@Composable
fun NextButton(enabled: Boolean, onClick: () -> Unit) =
    RoundButton(onClick, enabled = enabled) { color, w ->
        line(color, 30f, 15f, 30f, 29f, w)
        polygon(color, w, listOf(14f to 15f, 14f to 29f, 25f to 22f))
    }

@Composable
fun SkipButton(forward: Boolean, enabled: Boolean, onClick: () -> Unit) =
    RoundButton(onClick, enabled = enabled) { color, w ->
        if (forward) arcArrow(color, w, 22f, 22.5f, 11f, 55f, -235f)
        else arcArrow(color, w, 22f, 22.5f, 11f, 125f, 415f)
        ten(color, w)
    }

@Composable
fun ShuffleButton(active: Boolean, onClick: () -> Unit) =
    RoundButton(onClick, active = active) { color, w ->
        line(color, 12f, 29f, 19f, 29f, w); line(color, 19f, 29f, 25f, 15f, w); line(color, 25f, 15f, 31f, 15f, w)
        line(color, 12f, 15f, 19f, 15f, w); line(color, 19f, 15f, 25f, 29f, w); line(color, 25f, 29f, 31f, 29f, w)
        for (y in listOf(15f, 29f)) {
            line(color, 32f, y, 28.5f, y - 2.8f, w)
            line(color, 32f, y, 28.5f, y + 2.8f, w)
        }
    }

@Composable
fun StopButton(enabled: Boolean, onClick: () -> Unit) =
    RoundButton(onClick, enabled = enabled) { color, w ->
        line(color, 15f, 15f, 29f, 15f, w); line(color, 29f, 15f, 29f, 29f, w)
        line(color, 29f, 29f, 15f, 29f, w); line(color, 15f, 29f, 15f, 15f, w)
    }

/** mode: 0 = off, 1 = all, 2 = one */
@Composable
fun RepeatButton(mode: Int, onClick: () -> Unit) =
    RoundButton(onClick, active = mode != 0) { color, w ->
        line(color, 14f, 22f, 14f, 17f, w); line(color, 14f, 17f, 30f, 17f, w)
        line(color, 30f, 17f, 26f, 14.5f, w); line(color, 30f, 17f, 26f, 19.5f, w)
        line(color, 30f, 22f, 30f, 27f, w); line(color, 30f, 27f, 14f, 27f, w)
        line(color, 14f, 27f, 18f, 24.5f, w); line(color, 14f, 27f, 18f, 29.5f, w)
        if (mode == 2) {
            line(color, 20.5f, 21.5f, 22.5f, 19.5f, w)
            line(color, 22.5f, 19.5f, 22.5f, 25f, w)
        }
    }

@Composable
fun LyricsButton(active: Boolean, enabled: Boolean, onClick: () -> Unit) =
    RoundButton(onClick, active = active, enabled = enabled) { color, w ->
        line(color, 13f, 15f, 31f, 15f, w)
        line(color, 13f, 22f, 31f, 22f, w)
        line(color, 13f, 29f, 24f, 29f, w)
    }
