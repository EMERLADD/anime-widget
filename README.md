# Anime Maid Widget

一个以 Android 桌面小组件为主体的本地天气助手。界面由 Kotlin、
Android RemoteViews 和 Chaquopy/Pillow 共同实现，支持天气刷新、定位、
角色对话、主题配色、模块拖动，以及可选的本地小模型天气建议。

当前版本：**2.2.1**

## 主要功能

- Android 原生桌面小组件与启动器设置中心
- 系统 TextClock，应用进程停止后时间仍可继续更新
- 当前天气、体感温度、湿度及未来三小时预报
- Android 定位与可选高德逆地理编码
- 半小时天气强制刷新、手动刷新和缓存一致性处理
- 角色图片、主题颜色、对话内容和模块位置自定义
- 日期、时间、地点、天气、预报、角色、角色对话框、刷新按钮和
  AI 用户输入框独立维护
- 窄屏及大字体下自适应的应用内按钮布局

## v2.2.1 本地 AI 天气助手

本地 AI 是完全可选的，不下载模型也不影响普通天气小组件。

点击小组件上的“点击以询问天气意见”后，会在桌面上方显示一个透明、
无页面切换动画的底部输入浮层。浮层打开时会提前预热模型，提交后回答直接
覆盖角色对话框。用户可以在设置中心恢复普通角色对话。

每次请求只包含：

1. 固定的天气建议规则
2. 最多 50 个字符的用户角色提示词
3. 程序筛选后的当前或未来天气事实
4. 本次用户问题

模型不保存聊天上下文。生成完成或退出浮层后，模型在进程中保留 60 秒；
期间再次询问会重置计时，空闲超时后释放。Android 在内存紧张时仍可能提前
回收应用进程。

### 确定性天气路由

天气范围不交给 0.8B 模型猜测，而是由程序根据问题路由：

- “现在、当前、出门、穿什么、带伞、体感”等词选择当前天气
- “未来、稍后、几小时后、几点、晚上、预报”等词选择未来三小时
- 同时命中或无法明确判断时选择当前和未来

程序会把天气 JSON 重组成中文事实，并将实际天气和温度作为确定性前缀拼入
最终回答。AI 只负责生成简短行动建议，不能重新判断天气类型，从而避免把
“毛毛雨”描述成“天气晴好”。

### 可选模型

- 仓库：<https://modelscope.cn/models/unsloth/Qwen3.5-0.8B-GGUF>
- 文件：`Qwen3.5-0.8B-Q4_K_M.gguf`
- 大小：532,517,120 字节（约 508 MiB）
- SHA-256：
  `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517`

模型只会在用户点击下载按钮后保存到应用私有目录。下载支持暂停、续传、
可用空间检查、文件大小验证和 SHA-256 校验，模型本身不会打进 APK。

推理运行库基于 `ggml-org/llama.cpp` v0.3.0，使用通用 ARM64 CPU 构建。
第三方来源和许可证见
[`THIRD_PARTY_NOTICES.md`](03_android_project_final/anime-widget-v8-android/THIRD_PARTY_NOTICES.md)。

## 源码目录

```text
01_source_original/         原始 Python 项目存档
02_build_environment/       Termux/Chaquopy 构建环境资料
03_android_project_final/   当前 Android Gradle 工程
04_build_apk/               构建过程资料
final_apk/                  本地构建的 APK（默认被 Git 忽略）
```

Android 工程入口：
`03_android_project_final/anime-widget-v8-android`

主要模块：

- `app/src/main/kotlin/com/animewidget/maid/`：Activity、Widget、定位、
  刷新、布局编辑、模型管理和天气路由
- `app/src/main/kotlin/com/arm/aichat/internal/`：llama.cpp JNI 生命周期
- `app/src/main/python/`：天气服务、缓存、布局和 Pillow 渲染
- `app/src/main/jniLibs/arm64-v8a/`：通用 ARM64 推理运行库
- `app/src/main/assets/anime-widget-data/`：角色、字体、对话和默认配置

## Termux 构建

项目位于 Android 共享存储时，不要直接执行 Gradle。Chaquopy 和部分构建步骤
需要正常的 Unix 文件权限，应先把完整工程复制到 Termux 内部临时目录。

构建环境：

- Gradle 8.10.2
- Android compileSdk/targetSdk 35
- minSdk 26
- Java 17
- Python 3.13（Chaquopy buildPython）
- Termux 原生 `aapt2`
- 目标 ABI：`arm64-v8a`

示例流程：

```sh
cp -R 03_android_project_final/anime-widget-v8-android \
  /data/data/com.termux/files/usr/tmp/anime-widget-build
cd /data/data/com.termux/files/usr/tmp/anime-widget-build
printf 'sdk.dir=/data/data/com.termux/files/home/android-sdk\n' > local.properties
/data/data/com.termux/files/home/.local/gradle/gradle-8.10.2/bin/gradle \
  --no-daemon \
  -Dorg.gradle.project.android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 \
  clean assembleDebug
```

输出位于 `app/build/outputs/apk/debug/app-debug.apk`。交付前应验证 ZIP 完整性、
APK v2 签名、Manifest 中的 Launcher、Widget Receiver 及 AppWidgetProvider
元数据。

## 已知限制

- 当前产物使用 Android debug 签名，仅适合个人测试
- 仅打包 ARM64，不能安装在只支持 32 位 ABI 的设备上
- 标准 RemoteViews 不支持直接编辑文本，因此桌面输入通过透明 Activity 浮层实现
- 首次读取约 508 MiB 模型仍受手机存储速度和内存性能影响
- 系统省电、后台限制或内存压力可能延迟刷新或提前结束 60 秒模型保温
- 本地 0.8B 模型只负责简短建议，不适合复杂推理或长期对话
