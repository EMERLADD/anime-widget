package com.animewidget.maid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
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
import java.util.UUID

/** Itemized built-in and custom dialogue management. */
class DialogueSettingsActivity : Activity() {
    private data class DialogueItem(
        val id: String,
        val text: String,
        val builtin: Boolean,
        val enabled: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var dialogueConfig: JSONObject
    private lateinit var builtinDialogue: JSONObject
    private lateinit var modeSpinner: Spinner
    private lateinit var categorySpinner: Spinner
    private lateinit var filterSpinner: Spinner
    private lateinit var dialogueList: LinearLayout
    private lateinit var selectionStatus: TextView
    private lateinit var loadingStatus: TextView
    private lateinit var retryButton: Button
    private val actionButtons = mutableListOf<Button>()
    private var loaded = false
    private var currentCategory = "global"
    private val selectedDialogueIds = linkedSetOf<String>()

    private val categoryKeys = arrayOf("global", "morning", "day", "evening", "night", "late_night", "sunny", "cloudy", "overcast", "fog", "drizzle", "rain", "heavy_rain", "snow", "thunder", "hot", "cold")
    private val categoryLabels = arrayOf("通用", "早晨", "白天", "傍晚", "夜晚", "深夜", "晴天", "多云", "阴天", "雾", "毛毛雨", "雨", "大雨", "雪", "雷雨", "高温", "寒冷")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        AppThemeManager.apply(this)
        loadDialogueConfig()
    }

