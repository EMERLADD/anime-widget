package com.arm.aichat.internal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Thin one-turn Kotlin wrapper around the official llama.cpp Android JNI bridge. */
object InferenceEngineImpl {
    const val IDLE_UNLOAD_MILLIS = 60_000L
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var initialized = false
    @Volatile private var modelLoaded = false
    private var loadedPath: String? = null
    private var unloadJob: Job? = null

    private external fun init(nativeLibDir: String)
    private external fun load(modelPath: String): Int
    private external fun prepare(): Int
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun generateNextToken(): String?
    private external fun unload()

    suspend fun generate(
        context: Context,
        modelPath: String,
        systemPrompt: String,
        userPrompt: String,
        predictLength: Int = 96,
        stopAfterCodePoints: Int = 60,
        onToken: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            unloadJob?.cancel()
            ensureInitialized(context.applicationContext)
            try {
                ensureModelLoaded(modelPath)
                check(processSystemPrompt(systemPrompt) == 0) { "系统提示词处理失败" }
                check(processUserPrompt(userPrompt, predictLength) == 0) { "天气问题处理失败" }
                val result = StringBuilder()
                while (result.codePointCount(0, result.length) < stopAfterCodePoints) {
                    val token = generateNextToken() ?: break
                    result.append(token)
                    onToken(token)
                }
                result.toString()
            } finally {
                scheduleIdleUnload()
            }
        }
    }

    suspend fun unloadNow() = withContext(Dispatchers.IO) {
        mutex.withLock {
            unloadJob?.cancel()
            unloadLoadedModel()
        }
    }

    suspend fun preload(context: Context, modelPath: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            unloadJob?.cancel()
            ensureInitialized(context.applicationContext)
            try {
                ensureModelLoaded(modelPath)
            } finally {
                scheduleIdleUnload()
            }
        }
    }

    fun touchIdleWindow() {
        scope.launch {
            mutex.withLock {
                if (modelLoaded) scheduleIdleUnload()
            }
        }
    }

    private fun ensureModelLoaded(modelPath: String) {
        if (modelLoaded && loadedPath == modelPath) return
        if (modelLoaded) unloadLoadedModel()
        check(load(modelPath) == 0) { "模型加载失败或架构不受支持" }
        if (prepare() != 0) {
            unload()
            error("模型上下文初始化失败")
        }
        modelLoaded = true
        loadedPath = modelPath
    }

    private fun scheduleIdleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MILLIS)
            mutex.withLock { unloadLoadedModel() }
        }
    }

    private fun unloadLoadedModel() {
        if (!modelLoaded) return
        unload()
        modelLoaded = false
        loadedPath = null
    }

    @Synchronized
    private fun ensureInitialized(context: Context) {
        if (initialized) return
        System.loadLibrary("ai-chat")
        init(context.applicationInfo.nativeLibraryDir)
        initialized = true
    }
}
