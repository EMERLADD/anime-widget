package com.animewidget.maid

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnimeMaidWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_RENDER = "com.animewidget.maid.ACTION_RENDER"
        const val ACTION_MANUAL_REFRESH = "com.animewidget.maid.ACTION_MANUAL_REFRESH"
        const val RENDER_INTERVAL_MS = 60_000L

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun scheduleNextRefresh(context: Context) {
            val pendingIntent = WidgetRefreshController.renderPendingIntent(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + RENDER_INTERVAL_MS,
                pendingIntent
            )
        }

        fun cancelRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(WidgetRefreshController.renderPendingIntent(context))
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            WidgetRefreshController.publishControls(
                context, appWidgetManager, id, refreshing = true, partial = false
            )
        }
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                for (id in appWidgetIds) {
                    try {
                        WidgetRefreshController.initialRefresh(context, appWidgetManager, id)
                    } catch (_: Exception) {
                        WidgetRefreshController.publishControls(
                            context, appWidgetManager, id, refreshing = false, partial = true
                        )
                    }
                }
                scheduleNextRefresh(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_RENDER -> {
                val pendingResult = goAsync()
                widgetScope.launch {
                    try {
                        WidgetRefreshController.renderAllFromCache(context)
                        scheduleNextRefresh(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_MANUAL_REFRESH -> {
                val manager = AppWidgetManager.getInstance(context)
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

                when (WidgetRefreshController.tryStartManualRefresh(widgetId)) {
                    WidgetRefreshController.RefreshStart.ALREADY_RUNNING -> {
                        Toast.makeText(context, "正在刷新，请稍候", Toast.LENGTH_SHORT).show()
                        return
                    }
                    WidgetRefreshController.RefreshStart.TOO_SOON -> {
                        Toast.makeText(context, "刷新太频繁，请稍后再试", Toast.LENGTH_SHORT).show()
                        return
                    }
                    WidgetRefreshController.RefreshStart.STARTED -> Unit
                }

                Toast.makeText(context, "正在重新整理思绪……", Toast.LENGTH_SHORT).show()
                WidgetRefreshController.publishControls(
                    context, manager, widgetId, refreshing = true, partial = true
                )

                val pendingResult = goAsync()
                widgetScope.launch {
                    try {
                        WidgetRefreshController.manualRefresh(context, manager, widgetId)
                        scheduleNextRefresh(context)
                    } catch (_: Exception) {
                        WidgetRefreshController.publishControls(
                            context, manager, widgetId, refreshing = false, partial = true
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "刷新失败，已保留上次内容",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } finally {
                        WidgetRefreshController.finishManualRefresh(widgetId)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    override fun onEnabled(context: Context) = scheduleNextRefresh(context)

    override fun onDisabled(context: Context) = cancelRefresh(context)
}
