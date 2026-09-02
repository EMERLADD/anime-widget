package com.animewidget.maid

import android.content.Context
import android.location.Location
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/** Serialized Kotlin/Python bridge for the single Chaquopy interpreter and shared cache files. */
object PythonBridge {
    private fun ensureStarted(context: Context) {
        System.setProperty("java.io.tmpdir", context.cacheDir.absolutePath)
        val homeDir = AssetInstaller.ensureInstalled(context)
        System.setProperty("user.home", homeDir.absolutePath)
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        Python.getInstance().getModule("os").get("environ")
            ?.callAttr("__setitem__", "HOME", homeDir.absolutePath)
    }

    @Synchronized
    fun renderWidgetPng(context: Context): String? {
        ensureStarted(context)
        val path = Python.getInstance().getModule("bridge")
            .callAttr("render_widget_png").toString()
        return path.ifBlank { null }
    }

    @Synchronized
    fun manualRefreshAndRenderWidgetPng(context: Context, location: Location?): String? {
        ensureStarted(context)
        val bridge = Python.getInstance().getModule("bridge")
        val result = if (location != null) {
            bridge.callAttr("manual_refresh_and_render", location.latitude, location.longitude, null)
        } else {
            bridge.callAttr("manual_refresh_and_render")
        }
        return result.toString().ifBlank { null }
    }

    @Synchronized
    fun fullRefreshAndRenderWidgetPng(context: Context, location: Location?): String? {
        ensureStarted(context)
        val bridge = Python.getInstance().getModule("bridge")
        val result = if (location != null) {
            bridge.callAttr("full_refresh_and_render", location.latitude, location.longitude, null)
        } else {
            bridge.callAttr("full_refresh_and_render")
        }
        return result.toString().ifBlank { null }
    }

    @Synchronized
    fun getDialogueConfigJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_dialogue_config_json").toString()
    }

    @Synchronized
    fun getBuiltinDialogueJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_builtin_dialogue_json").toString()
    }

    @Synchronized
    fun saveDialogueConfigJson(context: Context, json: String): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("save_dialogue_config_json", json).toString()
    }

    @Synchronized
    fun getWeatherConfigJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_weather_config_json").toString()
    }

    @Synchronized
    fun saveWeatherConfigJson(context: Context, json: String): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("save_weather_config_json", json).toString()
    }

    @Synchronized
    fun testWeatherConfigJson(context: Context, json: String): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("test_weather_config_json", json).toString()
    }

    @Synchronized
    fun getLayoutConfigJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_layout_config_json").toString()
    }

    @Synchronized
    fun resetLayoutConfigJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("reset_layout_config_json").toString()
    }

    @Synchronized
    fun updateLayoutPositionsJson(context: Context, json: String): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("update_layout_positions_json", json).toString()
    }
    @Synchronized
    fun getLayoutGeometryJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_layout_geometry_json").toString()
    }

    @Synchronized
    fun getCharacterThemeJson(context: Context): String { ensureStarted(context); return Python.getInstance().getModule("bridge").callAttr("get_character_theme_json").toString() }

    @Synchronized
    fun importCharacterPng(context: Context, path: String): String { ensureStarted(context); return Python.getInstance().getModule("bridge").callAttr("import_character_png", path).toString() }

    @Synchronized
    fun restoreDefaultCharacter(context: Context): String { ensureStarted(context); return Python.getInstance().getModule("bridge").callAttr("restore_default_character").toString() }

    @Synchronized
    fun getThemeExtractionJson(context: Context): String { ensureStarted(context); return Python.getInstance().getModule("bridge").callAttr("get_theme_extraction_json").toString() }

    @Synchronized
    fun saveThemeExtractionJson(context: Context, json: String): String { ensureStarted(context); return Python.getInstance().getModule("bridge").callAttr("save_theme_extraction_json", json).toString() }
    @Synchronized
    fun getThemePreferencesJson(context: Context): String { ensureStarted(context); return Python.getInstance().getModule("bridge").callAttr("get_theme_preferences_json").toString() }

    @Synchronized
    fun saveThemePreferencesJson(context: Context, json: String): String { ensureStarted(context); return Python.getInstance().getModule("bridge").callAttr("save_theme_preferences_json", json).toString() }
}
