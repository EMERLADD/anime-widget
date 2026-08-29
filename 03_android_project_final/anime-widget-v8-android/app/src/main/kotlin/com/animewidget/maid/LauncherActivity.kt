package com.animewidget.maid

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** v1.1 settings UI for dialogue and weather provider JSON configuration. */
class LauncherActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var dialogueConfig: JSONObject
    private lateinit var weatherConfig: JSONObject
    private lateinit var modeSpinner: Spinner
    private lateinit var categorySpinner: Spinner
    private lateinit var dialogueEditor: EditText
    private lateinit var providerSpinner: Spinner
    private lateinit var endpointEditor: EditText
    private lateinit var apiKeyEditor: EditText
    private lateinit var adcodeEditor: EditText
    private lateinit var customEditor: EditText
    private lateinit var endpointLabel: TextView
    private var loaded = false
    private var currentCategory = "global"
    private var currentProvider = "open_meteo"
    private var programmaticSelection = false

    private val categoryKeys = arrayOf("global", "morning", "day", "evening", "night", "late_night", "sunny", "cloudy", "overcast", "fog", "drizzle", "rain", "heavy_rain", "snow", "thunder", "hot", "cold")
    private val categoryLabels = arrayOf("通用", "早晨", "白天", "傍晚", "夜晚", "深夜", "晴天", "多云", "阴天", "雾", "毛毛雨", "雨", "大雨", "雪", "雷雨", "高温", "寒冷")
    private val providerKeys = arrayOf("open_meteo", "qweather", "amap", "custom")
    private val providerLabels = arrayOf("Open-Meteo（默认）", "QWeather", "高德天气", "自定义 JSON API")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        scope.launch {
            try {
                val pair = withContext(Dispatchers.IO) {
                    PythonBridge.getDialogueConfigJson(this@LauncherActivity) to
                        PythonBridge.getWeatherConfigJson(this@LauncherActivity)
                }
                dialogueConfig = JSONObject(pair.first)
                weatherConfig = JSONObject(pair.second)
                loaded = true
                showDialogueConfig()
                showWeatherConfig()
            } catch (e: Exception) {
                toast("读取设置失败：${e.message}")
            }
        }
    }

    private fun buildUi(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun title(text: String) = TextView(this).apply {
            this.text = text; textSize = 21f; setTextColor(Color.rgb(25, 70, 130)); setPadding(0, dp(22), 0, dp(8))
        }
        fun label(text: String) = TextView(this).apply { this.text = text; textSize = 15f; setPadding(0, dp(8), 0, dp(4)) }
        fun editor(lines: Int = 1) = EditText(this).apply {
            minLines = lines; gravity = if (lines > 1) Gravity.TOP else Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(Color.rgb(240, 247, 253))
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() } }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(22), dp(30))
            addView(TextView(context).apply { text = "Anime Maid Widget v1.1"; textSize = 26f; gravity = Gravity.CENTER; setTextColor(Color.rgb(24, 86, 150)) })
            addView(TextView(context).apply { text = "对话内容与天气 API 设置"; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(10)) })
            addView(title("女仆对话"))
            addView(label("调用模式"))
            modeSpinner = Spinner(context).also { addView(it) }
            modeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("内置", "仅自定义", "混合"))
            addView(label("对话分类"))
            categorySpinner = Spinner(context).also { addView(it) }
            categorySpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categoryLabels)
            addView(label("每行一条对话；需要气泡内换行时写 \\n"))
            dialogueEditor = editor(7).also { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190))) }
            addView(button("保存对话并更新小组件") { saveDialogue() })

            addView(title("天气 API"))
            addView(label("天气提供商"))
            providerSpinner = Spinner(context).also { addView(it) }
            providerSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, providerLabels)
            endpointLabel = label("API 地址").also { addView(it) }
            endpointEditor = editor().also { addView(it) }
            addView(label("API Key（仅 QWeather / 高德）"))
            apiKeyEditor = editor().also { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; addView(it) }
            addView(label("高德 adcode（可留空，由经纬度查询）"))
            adcodeEditor = editor().also { addView(it) }
            addView(label("自定义 API 配置 JSON（仅自定义源）"))
            customEditor = editor(9).also { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250))) }
            addView(button("测试天气连接") { testWeather() })
            addView(button("保存天气配置") { saveWeather(refresh = false) })
            addView(button("保存并刷新所有小组件") { saveWeather(refresh = true) })
            addView(TextView(context).apply { text = "提示：API Key 保存在应用私有 JSON 中；卸载应用会清除。"; textSize = 12f; setPadding(0, dp(10), 0, 0) })
        }
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!loaded) return
                captureDialogue(currentCategory); currentCategory = categoryKeys[position]; showDialogueCategory()
            }
        }
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!loaded || programmaticSelection) return
                captureProvider(currentProvider, strict = false); currentProvider = providerKeys[position]; showProvider()
            }
        }
        return ScrollView(this).apply { addView(body) }
    }

    private fun showDialogueConfig() {
        modeSpinner.setSelection(when (dialogueConfig.optString("mode", "mixed")) { "builtin" -> 0; "custom" -> 1; else -> 2 })
        currentCategory = categoryKeys[categorySpinner.selectedItemPosition.coerceAtLeast(0)]
        showDialogueCategory()
    }

    private fun showDialogueCategory() {
        val entries = dialogueConfig.optJSONObject("categories")?.optJSONArray(currentCategory) ?: JSONArray()
        val lines = (0 until entries.length()).mapNotNull { index ->
            val item = entries.opt(index)
            when (item) { is JSONObject -> if (item.optBoolean("enabled", true)) item.optString("text") else null; is String -> item; else -> null }
        }
        dialogueEditor.setText(lines.joinToString("\n"))
    }

    private fun captureDialogue(category: String) {
        if (!::dialogueConfig.isInitialized) return
        val categories = dialogueConfig.optJSONObject("categories") ?: JSONObject().also { dialogueConfig.put("categories", it) }
        val array = JSONArray()
        dialogueEditor.text.toString().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(100).forEachIndexed { index, text ->
            array.put(JSONObject().put("id", "custom_${category}_${index + 1}").put("text", text.take(120)).put("enabled", true))
        }
        categories.put(category, array)
    }

    private fun saveDialogue() {
        if (!loaded) return
        captureDialogue(currentCategory)
        dialogueConfig.put("mode", arrayOf("builtin", "custom", "mixed")[modeSpinner.selectedItemPosition])
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { PythonBridge.saveDialogueConfigJson(this@LauncherActivity, dialogueConfig.toString()) }
                dialogueConfig = JSONObject(saved); sendBroadcast(Intent(this@LauncherActivity, AnimeMaidWidget::class.java).apply { action = AnimeMaidWidget.ACTION_RENDER })
                toast("对话设置已保存")
            } catch (e: Exception) { toast("保存对话失败：${e.message}") }
        }
    }

    private fun showWeatherConfig() {
        currentProvider = weatherConfig.optString("provider", "open_meteo")
        programmaticSelection = true
        providerSpinner.setSelection(providerKeys.indexOf(currentProvider).coerceAtLeast(0))
        programmaticSelection = false
        showProvider()
    }

    private fun showProvider() {
        endpointEditor.visibility = View.VISIBLE; apiKeyEditor.visibility = View.VISIBLE; adcodeEditor.visibility = View.VISIBLE; customEditor.visibility = View.GONE
        when (currentProvider) {
            "open_meteo" -> { endpointLabel.text = "Open-Meteo API 地址"; endpointEditor.setText(weatherConfig.optJSONObject("open_meteo")?.optString("base_url")); apiKeyEditor.setText(""); adcodeEditor.setText("") }
            "qweather" -> { endpointLabel.text = "QWeather API Host"; val c=weatherConfig.optJSONObject("qweather"); endpointEditor.setText(c?.optString("api_host")); apiKeyEditor.setText(c?.optString("api_key")); adcodeEditor.setText("") }
            "amap" -> { endpointLabel.text = "高德固定使用官方 HTTPS 地址"; endpointEditor.setText("https://restapi.amap.com"); val c=weatherConfig.optJSONObject("amap"); apiKeyEditor.setText(c?.optString("api_key")); adcodeEditor.setText(c?.optString("adcode")) }
            else -> { endpointEditor.visibility=View.GONE; apiKeyEditor.visibility=View.GONE; adcodeEditor.visibility=View.GONE; customEditor.visibility=View.VISIBLE; customEditor.setText((weatherConfig.optJSONObject("custom") ?: JSONObject()).toString(2)) }
        }
    }

    private fun captureProvider(provider: String, strict: Boolean) {
        if (!::weatherConfig.isInitialized) return
        when (provider) {
            "open_meteo" -> weatherConfig.put("open_meteo", JSONObject().put("base_url", endpointEditor.text.toString().trim()))
            "qweather" -> weatherConfig.put("qweather", JSONObject().put("api_host", endpointEditor.text.toString().trim()).put("api_key", apiKeyEditor.text.toString().trim()))
            "amap" -> weatherConfig.put("amap", JSONObject().put("api_key", apiKeyEditor.text.toString().trim()).put("adcode", adcodeEditor.text.toString().trim()))
            "custom" -> if (customEditor.text.isNotBlank()) {
                try { weatherConfig.put("custom", JSONObject(customEditor.text.toString())) }
                catch (e: Exception) { if (strict) throw IllegalArgumentException("自定义 API JSON 格式错误", e) }
            }
        }
    }

    private fun weatherJson(): String {
        captureProvider(currentProvider, strict = true); weatherConfig.put("provider", providerKeys[providerSpinner.selectedItemPosition]); return weatherConfig.toString()
    }

    private fun testWeather() {
        if (!loaded) return
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { PythonBridge.testWeatherConfigJson(this@LauncherActivity, weatherJson()) }
                val json=JSONObject(result)
                toast(if (json.optBoolean("success")) "连接成功：${json.optString("temperature")}°，逐小时 ${json.optInt("hourly_count")} 条" else "连接失败：${json.optString("error")}")
            } catch (e: Exception) { toast("测试失败：${e.message}") }
        }
    }

    private fun saveWeather(refresh: Boolean) {
        if (!loaded) return
        scope.launch {
            try {
                val saved=withContext(Dispatchers.IO) { PythonBridge.saveWeatherConfigJson(this@LauncherActivity, weatherJson()) }
                weatherConfig=JSONObject(saved); toast("天气设置已保存")
                if (refresh) refreshAllWidgets()
            } catch (e: Exception) { toast("保存天气失败：${e.message}") }
        }
    }

    private fun refreshAllWidgets() {
        val manager=AppWidgetManager.getInstance(this); val ids=manager.getAppWidgetIds(ComponentName(this,AnimeMaidWidget::class.java))
        if (ids.isEmpty()) { toast("尚未添加桌面小组件"); return }
        sendBroadcast(Intent(this,AnimeMaidWidget::class.java).apply { action=AppWidgetManager.ACTION_APPWIDGET_UPDATE; putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,ids) })
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
