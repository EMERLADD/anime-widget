"""
Android 桥接层：供 Kotlin 通过 Chaquopy 调用。
不修改 render.py / update_data.py / dialogue.py / weather_icons.py 任何逻辑，
只是在调用前后做胶水工作：
  1. 接收 Kotlin 侧 Android LocationManager 拿到的经纬度，写入 location.json 缓存
     （复用 update_data.py 的 resolve_location() 读缓存 fallback 逻辑）。
     Android 上 termux-location 不可用，坐标已由 Kotlin 提供，但 reverse_geocode()
     这一步必须在这里手动补上，否则 location 字段永远是空/默认值。
  2. 调用 update_data.main() 刷新天气
  3. 调用 render.py（import 时直接执行渲染并写 PNG）
  4. 返回渲染好的 PNG 绝对路径给 Kotlin
"""
import sys
import json
import importlib
from pathlib import Path
from datetime import datetime

BASE = Path.home() / "anime-widget"
CACHE = BASE / "cache"
LOCATION_CACHE = CACHE / "location.json"


def set_android_location(lat, lon, label=None):
    """由 Kotlin 侧 LocationManager 获取坐标后调用，写入缓存供 update_data.py 使用。"""
    CACHE.mkdir(parents=True, exist_ok=True)

    resolved_label = label
    if not resolved_label:
        try:
            from update_data import reverse_geocode
            resolved_label = reverse_geocode(lat, lon)
        except Exception as e:
            print("Android 定位逆地理编码失败:", e)
            resolved_label = None

    data = {
        "latitude": float(lat),
        "longitude": float(lon),
        "location": resolved_label or "",
        "accuracy": None,
        "updated": datetime.now().isoformat(),
    }
    LOCATION_CACHE.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return True


def refresh_weather():
    """调用原始 update_data.py 的 main()，定位/天气缓存全部走原逻辑。"""
    import update_data
    importlib.reload(update_data)  # 保证 Chaquopy 多次调用时状态干净
    update_data.main()
    return True


def render_widget_png():
    """
    render.py 是"import 即执行"风格（顶层代码直接渲染并写 PNG），
    每次调用需要 reload 以重新执行渲染逻辑。
    返回渲染好的 PNG 绝对路径。
    """
    if "render" in sys.modules:
        import render
        importlib.reload(render)
    else:
        import render  # noqa: F401

    output = CACHE / "widget.png"
    if output.exists():
        return str(output)
    return ""


def _android_refresh(lat=None, lon=None, label=None):
    """Refresh from Android coordinates/cache without invoking termux-location."""
    import update_data
    importlib.reload(update_data)
    return update_data.refresh_for_location(lat, lon, label)


def full_refresh_and_render(lat=None, lon=None, label=None):
    """Initial Android refresh followed by one final render."""
    try:
        _android_refresh(lat, lon, label)
    except Exception as e:
        print("Android 初始刷新异常（将使用缓存或占位数据）:", e)
    return render_widget_png()


def manual_refresh_and_render(lat=None, lon=None, label=None):
    """Refresh location/weather and dialogue, then render exactly one final frame."""
    try:
        _android_refresh(lat, lon, label)
    except Exception as e:
        print("手动刷新天气异常（将使用缓存）:", e)

    from refresh_state import advance_dialogue_sequence
    advance_dialogue_sequence()
    return render_widget_png()


def get_dialogue_config_json():
    from dialogue_customize import load_config
    return json.dumps(load_config(), ensure_ascii=False)


def get_builtin_dialogue_json():
    """Return the actual installed built-in dialogue with stable IDs."""
    from dialogue import get_builtin_entries
    from dialogue_customize import CATEGORIES
    data = {category: get_builtin_entries(BASE / "dialogue", category) for category in CATEGORIES}
    return json.dumps(data, ensure_ascii=False)


def save_dialogue_config_json(text):
    from dialogue_customize import save_config
    return json.dumps(save_config(json.loads(str(text))), ensure_ascii=False)


def get_weather_config_json():
    from weather_service import config_json
    return config_json()


def save_weather_config_json(text):
    from weather_service import save_config
    return json.dumps(save_config(json.loads(str(text))), ensure_ascii=False)


def test_weather_config_json(text, lat=31.27, lon=121.50):
    try:
        from weather_service import test_config
        result = test_config(json.loads(str(text)), float(lat), float(lon))
    except Exception as e:
        result = {"success": False, "error": str(e)}
    return json.dumps(result, ensure_ascii=False)
