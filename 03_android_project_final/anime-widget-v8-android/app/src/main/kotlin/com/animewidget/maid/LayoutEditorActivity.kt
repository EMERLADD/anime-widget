package com.animewidget.maid

import android.app.AlertDialog
import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LayoutEditorActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var preview: FrameLayout
    private lateinit var image: ImageView
    private lateinit var clock: TextClock
    private lateinit var refresh: ImageView
    private lateinit var overlay: DraggableModuleOverlay
    private lateinit var status: TextView
    private lateinit var resetButton: Button
    private lateinit var moduleRow: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var visibleSwitch: Switch
    private lateinit var scaleSeek: SeekBar
    private lateinit var scaleLabel: TextView
    private lateinit var restoreScaleButton: Button
    private val moduleButtons = mutableMapOf<String, Button>()
    private val scalableIds = setOf("clock", "character", "refresh")
    private var updatingControls = false

    private var layout = WidgetLayoutCodec.parse("{}")

    private val modules = listOf(
        EditorModule("date", "日期", 0, 0, 452, 70),
        EditorModule("clock", "时间", 0, 0, WidgetModuleSpecs.clock.width, WidgetModuleSpecs.clock.height),
        EditorModule("location", "地点", 0, 0, 340, 95),
        EditorModule("weather", "天气", 0, 0, 562, 225),
        EditorModule("forecast", "预报", 0, 0, 662, 118),
        EditorModule("bubble", "角色对话框", 0, 0, 450, 314),
        EditorModule("character", "角色", 0, 0, 800, 600),
        EditorModule("refresh", "刷新", 0, 0, WidgetModuleSpecs.refresh.width, WidgetModuleSpecs.refresh.height),
        EditorModule("ai_input", "用户输入框", 0, 0, WidgetModuleSpecs.aiInput.width, WidgetModuleSpecs.aiInput.height)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "小组件布局编辑"
        setContentView(buildUi())
        AppThemeManager.apply(this)
        overlay.setScalableIds(scalableIds)
        overlay.onModuleSelected = { m -> selectModule(m.id) }
        overlay.onModuleMoved = { m -> commitModule(m) }
        overlay.onModuleScaled = { m ->
            showModuleControls(m)
            commitModule(m)
        }
        loadAndRender(null)
    }

    override fun onResume() { super.onResume(); AppThemeManager.apply(this) }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() = ScrollView(this).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            addView(TextView(context).apply {
                text = "小组件布局编辑"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(24, 86, 150))
            })
            addView(TextView(context).apply {
                text = "长按模块约350毫秒后拖动，松手应用"
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(12))
            })

            preview = FrameLayout(context).apply {
                tag = AppThemeManager.SKIP_TAG
                setBackgroundColor(Color.rgb(35, 55, 80))
            }
            addView(preview, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)))

            image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "小组件 Pillow 预览"
            }
            preview.addView(image, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            clock = TextClock(context).apply {
                format12Hour = "HH:mm"
                format24Hour = "HH:mm"
                textSize = 18f
                setTextColor(Color.rgb(250, 252, 255))
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                setShadowLayer(dp(3).toFloat(), 0f, 0f, Color.rgb(25, 60, 135))
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            preview.addView(clock)

            refresh = ImageView(context).apply {
                setImageResource(R.drawable.refresh_icon)
                setBackgroundResource(R.drawable.refresh_button_background)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "刷新按钮预览"
            }
            preview.addView(refresh)

            this@LayoutEditorActivity.overlay = DraggableModuleOverlay(context)
            preview.addView(this@LayoutEditorActivity.overlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            addView(TextView(context).apply {
                text = "选择模块（单选）"
                textSize = 14f
                setPadding(0, dp(10), 0, dp(4))
            })
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                moduleRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                addView(moduleRow)
                for (module in modules) {
                    val button = Button(context).apply {
                        text = module.label
                        isAllCaps = false
                        minHeight = dp(50)
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        setOnClickListener { selectModule(module.id) }
                    }
                    moduleButtons[module.id] = button
                    moduleRow.addView(button, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ))
                }
            })

            controls = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            visibleSwitch = Switch(context).apply {
                text = "显示模块"
                setOnCheckedChangeListener { _, checked ->
                    if (!updatingControls) selectedModule()?.let { module ->
                        module.visible = checked
                        this@LayoutEditorActivity.overlay.setModules(modules)
                        layoutPreview()
                        commitModule(module)
                    }
                }
            }
            controls.addView(visibleSwitch)
            scaleLabel = TextView(context).apply { textSize = 14f }
            controls.addView(scaleLabel)
            scaleSeek = SeekBar(context).apply {
                max = 100
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser || updatingControls) return
                        selectedModule()?.takeIf { it.id in scalableIds }?.let { module ->
                            this@LayoutEditorActivity.overlay.scaleSelectedKeepingVisualCenter(progress + 50)
                            scaleLabel.text = "大小：" + module.scale + "%"
                            this@LayoutEditorActivity.overlay.setModules(modules)
                            layoutPreview()
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        selectedModule()?.takeIf { it.id in scalableIds }?.let(::commitModule)
                    }
                })
            }
            controls.addView(scaleSeek)
            restoreScaleButton = Button(context).apply {
                text = "还原为 100%"
                isAllCaps = false
                setOnClickListener {
                    selectedModule()?.takeIf { it.id in scalableIds }?.let { module ->
                        this@LayoutEditorActivity.overlay.scaleSelectedKeepingVisualCenter(100)
                        showModuleControls(module)
                        this@LayoutEditorActivity.overlay.setModules(modules)
                        layoutPreview()
                        commitModule(module)
                    }
                }
            }
            controls.addView(restoreScaleButton)
            addView(controls)

            status = TextView(context).apply {
                text = "正在读取布局…"
                textSize = 14f
                setPadding(0, dp(12), 0, dp(8))
            }
            addView(status)

            resetButton = Button(context).apply {
                text = "恢复默认布局"
                isEnabled = false
                setOnClickListener { confirmReset() }
            }
            addView(resetButton)
        })
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("恢复默认布局")
            .setMessage("将把所有模块位置恢复到默认，确定继续？")
            .setPositiveButton("确定") { _, _ -> resetDefault() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadAndRender(updateJson: String?) {
        setBusy(true)
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val normalized = if (updateJson == null) {
                        PythonBridge.getLayoutConfigJson(this@LayoutEditorActivity)
                    } else {
                        PythonBridge.updateLayoutPositionsJson(this@LayoutEditorActivity, updateJson)
                    }
                    coroutineScope {
                        val geometry = async { runCatching {
                            PythonBridge.getLayoutGeometryJson(this@LayoutEditorActivity)
                        }.getOrNull() }
                        val png = async { PythonBridge.renderWidgetPng(this@LayoutEditorActivity) }
                        Triple(normalized, geometry.await(), png.await())
                    }
                }
                layout = WidgetLayoutCache.write(this@LayoutEditorActivity, result.first)
                applyGeometry(result.second)
                syncModulesFromLayout()
                result.third?.let { image.setImageBitmap(BitmapFactory.decodeFile(it)) }
                preview.post { layoutPreview() }
                result.third?.let {
                    WidgetRefreshController.publishRenderedImageToAll(this@LayoutEditorActivity, it, false)
                }
                updateStatus()
            } catch (error: Exception) {
                Toast.makeText(this@LayoutEditorActivity,
                    "布局操作失败：${error.message}", Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun commitModule(m: EditorModule) {
        loadAndRender(modulePatch(m).toString())
    }

    private fun resetDefault() {
        val patch = JSONObject()
        for (m in modules) {
            val d = WidgetLayoutCodec.defaultPoint(m.id)
            when (m.id) {
                "character" -> patch.put("maid", JSONObject().put("x", d.x).put("y", d.y).put("scale", 100).put("visible", 1))
                else -> patch.put(m.id, JSONObject().put("x", d.x).put("y", d.y).put("scale", 100).put("visible", 1))
            }
        }
        loadAndRender(patch.toString())
    }

    private fun modulePatch(m: EditorModule): JSONObject {
        val fields = JSONObject()
            .put("x", m.x)
            .put("y", m.y)
            .put("scale", m.scale)
            .put("visible", if (m.visible) 1 else 0)
        return JSONObject().put(if (m.id == "character") "maid" else m.id, fields)
    }

    private fun applyGeometry(json: String?) {
        val geometry = json?.let(WidgetGeometryCodec::parse).orEmpty()
        for (module in modules) {
            val item = geometry[module.id]
            if (item == null) {
                module.visualLeft = 0
                module.visualTop = 0
                module.visualWidth = module.frameWidth
                module.visualHeight = module.frameHeight
            } else {
                module.frameWidth = item.frameWidth
                module.frameHeight = item.frameHeight
                module.visualLeft = item.visualLeft
                module.visualTop = item.visualTop
                module.visualWidth = item.visualWidth
                module.visualHeight = item.visualHeight
            }
        }
    }

    private fun syncModulesFromLayout() {
        for (m in modules) {
            val p = layout.point(m.id)
            m.x = p.x
            m.y = p.y
            m.scale = layout.scale(m.id)
            m.visible = layout.visible(m.id)
        }
        this@LayoutEditorActivity.overlay.setModules(modules)
        overlay.selectedId()?.let { id -> modules.firstOrNull { it.id == id }?.let(::showModuleControls) }
    }

    private fun layoutPreview() {
        if (preview.width <= 0 || preview.height <= 0) return
        val rect = WidgetCoordinateMapper.fitCenter(preview.width.toFloat(), preview.height.toFloat())
        overlay.setContentRect(rect)
        val clockModule = modules.first { it.id == "clock" }
        val refreshModule = modules.first { it.id == "refresh" }
        clock.visibility = if (clockModule.visible) View.VISIBLE else View.INVISIBLE
        refresh.visibility = if (refreshModule.visible) View.VISIBLE else View.INVISIBLE
        place(clock, DesignPoint(clockModule.x, clockModule.y), rect, WidgetModuleSpec(clockModule.frameWidth, clockModule.frameHeight), clockModule.scale)
        place(refresh, DesignPoint(refreshModule.x, refreshModule.y), rect, WidgetModuleSpec(refreshModule.frameWidth, refreshModule.frameHeight), refreshModule.scale)
        val designScale = WidgetModuleSpecs.designScale(rect)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, WidgetModuleSpecs.clock.textSize * designScale * clockModule.scale / 100f)
        val padding = (WidgetModuleSpecs.refresh.padding * designScale * refreshModule.scale / 100f).toInt().coerceAtLeast(0)
        refresh.setPadding(padding, padding, padding, padding)
    }

    private fun place(view: View, point: DesignPoint, rect: ContentRect, spec: WidgetModuleSpec, scalePercent: Int) {
        val pixel = WidgetCoordinateMapper.toPixel(point, rect)
        val size = WidgetCoordinateMapper.sizeToPixel(spec, rect, scalePercent)
        view.layoutParams = FrameLayout.LayoutParams(
            size.x.toInt().coerceAtLeast(1),
            size.y.toInt().coerceAtLeast(1)
        ).apply {
            leftMargin = pixel.x.toInt()
            topMargin = pixel.y.toInt()
        }
    }

    private fun selectedModule(): EditorModule? =
        overlay.selectedId()?.let { id -> modules.firstOrNull { it.id == id } }

    private fun selectModule(id: String) {
        overlay.select(id)
        val selected = modules.firstOrNull { it.id == id } ?: return
        showModuleControls(selected)
        moduleButtons.forEach { (moduleId, button) ->
            button.alpha = if (moduleId == id) 1f else 0.58f
        }
        updateStatus()
    }

    private fun showModuleControls(module: EditorModule) {
        updatingControls = true
        controls.visibility = View.VISIBLE
        visibleSwitch.text = "显示" + module.label + "模块"
        visibleSwitch.isChecked = module.visible
        val scalable = module.id in scalableIds
        scaleSeek.visibility = if (scalable) View.VISIBLE else View.GONE
        restoreScaleButton.visibility = if (scalable) View.VISIBLE else View.GONE
        scaleLabel.text = if (scalable) "大小：" + module.scale + "%" else "该模块使用固定尺寸"
        if (scalable) scaleSeek.progress = module.scale.coerceIn(50, 150) - 50
        updatingControls = false
    }

    private fun updateStatus() {
        val id = overlay.selectedId()
        val m = modules.firstOrNull { it.id == id }
        status.text = if (m != null) {
            "选中：${m.label}（${m.id}）  坐标 (${m.x}, ${m.y})"
        } else {
            "未选中模块。长按模块后拖动可调整位置。"
        }
    }

    private fun setBusy(busy: Boolean) {
        overlay.isEnabled = !busy
        resetButton.isEnabled = !busy
        visibleSwitch.isEnabled = !busy
        scaleSeek.isEnabled = !busy
        restoreScaleButton.isEnabled = !busy
        moduleButtons.values.forEach { it.isEnabled = !busy }
    }
}
