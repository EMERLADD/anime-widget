package com.animewidget.maid

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Kotlin <-> Python (bridge.py) 胶水层。
 * 不直接触碰 render.py / update_data.py / dialogue.py，全部通过 bridge.py 转调。
 */
object PythonBridge {

    private fun ensureStarted(context: Context) {
        // render.py / update_data.py 都用 Path.home()，Chaquopy 里对应 os.environ["HOME"]。
        // 指向 app 私有目录，首次调用前必须已解压好 anime-widget-data 静态资源。
        System.setProperty("java.io.tmpdir", context.cacheDir.absolutePath)
        val homeDir = AssetInstaller.ensureInstalled(context)
        System.setProperty("user.home", homeDir.absolutePath)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }

        // Chaquopy 的 os.environ 继承自 Java System properties 中未覆盖的部分，
        // 显式在 Python 侧设置更可靠。
        val py = Python.getInstance()
        val osModule = py.getModule("os")
        osModule.get("environ")?.callAttr("__setitem__", "HOME", homeDir.absolutePath)
    }

    private fun getLastKnownLocation(context: Context): Triple<Double, Double, String?>? {
        val hasFine = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)

        for (provider in providers) {
            try {
                if (!lm.isProviderEnabled(provider)) continue
                val loc = lm.getLastKnownLocation(provider) ?: continue
                return Triple(loc.latitude, loc.longitude, null)
            } catch (_: SecurityException) {
                continue
            }
        }
        return null
    }

    /** Render only: used by the 60-second clock tick. No weather request. */
    fun renderWidgetPng(context: Context): String? {
        ensureStarted(context)
        val bridge = Python.getInstance().getModule("bridge")
        val result = bridge.callAttr("render_widget_png")
        val path = result.toString()
        return if (path.isBlank()) null else path
    }

    /** Manual refresh: current location, weather, and dialogue sequence move together. */
    fun manualRefreshAndRenderWidgetPng(context: Context, location: Location?): String? {
        ensureStarted(context)
        val bridge = Python.getInstance().getModule("bridge")
        val result = if (location != null) {
            bridge.callAttr(
                "manual_refresh_and_render",
                location.latitude,
                location.longitude,
                null
            )
        } else {
            bridge.callAttr("manual_refresh_and_render")
        }
        return result.toString().ifBlank { null }
    }

    fun getDialogueConfigJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_dialogue_config_json").toString()
    }

    fun getBuiltinDialogueJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_builtin_dialogue_json").toString()
    }

    fun saveDialogueConfigJson(context: Context, json: String): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("save_dialogue_config_json", json).toString()
    }

    fun getWeatherConfigJson(context: Context): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("get_weather_config_json").toString()
    }

    fun saveWeatherConfigJson(context: Context, json: String): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("save_weather_config_json", json).toString()
    }

    fun testWeatherConfigJson(context: Context, json: String): String {
        ensureStarted(context)
        return Python.getInstance().getModule("bridge")
            .callAttr("test_weather_config_json", json).toString()
    }

    /** Manual ↻: update location/weather first, then render. */
    fun refreshAndRenderWidgetPng(context: Context): String? {
        ensureStarted(context)
        val bridge = Python.getInstance().getModule("bridge")
        val loc = getLastKnownLocation(context)
        val result: PyObject = if (loc != null) {
            bridge.callAttr("full_refresh_and_render", loc.first, loc.second, loc.third)
        } else {
            bridge.callAttr("full_refresh_and_render")
        }
        val path = result.toString()
        return if (path.isBlank()) null else path
    }

}
