package com.animewidget.maid

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Clears a stale native spinner even if the original process died mid-refresh. */
class RefreshWatchdogWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val token = inputData.getString(WidgetWorkScheduler.KEY_TOKEN).orEmpty()
        if (token.isNotBlank() && RefreshStateStore.expire(applicationContext, token)) {
            WidgetRefreshController.publishControlsToAll(applicationContext, refreshing = false)
        }
        return Result.success()
    }
}
