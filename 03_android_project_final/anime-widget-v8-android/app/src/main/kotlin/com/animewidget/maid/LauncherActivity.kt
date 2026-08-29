package com.animewidget.maid

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Main settings menu. Feature pages stay independent and easier to extend. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Anime Maid Widget v1.4"
        setContentView(buildUi())
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

    private fun unfinishedButton(title: String, subtitle: String) =
        menuButton(title + "（未完工）", subtitle) {
            Toast.makeText(this, "该功能已预留，将在后续版本实现", Toast.LENGTH_SHORT).show()
        }.apply {
            alpha = 0.72f
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
                text = "v1.4 设置主菜单"
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

            addView(TextView(context).apply {
                text = "外观资源"
                textSize = 19f
                setTextColor(Color.rgb(25, 70, 130))
                setPadding(0, dp(20), 0, dp(6))
            })
            addView(unfinishedButton("替换图标", "预留刷新图标与等待图标替换入口"),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
            addView(unfinishedButton("替换角色", "预留 character.png 替换与主题取色入口"),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
            addView(TextView(context).apply {
                text = "未完工提醒：图标替换和角色替换本版仅建立菜单入口，尚不会读取或写入图片。"
                textSize = 12f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(12), 0, 0)
            })
        })
    }
}
