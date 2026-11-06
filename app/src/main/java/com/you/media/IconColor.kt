package com.you.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The colors the app icon on the home screen can be. Each one is built into the app as its own launcher entry. */
data class IconChoice(val key: String, val label: String, val argb: Long)

val iconChoices = listOf(
    IconChoice("black", "Black", 0xFF000000),
    IconChoice("gray", "Gray", 0xFF6B6B6B),
    IconChoice("white", "White", 0xFFF2F2F2),
    IconChoice("red", "Red", 0xFFD32F2F),
    IconChoice("orange", "Orange", 0xFFF57C00),
    IconChoice("amber", "Amber", 0xFFFFB300),
    IconChoice("yellow", "Yellow", 0xFFFDD835),
    IconChoice("green", "Green", 0xFF2E7D32),
    IconChoice("teal", "Teal", 0xFF00897B),
    IconChoice("cyan", "Cyan", 0xFF00ACC1),
    IconChoice("blue", "Blue", 0xFF1E66D6),
    IconChoice("indigo", "Indigo", 0xFF3949AB),
    IconChoice("purple", "Purple", 0xFF7B1FA2),
    IconChoice("pink", "Pink", 0xFFE91E63),
    IconChoice("brown", "Brown", 0xFF6D4C41),
    IconChoice("rose", "Rose", 0xFFF8BBD0),
)

private fun entry(context: Context, key: String) =
    ComponentName(context.packageName, "com.you.media.Icon" + key.replaceFirstChar { it.uppercase() })

/** Which icon color is switched on now (black until the person picks another). */
fun currentIconKey(context: Context): String {
    val pm = context.packageManager
    for (choice in iconChoices) {
        val state = pm.getComponentEnabledSetting(entry(context, choice.key))
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && choice.key == "black")) return choice.key
    }
    return "black"
}

/** Switches the home-screen icon. The new one is turned on first so the app never disappears from the home screen. */
fun setIconKey(context: Context, key: String) {
    val pm = context.packageManager
    pm.setComponentEnabledSetting(entry(context, key), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    for (choice in iconChoices) {
        if (choice.key != key) {
            pm.setComponentEnabledSetting(entry(context, choice.key), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
