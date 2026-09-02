package com.animewidget.maid

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "关于"
        setContentView(buildUi())
        AppThemeManager.apply(this)
    }

    override fun onResume() { super.onResume(); AppThemeManager.apply(this) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "未知"
    }.getOrDefault("未知")

    private fun LinearLayout.heading(value: String) {
        addView(TextView(context).apply {
            text = value
            textSize = 20f
            setTextColor(Color.rgb(25, 70, 130))
            setPadding(0, dp(20), 0, dp(7))
        })
    }

    private fun LinearLayout.paragraph(value: String) {
        addView(TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(Color.rgb(35, 45, 60))
            setLineSpacing(0f, 1.18f)
            setPadding(0, 0, 0, dp(9))
        })
    }

    private fun LinearLayout.qa(question: String, answer: String) {
        addView(TextView(context).apply {
            text = question
            textSize = 16f
            setTextColor(Color.rgb(24, 86, 150))
            setPadding(0, dp(9), 0, dp(3))
        })
        paragraph(answer)
    }

    private fun LinearLayout.linkButton(label: String, url: String) {
        addView(Button(context).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { openUrl(url) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            bottomMargin = dp(5)
        })
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "未找到可打开网页的应用", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildUi() = ScrollView(this).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(32))
            addView(TextView(context).apply {
                text = "Anime Maid Widget"
                textSize = 27f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(24, 86, 150))
            })
            addView(TextView(context).apply {
                text = "版本 ${versionName()}"
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, dp(10))
            })
            heading("程序简介")
            paragraph("一款可自定义角色、对话、布局、天气数据源与主题颜色的 Android 桌面天气小组件。天气、地点和对话支持定时及手动刷新。")
            heading("API 常见问题")
            qa("什么是 API？", "API 可以理解为软件之间交换数据的接口。本程序通过天气 API 获取温度、天气状况和预报信息。")
            qa("一定要配置 API 吗？", "不一定。默认的 Open-Meteo 不要求填写 API Key；选择其他天气源时，通常需要自行注册并配置凭据。")
            qa("什么是 API Key？", "API Key 是天气服务商分配的访问凭据，类似程序专用密码。请勿截图公开、分享或提交到公共代码仓库。")
            qa("如何注册天气 API？", "前往下方服务商官网注册开发者账号，创建应用或项目，再申请相应凭据。免费额度和注册流程以服务商官网为准。")
            qa("为什么配置后仍然获取失败？", "常见原因包括 Key 或 API Host 填写错误、额度耗尽、网络或证书异常、定位权限未开启，以及城市 adcode 不正确。服务商接口调整也可能导致暂时失败。")
            qa("什么是自定义 JSON API？", "这是面向高级用户的入口，可接入其他天气接口并把返回字段映射为程序内部格式；普通用户无需配置。")
            heading("天气服务商官网")
            linkButton("Open-Meteo 天气 API 文档", "https://open-meteo.com/en/docs")
            linkButton("和风天气：开始使用", "https://dev.qweather.com/docs/start/")
            linkButton("和风天气：项目与凭据", "https://dev.qweather.com/docs/configuration/project-and-key/")
            linkButton("高德天气查询 API", "https://lbs.amap.com/api/webservice/guide/api/weatherinfo")
            linkButton("高德开放平台控制台", "https://console.amap.com/dev/id/select")
            heading("第三方服务说明")
            paragraph("本程序不提供、转售或代为申请第三方天气服务凭据。服务额度、费用、隐私政策和使用条款均由对应服务商决定，请在使用前阅读其官方说明。")
        })
    }
}
