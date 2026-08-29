package com.animewidget.maid

import android.content.Context
import java.util.UUID

/** Persistent manual-refresh guard. A stale spinner never blocks the next attempt forever. */
object RefreshStateStore {
    enum class StartResult { STARTED, ALREADY_RUNNING, TOO_SOON }
    data class Start(val result: StartResult, val token: String? = null)

    private const val PREFS = "widget_refresh_state"
    private const val ACTIVE_TOKEN = "active_token"
    private const val STARTED_AT = "started_at"
    private const val LAST_STARTED_AT = "last_started_at"
    const val ACTIVE_TTL_MS = 60_000L
    private const val MIN_INTERVAL_MS = 5_000L

    @Synchronized
    fun tryStart(context: Context): Start {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val token = prefs.getString(ACTIVE_TOKEN, null)
        val age = now - prefs.getLong(STARTED_AT, 0L)
        if (token != null && age in 0 until ACTIVE_TTL_MS) {
            return Start(StartResult.ALREADY_RUNNING)
        }
        val sinceLast = now - prefs.getLong(LAST_STARTED_AT, 0L)
        if (sinceLast in 0 until MIN_INTERVAL_MS) return Start(StartResult.TOO_SOON)

        val nextToken = UUID.randomUUID().toString()
        prefs.edit()
            .putString(ACTIVE_TOKEN, nextToken)
            .putLong(STARTED_AT, now)
            .putLong(LAST_STARTED_AT, now)
            .apply()
        return Start(StartResult.STARTED, nextToken)
    }

    @Synchronized
    fun isRefreshing(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(ACTIVE_TOKEN, null) ?: return false
        val age = System.currentTimeMillis() - prefs.getLong(STARTED_AT, 0L)
        if (age !in 0 until ACTIVE_TTL_MS) {
            prefs.edit().remove(ACTIVE_TOKEN).remove(STARTED_AT).apply()
            return false
        }
        return token.isNotBlank()
    }

    @Synchronized
    fun finish(context: Context, token: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(ACTIVE_TOKEN, null) != token) return false
        prefs.edit().remove(ACTIVE_TOKEN).remove(STARTED_AT).apply()
        return true
    }

    @Synchronized
    fun expire(context: Context, token: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(ACTIVE_TOKEN, null) != token) return false
        val age = System.currentTimeMillis() - prefs.getLong(STARTED_AT, 0L)
        if (age in 0 until ACTIVE_TTL_MS) return false
        prefs.edit().remove(ACTIVE_TOKEN).remove(STARTED_AT).apply()
        return true
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(ACTIVE_TOKEN).remove(STARTED_AT).apply()
    }
}
