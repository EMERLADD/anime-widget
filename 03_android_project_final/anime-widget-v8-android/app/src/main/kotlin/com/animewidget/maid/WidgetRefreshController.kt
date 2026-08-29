package com.animewidget.maid

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** Native Widget publication plus short Android location acquisition. */
object WidgetRefreshController {
    private const val FRESH_LOCATION_MAX_AGE_MS = 60_000L
    private const val CURRENT_LOCATION_TIMEOUT_MS = 3_000L

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
