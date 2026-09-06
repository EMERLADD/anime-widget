package com.animewidget.maid

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.abs

/** Native Widget publication plus short Android location acquisition. */
object WidgetRefreshController {
    private const val LOCATION_TIME_TIE_MS = 10_000L
    private const val CURRENT_LOCATION_TIMEOUT_MS = 10_000L
    private const val LOCATION_PREFS = "widget_location_cache"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_ACCURACY = "accuracy"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_SAVED_AT = "saved_at"

    fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, AnimeMaidWidget::class.java))

    private fun createViews(context: Context, widgetId: Int, refreshing: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.anime_widget_layout)
        val refreshIntent = Intent(context, AnimeMaidWidget::class.java).apply {
            action = AnimeMaidWidget.ACTION_MANUAL_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            10_000 + widgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.refresh_image, pendingIntent)
        val questionIntent = Intent(context, AiQuestionActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val questionPendingIntent = PendingIntent.getActivity(
            context,
            20_000 + widgetId,
            questionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.ai_input_touch, questionPendingIntent)
        views.setImageViewResource(
            R.id.refresh_image,
            if (refreshing) R.drawable.waiting_icon else R.drawable.refresh_icon
        )
        applyRefreshBackground(context, views)
        views.setContentDescription(
            R.id.refresh_image,
            context.getString(if (refreshing) R.string.refreshing_weather else R.string.refresh_weather)
        )
        applyLayoutTranslations(context, widgetId, views)
        return views
    }

    private fun applyRefreshBackground(context: Context, views: RemoteViews) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val themeFile = File(context.filesDir, "anime-widget/data/theme.json")
        val color = runCatching {
            val value = JSONObject(themeFile.readText()).optJSONObject("resolved")
                ?.optString("refresh_background").orEmpty()
            if (value.isBlank()) null else Color.parseColor(value)
        }.getOrNull() ?: return
        views.setColorStateList(R.id.refresh_image, "setBackgroundTintList", ColorStateList.valueOf(color))
    }

    private fun applyLayoutTranslations(context: Context, widgetId: Int, views: RemoteViews) {
        val manager = AppWidgetManager.getInstance(context)
        val options = manager.getAppWidgetOptions(widgetId)
        val density = context.resources.displayMetrics.density
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300) * density
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200) * density
        val content = WidgetCoordinateMapper.fitCenter(width, height)
        val layout = WidgetLayoutCache.read(context)
        val scale = WidgetModuleSpecs.designScale(content)

        fun place(viewId: Int, name: String, spec: WidgetModuleSpec, baseWidthDp: Float, baseHeightDp: Float) {
            val visible = layout.visible(name)
            views.setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)
            if (!visible) return
            val point = WidgetCoordinateMapper.toPixel(layout.point(name), content)
            val size = WidgetCoordinateMapper.sizeToPixel(spec, content, layout.scale(name))
            val baseWidthPx = baseWidthDp * density
            val baseHeightPx = baseHeightDp * density
            var translationX = point.x
            var translationY = point.y

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setViewLayoutWidth(viewId, size.x, TypedValue.COMPLEX_UNIT_PX)
                views.setViewLayoutHeight(viewId, size.y, TypedValue.COMPLEX_UNIT_PX)
                views.setFloat(viewId, "setScaleX", 1f)
                views.setFloat(viewId, "setScaleY", 1f)
            } else {
                val scaleX = if (baseWidthPx > 0f) size.x / baseWidthPx else 1f
                val scaleY = if (baseHeightPx > 0f) size.y / baseHeightPx else 1f
                views.setFloat(viewId, "setScaleX", scaleX)
                views.setFloat(viewId, "setScaleY", scaleY)
                translationX += (size.x - baseWidthPx) / 2f
                translationY += (size.y - baseHeightPx) / 2f
            }
            views.setFloat(viewId, "setTranslationX", translationX)
            views.setFloat(viewId, "setTranslationY", translationY)
        }

        place(R.id.widget_clock, "clock", WidgetModuleSpecs.clock, 120f, 45f)
        place(R.id.refresh_image, "refresh", WidgetModuleSpecs.refresh, 44f, 44f)
        place(R.id.ai_input_touch, "ai_input", WidgetModuleSpecs.aiInput, 108f, 18f)
        views.setTextViewTextSize(
            R.id.widget_clock,
            TypedValue.COMPLEX_UNIT_PX,
            WidgetModuleSpecs.clock.textSize * scale * layout.scale("clock") / 100f
        )
        val refreshPadding = (WidgetModuleSpecs.refresh.padding * scale * layout.scale("refresh") / 100f).toInt().coerceAtLeast(0)
        views.setViewPadding(
            R.id.refresh_image,
            refreshPadding, refreshPadding, refreshPadding, refreshPadding
        )
    }

    fun publishControls(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        refreshing: Boolean,
        partial: Boolean
    ) {
        val views = createViews(context, widgetId, refreshing)
        if (partial) manager.partiallyUpdateAppWidget(widgetId, views)
        else manager.updateAppWidget(widgetId, views)
    }

    fun publishControlsToAll(context: Context, refreshing: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        for (id in widgetIds(context)) publishControls(context, manager, id, refreshing, partial = true)
    }

    fun publishCachedImage(context: Context, ids: IntArray = widgetIds(context)): Boolean {
        val file = File(context.filesDir, "anime-widget/cache/widget.png")
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        val manager = AppWidgetManager.getInstance(context)
        val refreshing = RefreshStateStore.isRefreshing(context)
        for (id in ids) {
            val views = createViews(context, id, refreshing)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            manager.updateAppWidget(id, views)
        }
        return true
    }

    fun publishRenderedImageToAll(context: Context, pngPath: String, refreshing: Boolean) {
        val bitmap = BitmapFactory.decodeFile(pngPath)
            ?: throw IllegalStateException("Unable to decode rendered Widget image")
        val manager = AppWidgetManager.getInstance(context)
        for (id in widgetIds(context)) {
            val views = createViews(context, id, refreshing)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            manager.updateAppWidget(id, views)
        }
    }

    suspend fun getCurrentOrLastLocation(context: Context): Location? {
        val fine = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val manager = try {
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val enabledProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).filter { provider ->
            try {
                manager.isProviderEnabled(provider)
            } catch (_: RuntimeException) {
                false
            }
        }
        val activeProviders = enabledProviders.filter {
            it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER
        }
        val currentLocations = coroutineScope {
            activeProviders.map { provider ->
                async {
                    withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
                        awaitLocation(context, manager, provider)
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return bestLocation(currentLocations)?.also {
            saveLocation(context, it)
        }
    }

    suspend fun getLocationForAutoRefresh(context: Context): Location? {
        return getCurrentOrLastLocation(context)
    }

    fun getSavedLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SAVED_AT) || !prefs.contains(KEY_LATITUDE) ||
            !prefs.contains(KEY_LONGITUDE)
        ) return null
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        val latitude = Double.fromBits(prefs.getLong(KEY_LATITUDE, 0L))
        val longitude = Double.fromBits(prefs.getLong(KEY_LONGITUDE, 0L))
        if (savedAt <= 0L || (latitude == 0.0 && longitude == 0.0) ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return null
        return Location(prefs.getString(KEY_PROVIDER, null) ?: "saved").apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = prefs.getFloat(KEY_ACCURACY, Float.MAX_VALUE)
            time = savedAt
        }
    }

    private fun saveLocation(context: Context, location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        if ((latitude == 0.0 && longitude == 0.0) ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return
        context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LATITUDE, latitude.toBits())
            .putLong(KEY_LONGITUDE, longitude.toBits())
            .putFloat(KEY_ACCURACY, locationAccuracy(location))
            .putString(KEY_PROVIDER, location.provider.orEmpty())
            .putLong(KEY_SAVED_AT, location.time.takeIf { it > 0L } ?: System.currentTimeMillis())
            .apply()
    }

    private fun locationAgeMs(location: Location): Long {
        if (location.elapsedRealtimeNanos > 0L) {
            val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            return (ageNanos / 1_000_000L).coerceAtLeast(0L)
        }
        return (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
    }

    private fun bestLocation(locations: Collection<Location>): Location? =
        locations.minWithOrNull(Comparator { first, second ->
            val firstAge = locationAgeMs(first)
            val secondAge = locationAgeMs(second)
            if (abs(firstAge - secondAge) > LOCATION_TIME_TIE_MS) {
                firstAge.compareTo(secondAge)
            } else {
                val accuracyComparison = locationAccuracy(first).compareTo(locationAccuracy(second))
                if (accuracyComparison != 0) accuracyComparison else firstAge.compareTo(secondAge)
            }
        })

    private fun locationAccuracy(location: Location): Float {
        val accuracy = location.accuracy
        return if (location.hasAccuracy() && accuracy.isFinite() && accuracy >= 0f) {
            accuracy
        } else {
            Float.POSITIVE_INFINITY
        }
    }

    private suspend fun awaitLocation(
        context: Context,
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                manager.getCurrentLocation(provider, cancellation, context.mainExecutor) {
                    if (continuation.isActive) continuation.resume(it)
                }
            } catch (_: RuntimeException) {
                if (continuation.isActive) continuation.resume(null)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (continuation.isActive) continuation.resume(location)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) {
                    runCatching { manager.removeUpdates(this) }
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: RuntimeException) {
                runCatching { manager.removeUpdates(listener) }
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}
