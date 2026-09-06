package com.animewidget.maid

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads and verifies the single supported on-device model without storage permissions. */
object AiModelManager {
    const val MODEL_NAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
    const val MODEL_SIZE = 532_517_120L
    const val MODEL_SHA256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
    const val MODEL_REPOSITORY = "unsloth/Qwen3.5-0.8B-GGUF"
    private const val MODEL_URL =
        "https://modelscope.cn/models/$MODEL_REPOSITORY/resolve/master/$MODEL_NAME"
    private const val PREFS = "ai_model_state"
    private const val VERIFIED_SHA = "verified_sha256"

    fun modelFile(context: Context) = File(File(context.filesDir, "models"), MODEL_NAME)
    private fun partialFile(context: Context) = File(modelFile(context).parentFile, "$MODEL_NAME.part")

    fun isReady(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() == MODEL_SIZE &&
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(VERIFIED_SHA, null) == MODEL_SHA256
    }

    fun downloadedBytes(context: Context): Long = when {
        modelFile(context).isFile -> modelFile(context).length()
        partialFile(context).isFile -> partialFile(context).length()
        else -> 0L
    }

    suspend fun download(context: Context, progress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = modelFile(context)
            target.parentFile?.mkdirs()
            if (isReady(context)) return@withContext target

            if (target.exists()) target.delete()
            val partial = partialFile(context)
            if (partial.length() > MODEL_SIZE) partial.delete()
            val existing = partial.length()
            val needed = MODEL_SIZE - existing
            require((target.parentFile?.usableSpace ?: 0L) > needed + 128L * 1024 * 1024) {
                "可用空间不足，模型及校验过程至少还需要约 ${(needed + 128L * 1024 * 1024) / 1024 / 1024} MB"
            }

            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AnimeMaidWidget/2.2")
                if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
            }
            try {
                val code = connection.responseCode
                require(code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                    "模型下载失败：HTTP $code"
                }
                val append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0L
                var written = if (append) existing else 0L
                FileOutputStream(partial, append).use { output ->
                    connection.inputStream.buffered().use { input ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            written += count
                            progress(written, MODEL_SIZE)
                        }
                        output.fd.sync()
                    }
                }
            } finally {
                connection.disconnect()
            }

            require(partial.length() == MODEL_SIZE) {
                "模型大小不完整：${partial.length()} / $MODEL_SIZE 字节，可再次点击继续下载"
            }
            val actual = sha256(partial)
            require(actual == MODEL_SHA256) {
                partial.delete()
                "模型 SHA-256 校验失败，已删除损坏文件"
            }
            require(partial.renameTo(target)) { "模型校验成功，但无法保存到应用目录" }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(VERIFIED_SHA, actual).apply()
            progress(MODEL_SIZE, MODEL_SIZE)
            target
        }

    fun delete(context: Context) {
        modelFile(context).delete()
        partialFile(context).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
