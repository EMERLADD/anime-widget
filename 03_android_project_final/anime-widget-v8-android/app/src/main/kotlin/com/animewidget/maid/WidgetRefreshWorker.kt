package com.animewidget.maid

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Runs network, Chaquopy and Pillow outside BroadcastReceiver's short lifetime. */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private val processRefreshMutex = Mutex()
    }

    override suspend fun doWork(): Result = processRefreshMutex.withLock {
        val mode = inputData.getString(WidgetWorkScheduler.KEY_MODE) ?: WidgetWorkScheduler.MODE_AUTO
        val token = inputData.getString(WidgetWorkScheduler.KEY_TOKEN).orEmpty()
        if (WidgetRefreshController.widgetIds(applicationContext).isEmpty()) {
            if (token.isNotBlank()) RefreshStateStore.finish(applicationContext, token)
            return@withLock Result.success()
        }

        try {
            val location = when (mode) {
                WidgetWorkScheduler.MODE_RENDER -> null
                WidgetWorkScheduler.MODE_MANUAL ->
                    WidgetRefreshController.getCurrentOrLastLocation(applicationContext)
                else -> WidgetRefreshController.getLocationForAutoRefresh(applicationContext)
            }
            val refreshResult = withContext(Dispatchers.IO) {
                when (mode) {
                    WidgetWorkScheduler.MODE_RENDER -> PythonBridge.RefreshResult(
                        true, PythonBridge.renderWidgetPng(applicationContext), ""
                    )
                    WidgetWorkScheduler.MODE_MANUAL ->
                        PythonBridge.manualRefreshAndRenderWidget(applicationContext, location)
                    else -> PythonBridge.fullRefreshAndRenderWidget(applicationContext, location)
                }
            }
            val pngPath = refreshResult.pngPath
                ?: throw IllegalStateException("Python renderer returned no image")

            val keepManualSpinner = mode != WidgetWorkScheduler.MODE_MANUAL &&
                RefreshStateStore.isRefreshing(applicationContext)
            WidgetRefreshController.publishRenderedImageToAll(
                applicationContext, pngPath, refreshing = keepManualSpinner
            )
            if (!refreshResult.refreshSuccess) {
                throw IllegalStateException(refreshResult.error.ifBlank { "数据强制刷新失败" })
            }
            Result.success()
        } catch (_: Exception) {
            if (mode == WidgetWorkScheduler.MODE_MANUAL ||
                !RefreshStateStore.isRefreshing(applicationContext)
            ) {
                WidgetRefreshController.publishControlsToAll(applicationContext, refreshing = false)
            }
            if (mode == WidgetWorkScheduler.MODE_AUTO && runAttemptCount < 2) Result.retry()
            else Result.failure()
        } finally {
            if (token.isNotBlank() && RefreshStateStore.finish(applicationContext, token)) {
                WidgetRefreshController.publishControlsToAll(applicationContext, refreshing = false)
            }
        }
    }
}
