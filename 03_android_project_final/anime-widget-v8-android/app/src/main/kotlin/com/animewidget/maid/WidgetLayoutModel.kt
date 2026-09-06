package com.animewidget.maid

import android.content.Context
import org.json.JSONObject
import kotlin.math.roundToInt

data class DesignPoint(val x: Int, val y: Int)
data class PixelPoint(val x: Float, val y: Float)
data class ContentRect(val left: Float, val top: Float, val width: Float, val height: Float)
data class WidgetModuleSpec(
    val width: Int,
    val height: Int,
    val textSize: Int = 0,
    val padding: Int = 0
)

object WidgetModuleSpecs {
    val clock = WidgetModuleSpec(width = 480, height = 180, textSize = 72)
    val refresh = WidgetModuleSpec(width = 176, height = 176, padding = 36)
    val aiInput = WidgetModuleSpec(width = 430, height = 72)

    fun designScale(rect: ContentRect): Float = if (rect.width > 0f) {
        rect.width / WidgetLayoutCodec.DESIGN_WIDTH
    } else {
        0f
    }
}

data class WidgetLayout(
    val points: Map<String, DesignPoint>,
    val scales: Map<String, Int>,
    val visibility: Map<String, Boolean>
) {
    fun point(name: String): DesignPoint = points[name] ?: WidgetLayoutCodec.defaultPoint(name)
    fun scale(name: String): Int = scales[name] ?: 100
    fun visible(name: String): Boolean = visibility[name] ?: true
}

object WidgetLayoutCodec {
    const val DESIGN_WIDTH = 1200f
    const val DESIGN_HEIGHT = 800f

    private val defaults = mapOf(
        "date" to DesignPoint(48, 42),
        "clock" to DesignPoint(48, 118),
        "location" to DesignPoint(50, 315),
        "weather" to DesignPoint(48, 425),
        "forecast" to DesignPoint(48, 655),
        "refresh" to DesignPoint(976, 8),
        "character" to DesignPoint(382, 194),
        "bubble" to DesignPoint(500, 170),
        "ai_input" to DesignPoint(735, 704)
    )

    fun defaultPoint(name: String): DesignPoint = defaults[name] ?: DesignPoint(0, 0)

    fun parse(json: String): WidgetLayout {
        val root = runCatching { JSONObject(json) }.getOrNull()
        val points = defaults.mapValues { (name, fallback) ->
            val sourceName = if (name == "character") "maid" else name
            val item = root?.optJSONObject(sourceName)
            val xKey = if (name == "bubble") "x1" else "x"
            val yKey = if (name == "bubble") "y1" else "y"
            DesignPoint(item.safeInt(xKey, fallback.x), item.safeInt(yKey, fallback.y))
        }
        val scales = defaults.mapValues { (name, _) ->
            val sourceName = if (name == "character") "maid" else name
            root?.optJSONObject(sourceName).safeInt("scale", 100).coerceIn(50, 150)
        }
        val visibility = defaults.mapValues { (name, _) ->
            val sourceName = if (name == "character") "maid" else name
            root?.optJSONObject(sourceName).safeInt("visible", 1) != 0
        }
        return WidgetLayout(points, scales, visibility)
    }

    private fun JSONObject?.safeInt(key: String, fallback: Int): Int {
        if (this == null || !has(key)) return fallback
        return runCatching { get(key).toString().toInt() }.getOrDefault(fallback)
    }
}

data class ModuleGeometry(
    val frameWidth: Int,
    val frameHeight: Int,
    val visualLeft: Int,
    val visualTop: Int,
    val visualWidth: Int,
    val visualHeight: Int
)

object WidgetGeometryCodec {
    fun parse(json: String): Map<String, ModuleGeometry> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val source = root.optJSONObject("modules") ?: root
        val result = mutableMapOf<String, ModuleGeometry>()
        val names = listOf(
            "date", "clock", "location", "weather", "forecast", "bubble",
            "character", "refresh", "ai_input"
        )
        for (name in names) {
            val item = source.optJSONObject(name)
                ?: if (name == "character") source.optJSONObject("maid") else null
                ?: continue
            val frame = item.optJSONObject("frame") ?: continue
            val visual = item.optJSONObject("visual") ?: continue
            val frameWidth = frame.dimension("width") ?: continue
            val frameHeight = frame.dimension("height") ?: continue
            val visualWidth = visual.dimension("width") ?: continue
            val visualHeight = visual.dimension("height") ?: continue
            result[name] = ModuleGeometry(
                frameWidth, frameHeight,
                visual.coordinate("left"), visual.coordinate("top"),
                visualWidth, visualHeight
            )
        }
        return result
    }

    private fun JSONObject.dimension(key: String): Int? {
        if (!has(key)) return null
        return optDouble(key, Double.NaN).takeIf { it.isFinite() && it > 0.0 }?.roundToInt()
    }

    private fun JSONObject.coordinate(key: String): Int =
        optDouble(key, 0.0).takeIf { it.isFinite() }?.roundToInt() ?: 0
}

object WidgetCoordinateMapper {
    fun toPixel(point: DesignPoint, rect: ContentRect): PixelPoint = PixelPoint(
        rect.left + point.x / WidgetLayoutCodec.DESIGN_WIDTH * rect.width,
        rect.top + point.y / WidgetLayoutCodec.DESIGN_HEIGHT * rect.height
    )

    fun sizeToPixel(spec: WidgetModuleSpec, rect: ContentRect, scalePercent: Int = 100): PixelPoint {
        val scale = WidgetModuleSpecs.designScale(rect) * scalePercent.coerceIn(50, 150) / 100f
        return PixelPoint(spec.width * scale, spec.height * scale)
    }

    fun fitCenter(containerWidth: Float, containerHeight: Float): ContentRect {
        if (containerWidth <= 0f || containerHeight <= 0f) return ContentRect(0f, 0f, 0f, 0f)
        val scale = minOf(
            containerWidth / WidgetLayoutCodec.DESIGN_WIDTH,
            containerHeight / WidgetLayoutCodec.DESIGN_HEIGHT
        )
        val width = WidgetLayoutCodec.DESIGN_WIDTH * scale
        val height = WidgetLayoutCodec.DESIGN_HEIGHT * scale
        return ContentRect((containerWidth - width) / 2f, (containerHeight - height) / 2f, width, height)
    }
}

object WidgetLayoutCache {
    private const val PREFS = "widget_layout_cache"
    private const val KEY_JSON = "layout_json"

    fun read(context: Context): WidgetLayout = WidgetLayoutCodec.parse(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, "{}") ?: "{}"
    )

    fun write(context: Context, json: String): WidgetLayout {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_JSON, json).apply()
        return WidgetLayoutCodec.parse(json)
    }
}
