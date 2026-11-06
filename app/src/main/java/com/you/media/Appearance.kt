package com.you.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A circle of colors. Drag or tap on the ring to pick the hue. */
@Composable
fun HueCircle(hue: Float, onHue: (Float) -> Unit, modifier: Modifier = Modifier) {
    val steps = remember { (0..360 step 30).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) } }
    fun pick(pos: Offset, size: androidx.compose.ui.unit.IntSize) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        var deg = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat()
        if (deg < 0) deg += 360f
        onHue(deg)
    }
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { pick(it, size) } }
            .pointerInput(Unit) { detectDragGestures(onDrag = { change, _ -> pick(change.position, size) }) }
    ) {
        val stroke = size.minDimension * 0.17f
        val radius = size.minDimension / 2f - stroke / 2f
        drawCircle(
            brush = Brush.sweepGradient(steps, center),
            radius = radius, center = center, style = Stroke(stroke),
        )
        val a = Math.toRadians(hue.toDouble())
        val dot = Offset(center.x + (radius * cos(a)).toFloat(), center.y + (radius * sin(a)).toFloat())
        drawCircle(Color.White, radius = stroke * 0.5f, center = dot, style = Stroke(4f))
        drawCircle(Color.Black, radius = stroke * 0.5f + 2f, center = dot, style = Stroke(2f))
        // The middle shows the color you are making.
        drawCircle(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))), radius = radius - stroke * 0.8f, center = center)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Soft, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = White, activeTrackColor = White, inactiveTrackColor = Line),
        )
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp, bottom = 8.dp)
            .background(if (on) White else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text, color = if (on) YouColors.solidDark else Soft, fontSize = 14.sp,
            fontWeight = if (on) FontWeight.Bold else null)
    }
}