    private fun loadDialogueConfig() {
        loaded = false
        setControlsEnabled(false)
        loadingStatus.text = "正在读取对话设置…"
        retryButton.visibility = View.GONE
        scope.launch {
            try {
                val configs = withContext(Dispatchers.IO) {
                    Pair(
                        PythonBridge.getDialogueConfigJson(this@DialogueSettingsActivity),
                        PythonBridge.getBuiltinDialogueJson(this@DialogueSettingsActivity)
                    )
                }
                dialogueConfig = JSONObject(configs.first)
                builtinDialogue = JSONObject(configs.second)
                loaded = true
                setControlsEnabled(true)
                showDialogueConfig()
            } catch (e: Exception) {
                loadingStatus.text = "读取失败：${e.message ?: "未知错误"}"
                retryButton.visibility = View.VISIBLE
                toast("读取设置失败")
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        if (::modeSpinner.isInitialized) modeSpinner.isEnabled = enabled
        if (::categorySpinner.isInitialized) categorySpinner.isEnabled = enabled
        if (::filterSpinner.isInitialized) filterSpinner.isEnabled = enabled
        actionButtons.forEach { it.isEnabled = enabled }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 21f
        setTextColor(Color.rgb(25, 70, 130))
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun editor(lines: Int = 1) = EditText(this).apply {
        minLines = lines
        gravity = if (lines > 1) Gravity.TOP else Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(Color.rgb(240, 247, 253))
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun actionButton(text: String, action: () -> Unit) = button(text, action).also {
        it.isEnabled = false
        actionButtons += it
    }

    private fun buttonRow(vararg buttons: Button) = LinearLayout(this).apply {
        val stacked = resources.configuration.screenWidthDp < 420 ||
            resources.configuration.fontScale >= 1.25f
        orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        buttons.forEach {
            it.minHeight = dp(50)
            addView(it, if (stacked) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            } else {
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun buildUi(): View {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(30))
            addView(TextView(context).apply {
                text = "对话管理"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(24, 86, 150))
            })
            addView(TextView(context).apply {
                text = "逐条查看、添加和批量整理内置/自定义对话"
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(10))
            })

            addView(label("调用模式"))
            modeSpinner = Spinner(context).also { it.isEnabled = false; addView(it) }
            modeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("内置", "仅自定义", "混合"))

            addView(label("对话分类"))
            categorySpinner = Spinner(context).also { it.isEnabled = false; addView(it) }
            categorySpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categoryLabels)

            addView(label("显示来源"))
            filterSpinner = Spinner(context).also { it.isEnabled = false; addView(it) }
            filterSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("全部", "仅内置", "仅自定义"))

            loadingStatus = TextView(context).also {
                it.text = "正在读取对话设置…"
                it.textSize = 14f
                it.setTextColor(Color.rgb(25, 70, 130))
                it.setPadding(dp(10), dp(10), dp(10), dp(5))
                addView(it)
            }
            retryButton = button("重新加载") { loadDialogueConfig() }.also {
                it.visibility = View.GONE
                addView(it)
            }
            selectionStatus = TextView(context).also {
                it.textSize = 13f
                it.setTextColor(Color.DKGRAY)
                it.setPadding(0, dp(6), 0, dp(4))
                addView(it)
            }

            addView(buttonRow(
                actionButton("＋ 添加一条") { showSingleAddDialog() },
                actionButton("批量添加") { showBatchAddDialog() }
            ))
            addView(buttonRow(
                actionButton("全选当前") { selectAllVisible() },
                actionButton("取消选择") { selectedDialogueIds.clear(); renderDialogueList() }
            ))
            addView(buttonRow(
                actionButton("启用") { setSelectedEnabled(true) },
                actionButton("停用") { setSelectedEnabled(false) },
                actionButton("删除") { deleteSelected() }
            ))
            addView(buttonRow(
                actionButton("编辑一条") { showEditDialog() },
                actionButton("移动到…") { chooseDestination(copy = false) },
                actionButton("复制到…") { chooseDestination(copy = true) }
            ))

            dialogueList = LinearLayout(context).also {
                it.orientation = LinearLayout.VERTICAL
                addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            addView(TextView(context).apply {
                text = "内置内容不可物理删除：停用会隐藏；移动会在目标分类生成自定义副本。"
                textSize = 12f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(7), 0, dp(6))
            })
            addView(actionButton("保存对话并更新小组件") { saveDialogue() })

        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!loaded) return
                currentCategory = categoryKeys[position]
                selectedDialogueIds.clear()
                renderDialogueList()
            }
        }
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (loaded) {
                    selectedDialogueIds.clear()
                    renderDialogueList()
                }
            }
        }
        return ScrollView(this).apply { addView(body) }
    }

    private fun showDialogueConfig() {
        modeSpinner.setSelection(
            when (dialogueConfig.optString("mode", "mixed")) {
                "builtin" -> 0
                "custom" -> 1
                else -> 2
            }
        )
        currentCategory = categoryKeys[categorySpinner.selectedItemPosition.coerceAtLeast(0)]
        renderDialogueList()
    }

    private fun categoriesObject(): JSONObject =
        dialogueConfig.optJSONObject("categories") ?: JSONObject().also { dialogueConfig.put("categories", it) }

    private fun customArray(category: String): JSONArray =
        categoriesObject().optJSONArray(category) ?: JSONArray().also { categoriesObject().put(category, it) }

    private fun overridesObject(): JSONObject =
        dialogueConfig.optJSONObject("builtin_overrides") ?: JSONObject().also { dialogueConfig.put("builtin_overrides", it) }

    private fun isBuiltinEnabled(id: String): Boolean =
        overridesObject().optJSONObject(id)?.optBoolean("enabled", true) != false

    private fun dialogueItems(category: String): List<DialogueItem> {
        val result = mutableListOf<DialogueItem>()
        val builtins = builtinDialogue.optJSONArray(category) ?: JSONArray()
        for (index in 0 until builtins.length()) {
            when (val raw = builtins.opt(index)) {
                is JSONObject -> {
                    val id = raw.optString("id", "builtin_${category}_${index + 1}")
                    val text = raw.optString("text").trim()
                    if (text.isNotEmpty()) result += DialogueItem(id, text, builtin = true, enabled = isBuiltinEnabled(id))
                }
                is String -> if (raw.isNotBlank()) {
                    val id = "builtin_${category}_${index + 1}"
                    result += DialogueItem(id, raw.trim(), builtin = true, enabled = isBuiltinEnabled(id))
                }
            }
        }
        val custom = customArray(category)
        for (index in 0 until custom.length()) {
            val raw = custom.optJSONObject(index) ?: continue
            val text = raw.optString("text").trim()
            if (text.isNotEmpty()) {
                result += DialogueItem(
                    raw.optString("id", "custom_${UUID.randomUUID()}"),
                    text,
                    builtin = false,
                    enabled = raw.optBoolean("enabled", true)
                )
            }
        }
        return result
    }

    private fun visibleItems(): List<DialogueItem> {
        val filter = filterSpinner.selectedItemPosition
        return dialogueItems(currentCategory).filter {
            filter == 0 || (filter == 1 && it.builtin) || (filter == 2 && !it.builtin)
        }
    }

    private fun renderDialogueList() {
        if (!loaded) return
        dialogueList.removeAllViews()
        val visible = visibleItems()
        selectedDialogueIds.retainAll(dialogueItems(currentCategory).map { it.id }.toSet())
        if (visible.isEmpty()) {
            dialogueList.addView(TextView(this).apply {
                text = "（当前筛选下没有对话）"
                setPadding(dp(12), dp(14), dp(12), dp(14))
                setTextColor(Color.GRAY)
            })
        }
        visible.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(7), dp(6), dp(7))
                setBackgroundColor(if (item.enabled) Color.rgb(245, 249, 253) else Color.rgb(235, 235, 235))
            }
            row.addView(CheckBox(this).apply {
                isChecked = item.id in selectedDialogueIds
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedDialogueIds.add(item.id) else selectedDialogueIds.remove(item.id)
                    updateSelectionStatus()
                }
            })
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@DialogueSettingsActivity).apply {
                    text = item.text.replace("\\n", "\n")
                    textSize = 15f
                    setTextColor(if (item.enabled) Color.rgb(30, 45, 65) else Color.GRAY)
                })
                addView(TextView(this@DialogueSettingsActivity).apply {
                    text = (if (item.builtin) "内置" else "自定义") + if (item.enabled) " · 已启用" else " · 已停用"
                    textSize = 11f
                    setTextColor(if (item.builtin) Color.rgb(38, 105, 170) else Color.rgb(40, 130, 85))
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            dialogueList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            })
        }
        updateSelectionStatus()
    }

    private fun updateSelectionStatus() {
        val items = dialogueItems(currentCategory)
        val customCount = items.count { !it.builtin }
        val categoryLabel = categoryLabels[categoryKeys.indexOf(currentCategory)]
        loadingStatus.text = "新增内容将保存到当前分类：$categoryLabel"
        selectionStatus.text = "共 ${items.size} 条｜内置 ${items.size - customCount}｜自定义 $customCount｜已选择 ${selectedDialogueIds.size}"
    }

    private fun selectAllVisible() {
        selectedDialogueIds.addAll(visibleItems().map { it.id })
        renderDialogueList()
    }

    private fun selectedItems(): List<DialogueItem> =
        dialogueItems(currentCategory).filter { it.id in selectedDialogueIds }

    private fun setSelectedEnabled(enabled: Boolean) {
        val selected = selectedItems()
        if (selected.isEmpty()) return toast("请先选择对话")
        val custom = customArray(currentCategory)
        selected.forEach { item ->
            if (item.builtin) {
                if (enabled) overridesObject().remove(item.id)
                else overridesObject().put(item.id, JSONObject().put("enabled", false))
            } else {
                for (index in 0 until custom.length()) {
                    val raw = custom.optJSONObject(index)
                    if (raw?.optString("id") == item.id) raw.put("enabled", enabled)
                }
            }
        }
        renderDialogueList()
    }

    private fun deleteSelected() {
        val selected = selectedItems()
        if (selected.isEmpty()) return toast("请先选择对话")
        AlertDialog.Builder(this)
            .setTitle("删除或停用")
            .setMessage("自定义对话将被删除；内置对话将被停用。继续吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("继续") { _, _ ->
                selected.filter { it.builtin }.forEach {
                    overridesObject().put(it.id, JSONObject().put("enabled", false))
                }
                val removedIds = selected.filterNot { it.builtin }.map { it.id }.toSet()
                replaceCustomArray(currentCategory, customObjects(currentCategory).filterNot { it.optString("id") in removedIds })
                selectedDialogueIds.clear()
                renderDialogueList()
            }.show()
    }

    private fun showSingleAddDialog() {
        if (!loaded) return toast("对话设置尚未读取完成")
        val input = editor(3).apply { hint = "输入一条对话；气泡内换行写 \\n" }
        val categoryLabel = categoryLabels[categoryKeys.indexOf(currentCategory)]
        AlertDialog.Builder(this)
            .setTitle("添加到：$categoryLabel")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加") { _, _ ->
                val text = input.text.toString().trim().take(120)
                if (text.isBlank()) return@setPositiveButton
                val entries = customObjects(currentCategory)
                if (entries.size >= 100) return@setPositiveButton toast("当前分类已达到 100 条上限")
                if (entries.any { it.optString("text") == text }) return@setPositiveButton toast("这条自定义对话已经存在")
                entries += JSONObject()
                    .put("id", "custom_${UUID.randomUUID().toString().replace("-", "")}")
                    .put("text", text)
                    .put("enabled", true)
                replaceCustomArray(currentCategory, entries)
                renderDialogueList()
                toast("已添加 1 条，保存后生效")
            }.show()
    }

    private fun showBatchAddDialog() {
        if (!loaded) return toast("对话设置尚未读取完成")
        val input = editor(8).apply { hint = "每行一条；气泡内换行写 \\n" }
        val categoryLabel = categoryLabels[categoryKeys.indexOf(currentCategory)]
        AlertDialog.Builder(this)
            .setTitle("批量添加到：$categoryLabel")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加") { _, _ ->
                val entries = customObjects(currentCategory)
                val existing = entries.map { it.optString("text") }.toMutableSet()
                var added = 0
                var duplicate = 0
                var overflow = 0
                input.text.toString().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { raw ->
                    val text = raw.take(120)
                    when {
                        text in existing -> duplicate++
                        entries.size >= 100 -> overflow++
                        else -> {
                            existing += text
                            entries += JSONObject()
                                .put("id", "custom_${UUID.randomUUID().toString().replace("-", "")}")
                                .put("text", text)
                                .put("enabled", true)
                            added++
                        }
                    }
                }
                replaceCustomArray(currentCategory, entries)
                renderDialogueList()
                toast("新增 $added 条｜重复 $duplicate 条｜超出容量 $overflow 条；保存后生效")
            }.show()
    }

    private fun showEditDialog() {
        val selected = selectedItems()
        if (selected.size != 1 || selected.first().builtin) return toast("请选择恰好一条自定义对话")
        val item = selected.first()
        val input = editor(3).apply { setText(item.text) }
        AlertDialog.Builder(this)
            .setTitle("编辑自定义对话")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val text = input.text.toString().trim().take(120)
                if (text.isBlank()) return@setPositiveButton
                customObjects(currentCategory).firstOrNull { it.optString("id") == item.id }?.put("text", text)
                replaceCustomArray(currentCategory, customObjects(currentCategory))
                renderDialogueList()
            }.show()
    }

    private fun chooseDestination(copy: Boolean) {
        val selected = selectedItems()
        if (selected.isEmpty()) return toast("请先选择对话")
        AlertDialog.Builder(this)
            .setTitle(if (copy) "复制到分类" else "移动到分类")
            .setItems(categoryLabels) { _, which -> moveOrCopySelected(categoryKeys[which], copy) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun customObjects(category: String): MutableList<JSONObject> {
        val array = customArray(category)
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.toMutableList()
    }

    private fun replaceCustomArray(category: String, entries: List<JSONObject>) {
        val array = JSONArray()
        entries.take(100).forEach { array.put(it) }
        categoriesObject().put(category, array)
    }

    private fun moveOrCopySelected(destination: String, copy: Boolean) {
        if (!copy && destination == currentCategory) return toast("目标分类与当前分类相同")
        val selected = selectedItems()
        val destinationEntries = customObjects(destination)
        val destinationTexts = destinationEntries.map { it.optString("text") }.toMutableSet()
        val additionsNeeded = selected.count { it.text !in destinationTexts }
        if (destinationEntries.size + additionsNeeded > 100) {
            return toast("目标分类容量不足（每类最多 100 条自定义对话）")
        }
        val movedCustomIds = mutableSetOf<String>()

        selected.forEach { item ->
            if (destinationTexts.add(item.text)) {
                if (!copy && !item.builtin) {
                    val original = customObjects(currentCategory).firstOrNull { it.optString("id") == item.id }
                    if (original != null) destinationEntries += original
                } else {
                    destinationEntries += JSONObject()
                        .put("id", "custom_${UUID.randomUUID().toString().replace("-", "")}")
                        .put("text", item.text)
                        .put("enabled", true)
                }
            }
            if (!copy) {
                if (item.builtin) overridesObject().put(item.id, JSONObject().put("enabled", false))
                else movedCustomIds += item.id
            }
        }
        replaceCustomArray(destination, destinationEntries)
        if (!copy && movedCustomIds.isNotEmpty()) {
            replaceCustomArray(currentCategory, customObjects(currentCategory).filterNot { it.optString("id") in movedCustomIds })
        }
        selectedDialogueIds.clear()
        renderDialogueList()
        toast(if (copy) "已复制到${categoryLabels[categoryKeys.indexOf(destination)]}" else "已移动到${categoryLabels[categoryKeys.indexOf(destination)]}")
    }

    private fun saveDialogue() {
        if (!loaded) return
        dialogueConfig.put("schema_version", 2)
        dialogueConfig.put("mode", arrayOf("builtin", "custom", "mixed")[modeSpinner.selectedItemPosition])
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    PythonBridge.saveDialogueConfigJson(this@DialogueSettingsActivity, dialogueConfig.toString())
                }
                dialogueConfig = JSONObject(saved)
                sendBroadcast(Intent(this@DialogueSettingsActivity, AnimeMaidWidget::class.java).apply {
                    action = AnimeMaidWidget.ACTION_RENDER
                })
                renderDialogueList()
                toast("对话设置已保存")
            } catch (e: Exception) {
                toast("保存对话失败：${e.message}")
            }
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    override fun onResume() { super.onResume(); AppThemeManager.apply(this) }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
