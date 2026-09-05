package com.animewidget.maid

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat

/**
 * 首次添加 Widget 时系统会调起本 Activity（由 anime_widget_info.xml 的
 * android:configure 属性绑定）。
 *
 * 协议要求：
 * 1. 必须从 Intent 中读出 EXTRA_APPWIDGET_ID。
 * 2. 无论用户是否授权定位，最终都要 setResult(RESULT_OK, 带上该 ID 的 Intent)，
 *    否则系统会判定配置失败并直接删除刚添加的 Widget。
 */
class ConfigActivity : Activity() {

    private val REQUEST_LOCATION = 1001
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppThemeManager.apply(this)

        // 先置为取消结果：如果 Activity 中途被杀，系统按"取消"处理，不会留下残缺 Widget。
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val hasFine = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine) {
            finishOk()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION
            )
        }
    }

    override fun onResume() { super.onResume(); AppThemeManager.apply(this) }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 无论是否授权都完成配置；未授权时强制刷新会明确失败。
        finishOk()
    }

    private fun finishOk() {
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)

        // 首次配置完成后立即触发一次渲染，用户不用等 60 秒 AlarmManager 第一次触发
        val manager = AppWidgetManager.getInstance(this)
        val updateIntent = Intent(this, AnimeMaidWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_IDS,
                intArrayOf(appWidgetId)
            )
        }
        sendBroadcast(updateIntent)

        finish()
    }
}
