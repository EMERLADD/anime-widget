================================================================
anime-widget v8 -> APK 打包产物合集（Emr260824 版）
================================================================

本压缩包完整保留了从 v8 原始 Python 源码到最终可安装 Android APK
的全部阶段性产物，按处理流程分文件夹整理。全程在 Termux 环境内
完成打包（未使用 PC / Android Studio / proot-distro），核心技术
路径为 Chaquopy（Python-Kotlin 桥接框架）。

--------------------------------------------------------------
目录结构说明
--------------------------------------------------------------
01_source_original/         原始 Python 源码（v8，未经修改）
02_build_environment/       构建环境相关产物
03_android_project_final/   最终 Android Gradle 工程完整源码
04_build_apk/               构建过程中生成的关键资源
05_final_apk/               最终可安装 APK
--------------------------------------------------------------

（1）01_source_original/

v8 版本原始项目源码压缩包及解压后的完整目录，逻辑代码未做改动：
- render.py       主渲染逻辑（PIL 绘图）
- update_data.py  定位与天气数据获取
- dialogue.py     对白选择逻辑（新增天气变化强制提醒、极端温度分支）
- weather_icons.py v8 新增，天气图标程序化绘制模块
  （用 PIL 几何图形按天气类型动态生成，不再依赖外部图标素材，
    并支持夜间/白天的图形差异）
- widget.py       原始 Termux GUI 推送脚本（Android 化后已弃用，存档）
- assets/ data/ dialogue/  角色图、布局配置、对白文本库（含实际内容）

（2）02_build_environment/

- py313-android-aarch64.tar.gz
  Termux 内从 Python 官方源码编译好的完整 Python 3.13.7
  （aarch64 架构），用于 Chaquopy 的 buildPython 配置。
  可直接解压复用，无需重新编译。
- disable_macros.sh
  批量禁用 Android Bionic libc 缺失函数宏的脚本（编译 Python 时用到）
- patch_pwdmodule.sh
  修复 Python 源码 pwdmodule.c 编译报错的补丁脚本

（3）03_android_project_final/

完整的 Android Gradle 工程源码（已排除 build/ 编译缓存，可重新构建）：
- Kotlin 层：
    AnimeMaidWidget.kt   AppWidgetProvider，协程异步渲染
    ConfigActivity.kt    Widget 配置协议 + 定位权限请求
    PythonBridge.kt      Kotlin <-> Python 桥接
    AssetInstaller.kt    首启资源解压
- Python 层：原始四个 .py 文件 + 新增 bridge.py 胶水层（零改动原文件）
- 资源层：app/src/main/assets/anime-widget-data/
    （角色图、对白库、子集化字体）
- Gradle 配置：build.gradle / settings.gradle / gradle.properties
    （含阿里云镜像加速配置）

（4）04_build_apk/

本目录预留，存放构建过程中生成的关键资源与配置说明。
当前版本以程序化天气图标为主，未使用外部图标素材。

（5）05_final_apk/

- anime-widget-Emr260824-debug.apk
  最终产物，debug 签名，可直接安装到 Android 真机。
  已在真机验证运行成功。

--------------------------------------------------------------
技术路线摘要
--------------------------------------------------------------
原项目是一个在 Termux 环境下运行的 Android 桌面小组件（显示时间、
天气、定位、动画角色及智能对白）。由于目标形态是【桌面 Widget
（AppWidgetProvider）】而非普通全屏 App，唯一可行的打包方案是
Chaquopy（不能用 Kivy/Buildozer，那类方案只能生成全屏 Activity，
无法生成真正的桌面 Widget）。

移植过程中原始 Python 逻辑代码基本保持零改动，仅新增一个 bridge.py
承担与 Kotlin 层的参数传递和调用编排；Android 系统能力（定位、Widget
生命周期、后台定时刷新）全部在 Kotlin 层用原生 API 实现。

v8 相比旧版的主要变化：
- 天气图标程序化绘制：新增 weather_icons.py，用 PIL 几何图形按天气
  类型动态生成图标，不再依赖外部图片资源，且支持白天/夜间两种形态
  （内置"资产图覆盖"开关但默认关闭）
- 对白增强：天气变化时强制提醒一句话、极端温度（>=33 或 <=5）分支、
  跨渲染记住上次天气类别
- 天气卡片更丰富：新增体感温度、湿度、3 小时预报（含降水概率）

--------------------------------------------------------------
已知限制
--------------------------------------------------------------
- 当前为 debug 签名 APK，仅供个人测试安装，未做正式签名和混淆
- AlarmManager 使用非精确闹钟，Android 系统 Doze 省电模式下实际刷新
  间隔可能与设定值有出入（系统机制限制，非代码缺陷）
- weather_icons.py 的"资产图覆盖"模式默认关闭（ICON_MODE=procedural）。
  如需改用自定义图片图标，需将 ICON_MODE 改为 asset 并把对应 <kind>.png
  放入 Path.home()/anime-widget/icons/ 目录（改动在工具源码侧，不涉及
  本 APK）
================================================================
