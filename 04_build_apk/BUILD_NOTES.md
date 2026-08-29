# 构建说明与关键资源（Emr260824 版）

本目录存放 v8 构建过程中生成的关键资源与重新构建需注意的事项。

## 目录内容

- `NotoSansSC-Regular.ttf`：子集化裁剪后的中文字体（7.4MB）。
  v8 的 `render.py` 字体路径硬编码为 `/system/fonts/MiSansVF.ttf`，该字体仅存在于
  小米 HyperOS 系统。在目标设备（vivo / OriginOS）上不存在，直接渲染会抛异常
  导致整个 APK 渲染失败。为此做了如下处理：
  1. 将本字体打包进 APK 的 `assets/anime-widget-data/fonts/`
  2. `render.py` 的 `font()` 函数改为三级 fallback：APK内置字体 → 系统 MiSansVF →
     系统 NotoSansCJK（.ttc 需指定 index=0）

## 字体子集化方法

原始 Noto Sans SC Regular 全字体约 10.5MB，使用 fonttools 子集化仅保留实际
用到的字符后缩减到 7.4MB。子集化必须用脚本机器扫描源码全部字符串字面量，
不能靠人工猜测会用到哪些字符（经验教训：旧版曾遗漏 `●`(U+25CF) 和 `·`(U+00B7)
导致真机显示空白/方块）。

子集化覆盖范围：
```
U+0020-007E, U+00B0, U+00B7, U+2014, U+201C, U+201D, U+2022, U+25CF,
U+3000-303F, U+FF00-FFEF, U+4E00-9FFF
```

需要覆盖的字符（v8 实际用到、且不在 CJK 基本区的特殊符号）：
- `°`  U+00B0  （温度符号）
- `·`  U+00B7  （地址间隔号，如 "上海 · 虹口"）
- `—`  U+2014  （破折号）
- `"` U+201C  `"` U+201D  （中文引号）
- `●`  U+25CF  （定位图标前缀圆点）

校验方法（用 fontTools 逐码位确认存在）：
```python
from fontTools.ttLib import TTFont
font = TTFont("NotoSansSC-Regular.ttf")
cmap = font.getBestCmap()
for code in (0x00B0, 0x00B7, 0x2014, 0x201C, 0x201D, 0x25CF):
    print(hex(code), code in cmap)
```

## v8 天气图标说明

v8 版本新增 `weather_icons.py`，天气图标改为**程序化绘制**（PIL 几何图形），
不再依赖外部图标图片素材。该模块内置一个"资产图覆盖"开关：

```python
ICON_MODE = "procedural"   # "procedural"（默认）| "asset"
ICON_DIR = Path.home() / "anime-widget" / "icons"
```

- 默认 `ICON_MODE = "procedural"`：直接用 PIL 画几何图形（太阳/云/雨滴/雪花/
  闪电），支持白天/夜间两种形态
- 若改为 `"asset"`：会从 `ICON_DIR` 读取 `<kind>.png` 覆盖程序化图形
  （该模式当前处于休眠状态，默认未启用）

因此 **v8 不需要任何外部天气图标素材**，这是与旧版（v7）最大的架构差异。

## 重新构建指引

1. 解压 `03_android_project_final/anime-widget-v8-android/`
2. 确保 Termux 环境具备 JDK21 + Android SDK(platform-35, build-tools 35.0.0)
   + Gradle 8.10.2，且 `~/.gradle/gradle.properties` 里已配置 aapt2 override
   （指向 Termux 原生 aapt2）
3. 确保 `~/py313/bin/python3.13` 存在（可解压 `02_build_environment/` 里的
   py313 压缩包得到），build.gradle 里的 `buildPython` 已指向该路径
4. 执行：
   ```bash
   cd anime-widget-v8-android
   sum -P 8022  # 若有环境变量需先 source
   source ~/.bashrc
   gradle assembleDebug --stacktrace
   ```
   输出 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`
