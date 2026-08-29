package com.animewidget.maid

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Coordinates the visible refresh state, location, weather, dialogue, and final render. */
object WidgetRefreshController {

    enum class RefreshStart { STARTED, ALREADY_RUNNING, TOO_SOON }

    private const val MIN_REFRESH_INTERVAL_MS = 5_000L
    private const val FRESH_LOCATION_MAX_AGE_MS = 60_000L
    private const val CURRENT_LOCATION_TIMEOUT_MS = 3_000L
    private val refreshStateLock = Any()
    private val refreshingWidgetIds = mutableSetOf<Int>()
    private val lastRefreshStartedAt = mutableMapOf<Int, Long>()

    fun tryStartManualRefresh(widgetId: Int): RefreshStart = synchronized(refreshStateLock) {
        if (widgetId in refreshingWidgetIds) return@synchronized RefreshStart.ALREADY_RUNNING
        val now = SystemClock.elapsedRealtime()
        val last = lastRefreshStartedAt[widgetId] ?: Long.MIN_VALUE
        if (last != Long.MIN_VALUE && now - last < MIN_REFRESH_INTERVAL_MS) {
            return@synchronized RefreshStart.TOO_SOON
        }
        refreshingWidgetIds += widgetId
        lastRefreshStartedAt[widgetId] = now
        RefreshStart.STARTED
    }

    fun finishManualRefresh(widgetId: Int) = synchronized(refreshStateLock) {
        refreshingWidgetIds -= widgetId
    }

    fun renderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AnimeMaidWidget::class.java).apply {
            action = AnimeMaidWidget.ACTION_RENDER
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createViews(context: Context, widgetId: Int, refreshing: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.anime_widget_layout)
        val refreshIntent = Intent(context, AnimeMaidWidget::class.java).apply {
            action = AnimeMaidWidget.ACTION_MANUAL_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            10_000 + widgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.refresh_image, pendingIntent)
        views.setImageViewResource(
            R.id.refresh_image,
            if (refreshing) R.drawable.waiting_icon else R.drawable.refresh_icon
        )
        views.setContentDescription(
            R.id.refresh_image,
            context.getString(if (refreshing) R.string.refreshing_weather else R.string.refresh_weather)
        )
        return views
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

    private suspend fun publishRenderedImage(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        pngPath: String?,
        refreshing: Boolean
    ) {
        val views = createViews(context, widgetId, refreshing)
        if (pngPath != null) {
            val bitmap = withContext(Dispatchers.IO) {
                android.graphics.BitmapFactory.decodeFile(pngPath)
            }
            bitmap?.let { views.setImageViewBitmap(R.id.widget_image, it) }
        }
        manager.updateAppWidget(widgetId, views)
    }

    suspend fun initialRefresh(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val path = withContext(Dispatchers.IO) {
            PythonBridge.refreshAndRenderWidgetPng(context)
        }
        publishRenderedImage(context, manager, widgetId, path, refreshing = false)
    }

    suspend fun renderAllFromCache(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            android.content.ComponentName(context, AnimeMaidWidget::class.java)
        )
        for (id in ids) {
            val path = withContext(Dispatchers.IO) { PythonBridge.renderWidgetPng(context) }
            publishRenderedImage(context, manager, id, path, refreshing = false)
        }
    }

    suspend fun manualRefresh(context: Context, manager: AppWidgetManager, widgetId: Int) {
        // Fast path: the native waiting icon is already visible. Render only the final PNG.
        val location = getCurrentOrLastLocation(context)
        val finalPath = withContext(Dispatchers.IO) {
            PythonBridge.manualRefreshAndRenderWidgetPng(context, location)
        }
        publishRenderedImage(context, manager, widgetId, finalPath, refreshing = false)
    }

    private suspend fun getCurrentOrLastLocation(context: Context): Location? {
        val fine = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val cached = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (cached != null && locationAgeMs(cached) <= FRESH_LOCATION_MAX_AGE_MS) return cached

        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val current = provider?.let {
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) { awaitLocation(context, manager, it) }
        }
        return current ?: cached
    }

    private fun locationAgeMs(location: Location): Long {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return (ageNanos / 1_000_000L).coerceAtLeast(0L)
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
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}
