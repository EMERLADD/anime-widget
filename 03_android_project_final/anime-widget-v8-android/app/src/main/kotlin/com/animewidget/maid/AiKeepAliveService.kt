package com.animewidget.maid

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Keeps the process important during the 60-second model reuse window. */
class AiKeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stop = Runnable { stopSelf() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(stop)
        handler.postDelayed(stop, KEEP_ALIVE_MILLIS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stop)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val KEEP_ALIVE_MILLIS = 60_000L
        fun touch(context: Context) {
            context.startService(Intent(context, AiKeepAliveService::class.java))
        }
    }
}
