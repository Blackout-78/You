package com.you.media

import android.content.SharedPreferences
import android.graphics.Color as AColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

/** One color the person picked: hue circle + saturation + brightness. Stored as plain numbers. */
data class Tone(val hue: Float, val sat: Float, val bright: Float) {
    fun color(): Color = Color(AColor.HSVToColor(floatArrayOf(hue, sat, bright)))

    fun toJson(): JSONObject = JSONObject().put("h", hue.toDouble()).put("s", sat.toDouble()).put("v", bright.toDouble())

    companion object {
        fun fromJson(o: JSONObject?, fallback: Tone): Tone =
            if (o == null) fallback
            else Tone(o.optDouble("h", fallback.hue.toDouble()).toFloat(),
                o.optDouble("s", fallback.sat.toDouble()).toFloat(),
                o.optDouble("v", fallback.bright.toDouble()).toFloat())
    }
}

/**
 * A whole look: the three tones (light = the "white", middle = the "gray", dark = the "black")
 * and optionally a background picture the person chose, with a dim amount so text stays readable.
 */
data class Palette(
    val light: Tone,
    val middle: Tone,
    val dark: Tone,
    val picture: String? = null,
    val dim: Float = 0.7f,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("light", light.toJson()).put("middle", middle.toJson()).put("dark", dark.toJson())
        .put("picture", picture ?: JSONObject.NULL).put("dim", dim.toDouble())

    companion object {
        val Default = Palette(Tone(0f, 0f, 1f), Tone(0f, 0f, 0.54f), Tone(0f, 0f, 0f))

        fun fromJson(o: JSONObject?): Palette {
            if (o == null) return Default
            return Palette(
                Tone.fromJson(o.optJSONObject("light"), Default.light),
                Tone.fromJson(o.optJSONObject("middle"), Default.middle),
                Tone.fromJson(o.optJSONObject("dark"), Default.dark),
                if (o.isNull("picture")) null else o.optString("picture").ifEmpty { null },
                o.optDouble("dim", 0.7).toFloat(),
            )
        }
    }
}

/** Ready-made starting points. Every one can still be changed tone by tone. */
val presets: List<Pair<String, Palette>> = listOf(
    "Classic" to Palette.Default,
    "Ocean" to Palette(Tone(200f, 0.10f, 1f), Tone(195f, 0.55f, 0.80f), Tone(215f, 0.80f, 0.14f)),
    "Sunset" to Palette(Tone(35f, 0.12f, 1f), Tone(25f, 0.85f, 0.95f), Tone(345f, 0.75f, 0.18f)),
    "Forest" to Palette(Tone(95f, 0.10f, 1f), Tone(120f, 0.45f, 0.65f), Tone(140f, 0.75f, 0.10f)),
    "Berry" to Palette(Tone(320f, 0.08f, 1f), Tone(330f, 0.55f, 0.85f), Tone(285f, 0.80f, 0.15f)),
)

/**
 * The colors every screen reads. "Section" says which part of the app is showing (music, movies, shows),
 * so a section that has its own look can differ from the rest. Everything here is plain state, so changing
 * a color repaints the whole app at once.
 */
object YouColors {
    var global by mutableStateOf(Palette.Default)
    val own = androidx.compose.runtime.mutableStateMapOf<String, Palette>()
    var sectionProvider: () -> String = { "" }

    val palette: Palette get() = own[sectionProvider()] ?: global

    private val hasPicture: Boolean get() = palette.picture != null

    /** With a background picture the dark tone becomes see-through, so the picture shows behind it. */
    private fun dark(): Color {
        val c = palette.dark.color()
        return if (hasPicture) c.copy(alpha = 0.30f + 0.65f * palette.dim) else c
    }

    val black: Color get() = dark()
    val white: Color get() = palette.light.color()
    val muted: Color get() = palette.middle.color()
    val soft: Color get() = lerp(palette.light.color(), palette.middle.color(), 0.25f)
    val line: Color get() = lerp(palette.dark.color(), palette.middle.color(), 0.38f)
    val panel: Color get() = lerp(palette.dark.color(), palette.light.color(), 0.08f).let {
        if (hasPicture) it.copy(alpha = 0.40f + 0.55f * palette.dim) else it
    }
    /** The solid dark tone, used where a see-through color would look wrong (system bars, text on a light button). */
    val solidDark: Color get() = palette.dark.color()

    fun load(prefs: SharedPreferences) {
        global = runCatching { Palette.fromJson(JSONObject(prefs.getString("palette_all", "") ?: "")) }
            .getOrDefault(Palette.Default)
        own.clear()
        for (key in listOf("music", "movies", "shows")) {
            val text = prefs.getString("palette_$key", null) ?: continue
            runCatching { own[key] = Palette.fromJson(JSONObject(text)) }
        }
    }

    fun reset(prefs: SharedPreferences) {
        global = Palette.Default
        own.clear()
        prefs.edit().remove("palette_all").remove("palette_music").remove("palette_movies").remove("palette_shows").apply()
    }
}

// The rest of the app keeps using these names; they now follow the person's colors.
val Black: Color get() = YouColors.black
val White: Color get() = YouColors.white
val Soft: Color get() = YouColors.soft
val Muted: Color get() = YouColors.muted
val Line: Color get() = YouColors.line
val Panel: Color get() = YouColors.panel

@Composable
fun YouTheme(content: @Composable () -> Unit) {
    // Paint the phone's status bar and navigation bar in the dark tone too.
    val view = androidx.compose.ui.platform.LocalView.current
    val bars = YouColors.solidDark.toArgb()
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let {
            it.statusBarColor = bars
            it.navigationBarColor = bars
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = White,
            onPrimary = YouColors.solidDark,
            background = Black,
            onBackground = White,
            surface = Black,
            onSurface = White,
            surfaceVariant = Panel,
            onSurfaceVariant = Muted,
            outline = Line,
        ),
        content = content,
    )
}