/** A small picture of how the three colors will look together. Drawn from the colors being edited, not the current look. */
@Composable
private fun Preview(p: Palette) {
    Box(Modifier.fillMaxWidth().height(110.dp).background(p.dark.color()).border(1.dp, Line)) {
        Column(Modifier.padding(14.dp)) {
            Text("Your look", color = p.light.color(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Songs, movies and shows", color = p.middle.color(), fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row {
                for (t in listOf(p.light, p.middle, p.dark)) {
                    Box(Modifier.size(22.dp).background(t.color()).border(1.dp, p.light.color().copy(alpha = 0.4f)))
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
fun AppearanceSection(vm: AppViewModel) {
    var scope by remember { mutableStateOf("all") }
    var which by remember { mutableIntStateOf(0) }       // 0 light, 1 middle, 2 dark
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setBackground(scope, uri)
    }

    Text("Colors", color = White, fontSize = 16.sp)
    Spacer(Modifier.height(4.dp))
    Text(
        "The app has three colors: a light one for text, a middle one for small words, and a dark one for the background. " +
            "Pick any color for each, or use a picture as the background.",
        color = Muted, fontSize = 14.sp,
    )
    Spacer(Modifier.height(10.dp))

    Row(Modifier.horizontalScroll(rememberScrollState())) {
        for ((key, label) in listOf("all" to "Whole app", "music" to "Music", "movies" to "Movies", "shows" to "TV Shows")) {
            Chip(label, scope == key) { scope = key }
        }
    }

    val editable = scope == "all" || vm.hasOwnLook(scope)
    if (scope != "all") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("Use my own colors here", color = Soft, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = editable, onCheckedChange = { vm.useOwnLook(scope, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = YouColors.solidDark, checkedTrackColor = White,
                    uncheckedThumbColor = Muted, uncheckedTrackColor = Panel, uncheckedBorderColor = Line),
            )
        }
        if (!editable) {
            Text("This section uses the whole-app colors.", color = Muted, fontSize = 14.sp)
            return
        }
    }

    val palette = vm.paletteFor(scope)
    Preview(palette)
    Spacer(Modifier.height(12.dp))

    Text("Ready-made", color = Soft, fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        for ((name, preset) in presets) {
            Chip(name, false) { vm.savePalette(scope, preset.copy(picture = palette.picture, dim = palette.dim)) }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text("Choose a color to change", color = Soft, fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    Row {
        for ((i, label) in listOf("Light", "Middle", "Dark").withIndex()) {
            val tone = listOf(palette.light, palette.middle, palette.dark)[i]
            Row(
                Modifier.padding(end = 8.dp).border(1.dp, if (which == i) White else Line)
                    .clickable { which = i }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp).background(tone.color(), CircleShape).border(1.dp, Line, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(label, color = if (which == i) White else Soft, fontSize = 14.sp)
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    val tone = listOf(palette.light, palette.middle, palette.dark)[which]
    fun update(t: Tone) = vm.savePalette(scope, when (which) {
        0 -> palette.copy(light = t)
        1 -> palette.copy(middle = t)
        else -> palette.copy(dark = t)
    })
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HueCircle(tone.hue, { update(tone.copy(hue = it)) }, Modifier.size(220.dp))
    }
    LabeledSlider("Saturation", tone.sat, { update(tone.copy(sat = it)) })
    LabeledSlider("Brightness", tone.bright, { update(tone.copy(bright = it)) })

    Spacer(Modifier.height(14.dp))
    Text("Background picture", color = Soft, fontSize = 14.sp)
    Text(
        if (palette.picture != null) "A picture is used instead of the dark color."
        else "Optional. A picture of your choice can replace the dark color.",
        color = Muted, fontSize = 13.sp,
    )
    Spacer(Modifier.height(8.dp))
    Row {
        OutlinedButton(onClick = { pickBackground.launch("image/*") }, border = BorderStroke(1.dp, Line)) {
            Text(if (palette.picture != null) "Change picture" else "Choose picture", color = White)
        }
        if (palette.picture != null) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.setBackground(scope, null) }, border = BorderStroke(1.dp, Line)) {
                Text("Remove picture", color = White)
            }
        }
    }
    if (palette.picture != null) {
        LabeledSlider("Dim picture", palette.dim, { vm.savePalette(scope, palette.copy(dim = it)) })
    }

    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = { vm.resetLook(scope) }, border = BorderStroke(1.dp, Line)) {
        Text(if (scope == "all") "Back to black, white and gray" else "Use the whole-app colors", color = White)
    }
}

@Composable
fun youFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White, unfocusedTextColor = White,
    focusedBorderColor = White, unfocusedBorderColor = Line,
    cursorColor = White, focusedLabelColor = Soft, unfocusedLabelColor = Muted,
)

@Composable
fun ProfileSection(vm: AppViewModel) {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setProfilePicture(uri)
    }
    var shortcutMsg by remember { mutableStateOf("") }

    Text("Profile", color = White, fontSize = 16.sp)
    Spacer(Modifier.height(4.dp))
    Text("Your picture and name stay on this phone. They make the app feel like yours.", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(vm.profilePicture, 72.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            OutlinedButton(onClick = { pick.launch("image/*") }, border = BorderStroke(1.dp, Line)) {
                Text(if (vm.profilePicture != null) "Change picture" else "Choose picture", color = White)
            }
            if (vm.profilePicture != null) {
                OutlinedButton(onClick = { vm.setProfilePicture(null) }, border = BorderStroke(1.dp, Line)) {
                    Text("Remove picture", color = White)
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = vm.profileName, onValueChange = { vm.changeProfileName(it) },
        label = { Text("Your name (optional)") }, singleLine = true,
        colors = youFieldColors(), modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = {
            shortcutMsg = if (pinShortcut(context, vm.profilePicture)) "Your phone will ask you to add it."
            else "This phone cannot add shortcuts from here."
        },
        border = BorderStroke(1.dp, Line),
    ) { Text("Add a shortcut with my picture", color = White) }
    if (shortcutMsg.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(shortcutMsg, color = Muted, fontSize = 13.sp)
    }
}

/** The first thing a new person sees: pick a picture and name, optionally a look, then go. */
@Composable
fun WelcomeScreen(vm: AppViewModel) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setProfilePicture(uri)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Avatar(vm.profilePicture, 120.dp)
        Spacer(Modifier.height(18.dp))
        Text("Welcome to You", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Make it yours. You can change all of this later in Settings.",
            color = Muted, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { pick.launch("image/*") }, border = BorderStroke(1.dp, Line)) {
            Text(if (vm.profilePicture != null) "Change picture" else "Choose my picture", color = White)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.profileName, onValueChange = { vm.changeProfileName(it) },
            label = { Text("Your name (optional)") }, singleLine = true,
            colors = youFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Text("Pick a look", color = Soft, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            for ((name, preset) in presets) Chip(name, false) { vm.savePalette("all", preset) }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { vm.finishWelcome() }, border = BorderStroke(1.dp, White)) {
            Text("Start", color = White, fontSize = 16.sp)
        }
    }
}
