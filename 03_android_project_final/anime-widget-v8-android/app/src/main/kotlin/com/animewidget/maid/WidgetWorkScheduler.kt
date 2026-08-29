package com.animewidget.maid

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Durable scheduling for periodic, manual, render-only, and spinner-watchdog work. */
object WidgetWorkScheduler {
    const val MODE_AUTO = "auto"
    const val MODE_MANUAL = "manual"
    const val MODE_RENDER = "render"
    const val KEY_MODE = "mode"
    const val KEY_TOKEN = "token"

    private const val PERIODIC_NAME = "anime_widget_periodic_v12"
    private const val IMMEDIATE_NAME = "anime_widget_immediate_v12"
    private const val RENDER_NAME = "anime_widget_render_v12"

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(KEY_MODE, MODE_AUTO).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun enqueueInitial(context: Context) {
        enqueueImmediate(context, MODE_AUTO, "", ExistingWorkPolicy.KEEP)
    }

    fun enqueueManual(context: Context, token: String) {
        enqueueImmediate(context, MODE_MANUAL, token, ExistingWorkPolicy.REPLACE)
        val watchdog = OneTimeWorkRequestBuilder<RefreshWatchdogWorker>()
            .setInitialDelay(RefreshStateStore.ACTIVE_TTL_MS, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_TOKEN, token).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "anime_widget_watchdog_$token", ExistingWorkPolicy.REPLACE, watchdog
        )
    }

    fun enqueueRender(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(Data.Builder().putString(KEY_MODE, MODE_RENDER).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RENDER_NAME, ExistingWorkPolicy.REPLACE, request
        )
    }

    private fun enqueueImmediate(
        context: Context,
        mode: String,
        token: String,
        policy: ExistingWorkPolicy
    ) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(Data.Builder().putString(KEY_MODE, mode).putString(KEY_TOKEN, token).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_NAME, policy, request)
    }

    fun cancelAll(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(PERIODIC_NAME)
        manager.cancelUniqueWork(IMMEDIATE_NAME)
        manager.cancelUniqueWork(RENDER_NAME)
        RefreshStateStore.clear(context)
    }
}
