package com.animewidget.maid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Main settings menu. Feature pages stay independent and easier to extend. */
class LauncherActivity : Activity() {
    private val requestForegroundLocation = 2001
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var locationWarmupJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Anime Maid Widget v2.0"
        setContentView(buildUi())
        AppThemeManager.apply(this)
    }

    override fun onResume() {
        super.onResume()
        AppThemeManager.apply(this)
        val fine = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if ((fine || coarse) && locationWarmupJob?.isActive != true) {
            locationWarmupJob = activityScope.launch {
                WidgetRefreshController.getCurrentOrLastLocation(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        locationWarmupJob = null
        activityScope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun menuButton(title: String, subtitle: String, action: () -> Unit) =
        Button(this).apply {
            text = title + "\n" + subtitle
            textSize = 17f
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener { action() }
        }

    private fun buildUi() = ScrollView(this).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(30))
            addView(TextView(context).apply {
                text = "Anime Maid Widget"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(24, 86, 150))
            })
            addView(TextView(context).apply {
                text = "v2.0 设置主菜单"
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, dp(18))
            })

            addView(TextView(context).apply {
                text = "内容与数据"
                textSize = 19f
                setTextColor(Color.rgb(25, 70, 130))
                setPadding(0, dp(10), 0, dp(6))
            })
            addView(menuButton("对话管理", "逐条管理内置和自定义对话") {
                startActivity(Intent(this@LauncherActivity, DialogueSettingsActivity::class.java))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
            addView(menuButton("天气 API", "天气源、API Key、自定义 JSON 翻译") {
                startActivity(Intent(this@LauncherActivity, WeatherSettingsActivity::class.java))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
            addView(menuButton("定位权限", "授权精确位置与后台位置，供半小时强制刷新") {
                requestWidgetLocationPermissions()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))

            addView(TextView(context).apply {
                text = "外观资源"
                textSize = 19f
                setTextColor(Color.rgb(25, 70, 130))
                setPadding(0, dp(20), 0, dp(6))
            })
            addView(menuButton("布局编辑", "在应用预览中长按拖动各模块") {
                startActivity(Intent(this@LauncherActivity, LayoutEditorActivity::class.java))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
            addView(menuButton("角色与主题", "导入透明 PNG，并配置自动或手动主题") {
                startActivity(Intent(this@LauncherActivity, CharacterSettingsActivity::class.java))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
            addView(TextView(context).apply {
                text = "帮助"
                textSize = 19f
                setTextColor(Color.rgb(25, 70, 130))
                setPadding(0, dp(20), 0, dp(6))
            })
            addView(menuButton("关于", "程序说明、API 常见问题与服务商官网") {
                startActivity(Intent(this@LauncherActivity, AboutActivity::class.java))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
        })
    }

    private fun requestWidgetLocationPermissions() {
        val fine = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                requestForegroundLocation
            )
            return
        }
        requestBackgroundLocation()
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "定位权限已授权", Toast.LENGTH_LONG).show()
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "前台与后台定位均已授权", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2002
            )
        } else {
            Toast.makeText(this, "请在权限中把位置设为“始终允许”", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestForegroundLocation &&
            grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        ) requestBackgroundLocation()
    }
}
