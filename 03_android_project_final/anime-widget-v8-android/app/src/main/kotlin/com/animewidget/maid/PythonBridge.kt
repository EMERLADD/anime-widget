package com.animewidget.maid

import android.content.Context
import android.location.Location
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

/** Serialized Kotlin/Python bridge for the single Chaquopy interpreter and shared cache files. */
object PythonBridge {
    data class RefreshResult(
        val refreshSuccess: Boolean,
        val pngPath: String?,
        val error: String
    )

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
    fun manualRefreshAndRenderWidget(context: Context, location: Location?): RefreshResult {
        ensureStarted(context)
        return refreshResult(context, "manual_refresh_and_render_result_json", location)
    }

    @Synchronized
    fun fullRefreshAndRenderWidget(context: Context, location: Location?): RefreshResult {
        ensureStarted(context)
        return refreshResult(context, "full_refresh_and_render_result_json", location)
    }

    private fun refreshResult(context: Context, function: String, location: Location?): RefreshResult {
        val bridge = Python.getInstance().getModule("bridge")
        val credentials = AmapCredentialStore.load(context)
        val raw = if (location != null) {
            bridge.callAttr(
                function,
                location.latitude,
                location.longitude,
                null,
                if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                location.provider.orEmpty(),
                location.time,
                credentials?.apiKey.orEmpty(),
                credentials?.privateKey.orEmpty()
            )
        } else {
            bridge.callAttr(function)
        }.toString()
        val result = JSONObject(raw)
        return RefreshResult(
            refreshSuccess = result.optBoolean("refresh_success", false),
            pngPath = result.optString("png_path").ifBlank { null },
            error = result.optString("error")
        )
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
    fun testWeatherConfigJson(
        context: Context,
        json: String,
        credentials: AmapCredentialStore.Credentials? = AmapCredentialStore.load(context)
    ): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr(
                "test_weather_config_json", json, 31.27, 121.50,
                credentials?.apiKey.orEmpty(), credentials?.privateKey.orEmpty()
            ).toString()
    }

    @Synchronized
    fun testAmapGeocodingJson(
        context: Context,
        latitude: Double,
        longitude: Double,
        credentials: AmapCredentialStore.Credentials
    ): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge").callAttr(
            "test_amap_geocoding_json", latitude, longitude,
            credentials.apiKey, credentials.privateKey
        ).toString()
    }

    @Synchronized
    fun getAiWeatherContextJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_ai_weather_context_json").toString()
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
