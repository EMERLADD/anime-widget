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


def _refresh_result(manual, lat=None, lon=None, label=None, accuracy=None,
                    provider="android", acquired_at=None, amap_key="",
                    amap_private_key=""):
    result = {"refresh_success": False, "png_path": "", "error": ""}
    try:
        import update_data
        importlib.reload(update_data)
        update_data.refresh_for_location(
            lat, lon, label, accuracy, provider, acquired_at,
            amap_key, amap_private_key
        )
        result["refresh_success"] = True
    except Exception as error:
        result["error"] = str(error)

    if manual:
        from refresh_state import advance_dialogue_sequence
        advance_dialogue_sequence()
    try:
        result["png_path"] = render_widget_png()
    except Exception as error:
        if not result["error"]:
            result["error"] = str(error)
    return json.dumps(result, ensure_ascii=False)


def full_refresh_and_render_result_json(lat=None, lon=None, label=None,
                                        accuracy=None, provider="android",
                                        acquired_at=None, amap_key="",
                                        amap_private_key=""):
    return _refresh_result(False, lat, lon, label, accuracy, provider, acquired_at,
                           amap_key, amap_private_key)


def manual_refresh_and_render_result_json(lat=None, lon=None, label=None,
                                          accuracy=None, provider="android",
                                          acquired_at=None, amap_key="",
                                          amap_private_key=""):
    return _refresh_result(True, lat, lon, label, accuracy, provider, acquired_at,
                           amap_key, amap_private_key)


def full_refresh_and_render(lat=None, lon=None, label=None):
    """Initial Android refresh followed by one final render."""
    result = json.loads(full_refresh_and_render_result_json(lat, lon, label))
    if not result["refresh_success"]:
        raise RuntimeError(result["error"] or "刷新失败")
    return result["png_path"]


def manual_refresh_and_render(lat=None, lon=None, label=None):
    """Refresh location/weather and dialogue, then render exactly one final frame."""
    result = json.loads(manual_refresh_and_render_result_json(lat, lon, label))
    if not result["refresh_success"]:
        raise RuntimeError(result["error"] or "刷新失败")
    return result["png_path"]


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


def test_weather_config_json(text, lat=31.27, lon=121.50,
                             amap_key="", amap_private_key=""):
    try:
        from weather_service import test_config
        result = test_config(json.loads(str(text)), float(lat), float(lon),
                             amap_key, amap_private_key)
    except Exception as e:
        result = {"success": False, "error": str(e)}
    return json.dumps(result, ensure_ascii=False)


def test_amap_geocoding_json(lat, lon, amap_key, amap_private_key):
    try:
        from geocoding_service import reverse_geocode
        place = reverse_geocode(float(lat), float(lon), amap_key, amap_private_key)
        result = {"success": True, "location": place["location"]}
    except Exception as error:
        result = {"success": False, "error": str(error)}
    return json.dumps(result, ensure_ascii=False)


def get_layout_config_json():
    from layout_service import get_layout_json
    return get_layout_json()


def get_layout_geometry_json():
    from layout_service import get_layout_geometry_json as get_geometry
    return get_geometry()


def reset_layout_config_json():
    from layout_service import reset_layout_json
    return reset_layout_json()


def update_layout_positions_json(text):
    from layout_service import update_positions_json
    return update_positions_json(text)


def get_character_theme_json():
    from theme_engine import load_theme
    return json.dumps(load_theme(), ensure_ascii=False)

def import_character_png(path):
    from theme_engine import import_character
    return json.dumps(import_character(str(path)), ensure_ascii=False)

def restore_default_character():
    from theme_engine import restore_default_character as restore
    return json.dumps(restore(), ensure_ascii=False)

def get_theme_extraction_json():
    from theme_engine import load_extraction_config
    return json.dumps(load_extraction_config(), ensure_ascii=False)

def save_theme_extraction_json(text):
    from theme_engine import save_extraction_config
    return json.dumps(save_extraction_config(json.loads(str(text))), ensure_ascii=False)

def get_theme_preferences_json():
    from theme_engine import get_theme_preferences
    return json.dumps(get_theme_preferences(), ensure_ascii=False)

def save_theme_preferences_json(text):
    from theme_engine import save_theme_preferences
    return json.dumps(save_theme_preferences(json.loads(str(text))), ensure_ascii=False)
