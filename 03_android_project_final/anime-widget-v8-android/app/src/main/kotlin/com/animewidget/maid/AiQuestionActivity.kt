package com.animewidget.maid

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.arm.aichat.internal.InferenceEngineImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-turn question UI launched from the translucent Widget input module. */
class AiQuestionActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var question: EditText
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var progress: ProgressBar
    private lateinit var send: Button
    private var generating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "询问天气意见"
        setContentView(buildUi())
        AppThemeManager.apply(this)
        configureOverlayWindow()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (AiModelManager.isReady(this)) {
            question.requestFocus()
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            scope.launch {
                status.text = "正在预热本地模型，可直接输入问题……"
                try {
                    InferenceEngineImpl.preload(
                        this@AiQuestionActivity,
                        AiModelManager.modelFile(this@AiQuestionActivity).absolutePath
                    )
                    status.text = "模型就绪"
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    status.text = "模型预热失败，发送时将重试"
                }
            }
        } else {
            status.text = "尚未下载本地 AI 模型"
            send.text = "前往下载模型"
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(52)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        setOnClickListener { action() }
    }

    private fun buildUi() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(18))
        background = GradientDrawable().apply {
            setColor(Color.argb(246, 250, 253, 255))
            cornerRadii = floatArrayOf(dp(24f), dp(24f), dp(24f), dp(24f), 0f, 0f, 0f, 0f)
        }
        addView(TextView(context).apply {
            text = "询问天气意见"
            textSize = 23f
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = "只会发送本次问题、天气摘要和已保存的角色提示词。"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(10))
        })
        question = EditText(context).also {
            it.hint = "例如：今天出门应该穿什么？"
            it.minLines = 2
            it.maxLines = 5
            it.filters = arrayOf(InputFilter.LengthFilter(160))
            it.gravity = Gravity.TOP
            it.setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        status = TextView(context).also {
            it.text = "模型就绪"
            it.textSize = 13f
            it.setPadding(0, dp(8), 0, dp(4))
            addView(it)
        }
        progress = ProgressBar(context).also {
            it.visibility = View.GONE
            addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        answer = TextView(context).also {
            it.textSize = 16f
            it.setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(it)
        }
        send = button("生成天气意见") { submit() }.also { addView(it) }
        addView(button("关闭") { finish() })
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun configureOverlayWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        findViewById<View>(android.R.id.content)?.setBackgroundColor(Color.TRANSPARENT)
        window.setDimAmount(0f)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)
        setFinishOnTouchOutside(true)
        overridePendingTransition(0, 0)
    }

    private fun submit() {
        if (!AiModelManager.isReady(this)) {
            startActivity(Intent(this, AiSettingsActivity::class.java))
            return
        }
        val text = question.text.toString().trim()
        if (text.isEmpty()) {
            toast("请先输入问题")
            return
        }
        if (generating) return
        generating = true
        send.isEnabled = false
        question.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "正在加载模型并分析天气……"
        answer.text = ""
        AiDialogueStore.markThinking(this, text)
        AiKeepAliveService.touch(this)

        scope.launch {
            try {
                renderDialogue()
                val weatherJson = withContext(Dispatchers.IO) {
                    PythonBridge.getAiWeatherContextJson(this@AiQuestionActivity)
                }
                val routed = WeatherPromptRouter.build(weatherJson, text)
                val generated = InferenceEngineImpl.generate(
                    this@AiQuestionActivity,
                    AiModelManager.modelFile(this@AiQuestionActivity).absolutePath,
                    AiDialogueStore.systemPrompt(this@AiQuestionActivity),
                    WeatherPromptRouter.userPrompt(routed, text),
                    stopAfterCodePoints = 35
                )
                val clean = WeatherPromptRouter.combine(routed, generated)
                require(clean.isNotBlank()) { "模型没有生成有效回答" }
                AiDialogueStore.activate(this@AiQuestionActivity, text, clean)
                answer.text = clean
                renderDialogue()
                status.text = "已显示到角色对话框；模型将在空闲 60 秒后释放"
                AiKeepAliveService.touch(this@AiQuestionActivity)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AiDialogueStore.clear(this@AiQuestionActivity)
                runCatching { renderDialogue() }
                status.text = "生成失败：" + error.message
            } catch (error: LinkageError) {
                AiDialogueStore.clear(this@AiQuestionActivity)
                runCatching { renderDialogue() }
                status.text = "本机 AI 组件加载失败：" + (error.message ?: "不支持的设备")
            } finally {
                generating = false
                send.isEnabled = true
                question.isEnabled = true
                progress.visibility = View.GONE
            }
        }
    }

    private suspend fun renderDialogue() = withContext(Dispatchers.IO) {
        val png = PythonBridge.renderWidgetPng(this@AiQuestionActivity)
            ?: error("小组件渲染失败")
        WidgetRefreshController.publishRenderedImageToAll(
            this@AiQuestionActivity, png, false
        )
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun onStop() {
        InferenceEngineImpl.touchIdleWindow()
        AiKeepAliveService.touch(this)
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        overridePendingTransition(0, 0)
        super.onDestroy()
    }
}
