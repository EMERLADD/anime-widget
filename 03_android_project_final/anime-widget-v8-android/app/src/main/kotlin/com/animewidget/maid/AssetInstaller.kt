package com.animewidget.maid

import android.content.Context
import java.io.File

/**
 * 把 assets/anime-widget-data/{assets,data,dialogue,fonts} 解压到
 * <filesDir>/anime-widget/ 下，对应原 Termux 项目里 ~/anime-widget/ 结构。
 * render.py 里 BASE = Path.home() / "anime-widget"，
 * 所以我们让 HOME 指向 <filesDir>，这样 Path.home()/anime-widget 正好对上。
 *
 * 注意：Android 的 AssetManager.list() 不返回以 "." 开头的隐藏文件，
 * 所以 dialogue/.last_weather_cat 这类运行时生成的隐藏文件无法作为 asset 打包，
 * 首次运行后由 dialogue.py 自己在可写目录里生成（其 _load_last_cat/_save_last_cat
 * 均有 try/except 兜底，缺失不报错）。
 */
object AssetInstaller {

    private const val ASSET_ROOT = "anime-widget-data"
    private const val VERSION_MARK_FILE = ".installed_v3_character_bubble_split"

    // 已知的静态子目录（Android assets 无法可靠区分空文件和目录，显式列出更稳妥）
    private val KNOWN_DIRS = listOf("assets", "data", "dialogue", "fonts")

    fun ensureInstalled(context: Context): File {
        val homeDir = context.filesDir
        val targetBase = File(homeDir, "anime-widget")
        val markFile = File(targetBase, VERSION_MARK_FILE)

        if (!markFile.exists()) {
            targetBase.mkdirs()
            for (dirName in KNOWN_DIRS) {
                copyAssetDir(context, "$ASSET_ROOT/$dirName", File(targetBase, dirName))
            }
            markFile.writeText("ok")
        }

        // cache 目录用于渲染输出（widget.png）和 weather/location json 缓存
        File(targetBase, "cache").mkdirs()

        return homeDir
    }

    private fun copyAssetDir(context: Context, assetPath: String, targetDir: File) {
        val am = context.assets
        val entries = am.list(assetPath) ?: return
        targetDir.mkdirs()

        for (name in entries) {
            val childAssetPath = "$assetPath/$name"
            val subEntries = am.list(childAssetPath)
            if (subEntries.isNullOrEmpty()) {
                // 叶子文件
                val outFile = File(targetDir, name)
                am.open(childAssetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                copyAssetDir(context, childAssetPath, File(targetDir, name))
            }
        }
    }
}
