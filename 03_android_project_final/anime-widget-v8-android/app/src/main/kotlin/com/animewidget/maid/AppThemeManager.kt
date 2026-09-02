package com.animewidget.maid

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/** Applies the app palette without starting Python or touching functional preview colors. */
object AppThemeManager {
    const val SKIP_TAG = "anime_widget_skip_app_theme"
    private data class Palette(val primary: Int, val secondary: Int, val background: Int,
        val surface: Int, val onPrimary: Int, val text: Int, val outline: Int)
    private val classic = Palette(Color.rgb(72, 164, 242), Color.rgb(174, 220, 250),
        Color.rgb(238, 248, 255), Color.WHITE, Color.WHITE,
        Color.rgb(18, 22, 30), Color.rgb(123, 175, 212))

    fun apply(activity: Activity) {
        val palette = readPalette(activity.filesDir)
        activity.window.statusBarColor = palette.primary
        activity.window.navigationBarColor = palette.background
        activity.window.decorView.setBackgroundColor(palette.background)
        activity.findViewById<ViewGroup>(android.R.id.content)?.let {
            it.setBackgroundColor(palette.background)
            styleTree(it, palette)
        }
    }

    private fun readPalette(filesDir: File): Palette = runCatching {
        val data = File(filesDir, "anime-widget/data")
        val preferences = JSONObject(File(data, "theme_preferences.json").readText())
        if (!preferences.optBoolean("app_follow_theme", true)) return classic
        val resolved = JSONObject(File(data, "theme.json").readText()).getJSONObject("resolved")
        Palette(resolved.color("app_primary", classic.primary), resolved.color("app_secondary", classic.secondary),
            resolved.color("app_background", classic.background), resolved.color("app_surface", classic.surface),
            resolved.color("app_on_primary", classic.onPrimary), resolved.color("app_text", classic.text),
            resolved.color("app_outline", classic.outline))
    }.getOrDefault(classic)

    private fun JSONObject.color(key: String, fallback: Int): Int =
        optString(key).takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }?.let(Color::parseColor) ?: fallback

    private fun styleTree(view: View, palette: Palette) {
        if (view.tag == SKIP_TAG) return
        when (view) {
            is CompoundButton -> { view.setTextColor(palette.text); view.buttonTintList = controlStates(palette) }
            is Button -> { view.background = rounded(palette.primary, palette.outline); view.setTextColor(palette.onPrimary) }
            is EditText -> { view.setTextColor(palette.text); view.setHintTextColor(withAlpha(palette.text, 150)); view.background = rounded(palette.surface, palette.outline) }
            is Spinner -> view.backgroundTintList = ColorStateList.valueOf(palette.secondary)
            is TextView -> view.setTextColor(palette.text)
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) styleTree(view.getChildAt(index), palette)
    }

    private fun rounded(fill: Int, outline: Int) = GradientDrawable().apply { cornerRadius = 12f; setColor(fill); setStroke(1, outline) }
    private fun controlStates(palette: Palette) = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(palette.primary, palette.secondary))
    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
