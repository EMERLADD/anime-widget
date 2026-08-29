package com.animewidget.maid

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Thin broadcast entrypoint: durable work is delegated immediately to WorkManager. */
class AnimeMaidWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_RENDER = "com.animewidget.maid.ACTION_RENDER"
        const val ACTION_MANUAL_REFRESH = "com.animewidget.maid.ACTION_MANUAL_REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetWorkScheduler.ensurePeriodic(context)
        val hasCachedImage = WidgetRefreshController.publishCachedImage(context, appWidgetIds)
        if (!hasCachedImage) {
            for (id in appWidgetIds) {
                WidgetRefreshController.publishControls(
                    context, appWidgetManager, id, refreshing = true, partial = false
                )
            }
        }
        WidgetWorkScheduler.enqueueInitial(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_RENDER -> WidgetWorkScheduler.enqueueRender(context)
            ACTION_MANUAL_REFRESH -> handleManualRefresh(context, intent)
        }
    }

    private fun handleManualRefresh(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val start = RefreshStateStore.tryStart(context)
        when (start.result) {
            RefreshStateStore.StartResult.ALREADY_RUNNING -> {
                Toast.makeText(context, "正在刷新，请稍候", Toast.LENGTH_SHORT).show()
                return
            }
            RefreshStateStore.StartResult.TOO_SOON -> {
                Toast.makeText(context, "刷新太频繁，请稍后再试", Toast.LENGTH_SHORT).show()
                return
            }
            RefreshStateStore.StartResult.STARTED -> Unit
        }

        Toast.makeText(context, "正在重新整理思绪……", Toast.LENGTH_SHORT).show()
        WidgetRefreshController.publishControlsToAll(context, refreshing = true)
        WidgetWorkScheduler.enqueueManual(context, requireNotNull(start.token))
    }

    override fun onEnabled(context: Context) {
        WidgetWorkScheduler.ensurePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        WidgetWorkScheduler.cancelAll(context)
    }
}
