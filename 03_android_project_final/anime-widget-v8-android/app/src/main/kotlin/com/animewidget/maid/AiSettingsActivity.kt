package com.animewidget.maid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.arm.aichat.internal.InferenceEngineImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Opt-in model management and the user-editable 50-character persona prompt. */
class AiSettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var downloadJob: Job? = null
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var deleteButton: Button
    private lateinit var askButton: Button
    private lateinit var promptEditor: EditText
    private lateinit var counter: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "本地 AI 天气助手"
        setContentView(buildUi())
        AppThemeManager.apply(this)
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        AppThemeManager.apply(this)
        refreshState()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(52)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { action() }
    }

    private fun buildUi() = ScrollView(this).apply {
        isFillViewport = true
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))

            addView(TextView(context).apply {
                text = "本地 AI 天气助手"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(24, 86, 150))
            })
            addView(TextView(context).apply {
                text = "模型完全由用户选择下载，不下载也不影响普通小组件功能。"
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(18))
            })

            addView(TextView(context).apply {
                text = "模型"
                textSize = 20f
                setPadding(0, dp(8), 0, dp(6))
            })
            addView(TextView(context).apply {
                text = "Qwen3.5 0.8B · Q4_K_M · 约 508 MiB\n来源：魔搭社区 " +
                    AiModelManager.MODEL_REPOSITORY
                textSize = 14f
                setPadding(0, 0, 0, dp(8))
            })
            status = TextView(context).also {
                it.textSize = 14f
                it.setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(it)
            }
            progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).also {
                it.max = 1000
                addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)).apply {
                    bottomMargin = dp(8)
                })
            }
            downloadButton = button("下载模型") { toggleDownload() }.also { addView(it) }
            deleteButton = button("删除已下载模型") { confirmDelete() }.also { addView(it) }

            addView(TextView(context).apply {
                text = "角色提示词"
                textSize = 20f
                setPadding(0, dp(22), 0, dp(6))
            })
            addView(TextView(context).apply {
                text = "固定天气助手指令之后的补充设定，最多 50 个字符。"
                textSize = 13f
            })
            promptEditor = EditText(context).also {
                it.minLines = 3
                it.gravity = Gravity.TOP
                it.setText(AiDialogueStore.customPrompt(this@AiSettingsActivity))
                it.setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(it, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) })
            }
            counter = TextView(context).also {
                it.gravity = Gravity.END
                it.textSize = 12f
                addView(it)
            }
            promptEditor.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) savePrompt()
            }
            promptEditor.addTextChangedListener(SimpleTextWatcher {
                val limited = AiDialogueStore.limitPrompt(it)
                if (limited != it) {
                    promptEditor.setText(limited)
                    promptEditor.setSelection(limited.length)
                }
                updateCounter(limited)
            })
            updateCounter(promptEditor.text.toString())
            addView(button("保存提示词") {
                savePrompt()
                toast("提示词已保存")
            })
            askButton = button("现在询问天气意见") {
                savePrompt()
                startActivity(Intent(this@AiSettingsActivity, AiQuestionActivity::class.java))
            }.also { addView(it) }
            addView(button("恢复普通角色对话") { restoreDialogue() })

            addView(TextView(context).apply {
                text = "模型没有聊天上下文。每次只发送固定指令、自定义提示词、精简天气 JSON 和本次问题。" +
                    "模型在最后一次生成后保留 60 秒，再自动释放内存。"
                textSize = 12f
                setPadding(0, dp(12), 0, 0)
            })
        })
    }

    private fun toggleDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            toast("已暂停，下载进度会保留")
            return
        }
        downloadJob = scope.launch {
            setDownloading(true)
            try {
                AiModelManager.download(this@AiSettingsActivity) { done, total ->
                    runOnUiThread {
                        progress.progress = ((done * 1000L) / total.coerceAtLeast(1L)).toInt()
                        status.text = "正在下载：" + (done / 1024 / 1024) + " / " +
                            (total / 1024 / 1024) + " MiB"
                    }
                }
                toast("模型下载并校验完成")
            } catch (_: CancellationException) {
                // The .part file is deliberately retained for resume.
            } catch (error: Exception) {
                toast("下载未完成：" + error.message)
            } finally {
                downloadJob = null
                setDownloading(false)
                refreshState()
            }
        }
    }

    private fun setDownloading(value: Boolean) {
        downloadButton.text = if (value) "暂停下载" else "下载模型"
        deleteButton.isEnabled = !value
        askButton.isEnabled = !value && AiModelManager.isReady(this)
    }

    private fun refreshState() {
        if (downloadJob?.isActive == true) return
        val ready = AiModelManager.isReady(this)
        val bytes = AiModelManager.downloadedBytes(this)
        progress.progress = ((bytes.coerceAtMost(AiModelManager.MODEL_SIZE) * 1000L) /
            AiModelManager.MODEL_SIZE).toInt()
        status.text = when {
            ready -> "模型已下载并通过 SHA-256 校验"
            bytes > 0L -> "已有 " + (bytes / 1024 / 1024) + " MiB，可继续下载"
            else -> "未下载。只有点击下方按钮后才会联网获取模型。"
        }
        downloadButton.text = if (bytes > 0L && !ready) "继续下载模型" else "下载模型"
        downloadButton.isEnabled = !ready
        deleteButton.isEnabled = bytes > 0L
        askButton.isEnabled = ready
    }

    private fun savePrompt() {
        val saved = AiDialogueStore.saveCustomPrompt(this, promptEditor.text.toString())
        if (saved != promptEditor.text.toString()) promptEditor.setText(saved)
        updateCounter(saved)
    }

    private fun updateCounter(value: String) {
        counter.text = value.codePointCount(0, value.length).toString() + " / " +
            AiDialogueStore.MAX_CUSTOM_PROMPT_CODE_POINTS
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("删除本地模型")
            .setMessage("将删除约 508 MiB 的模型和未完成下载，之后可重新下载。")
            .setPositiveButton("删除") { _, _ ->
                scope.launch {
                    InferenceEngineImpl.unloadNow()
                    AiModelManager.delete(this@AiSettingsActivity)
                    refreshState()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun restoreDialogue() {
        scope.launch {
            try {
                AiDialogueStore.clear(this@AiSettingsActivity)
                val png = PythonBridge.renderWidgetPng(this@AiSettingsActivity)
                    ?: error("小组件渲染失败")
                WidgetRefreshController.publishRenderedImageToAll(
                    this@AiSettingsActivity, png, false
                )
                toast("已恢复普通角色对话")
            } catch (error: Exception) {
                toast("恢复失败：" + error.message)
            }
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
