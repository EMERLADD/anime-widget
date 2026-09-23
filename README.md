# Anime Maid Widget（无 AI 分支）

Android 桌面天气小组件，使用 Kotlin、Android RemoteViews 和 Chaquopy/Pillow
实现。此分支保留天气、定位、角色对话、主题配色、模块拖动及应用内窄屏适配，
不包含本地模型、llama.cpp、AI 输入框或 AI 设置入口。

版本：**2.2.2-no-ai**（versionCode 18）

## 功能

- 当前天气、体感温度、湿度和未来三小时预报
- Android 定位与可选高德逆地理编码
- 半小时天气强制刷新、手动刷新和缓存一致性处理
- 角色图片、主题颜色、对话内容和模块位置自定义
- 系统 TextClock，应用进程停止后时间仍可继续更新
- 窄屏及大字体下自适应的应用内按钮布局

## 源码目录

```text
01_source_original/         原始 Python 项目存档
02_build_environment/       Termux/Chaquopy 构建环境资料
03_android_project_final/   Android Gradle 工程
04_build_apk/               构建过程资料
final_apk/                  本地构建的 APK（默认被 Git 忽略）
```

Android 工程位于 `03_android_project_final/anime-widget-v8-android`。
`app/src/main/kotlin/com/animewidget/maid/` 负责系统小组件、定位、刷新、
设置和布局编辑；`app/src/main/python/` 负责天气服务、缓存和图片渲染。

## Termux 构建

共享存储不适合直接执行 Gradle。先将完整工程复制到 Termux 内部临时目录，
使用 Gradle 8.10.2、Android 35、Python 3.13 和 Termux 原生 `aapt2`
执行 `assembleDebug`。项目最低支持 Android 8.0（API 26），目标 ABI 为
`arm64-v8a`。

交付前验证 APK ZIP 完整性、v2 签名、Manifest 中的 Launcher、Widget
Receiver 和 AppWidgetProvider 元数据。

## 安装说明

此分支与主线使用相同的应用 ID `com.animewidget.maid`。若安装包采用与已装
版本相同的签名，可以覆盖升级；若签名不同，Android 会拒绝覆盖安装。
当前 APK 为 debug 签名，仅适合个人测试。
