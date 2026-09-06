from datetime import datetime
from pathlib import Path
import json

from PIL import ImageFont

WIDTH, HEIGHT = 1200, 800
BASE = Path.home() / "anime-widget"
CACHE = BASE / "cache"
ASSETS = BASE / "assets"
CACHE.mkdir(parents=True, exist_ok=True)

OUTPUT = CACHE / "widget.png"
WEATHER_CACHE = CACHE / "weather.json"
LOCATION_CACHE = CACHE / "location.json"
SNAPSHOT_CACHE = CACHE / "widget_snapshot.json"
CHARACTER = ASSETS / "character_only.png"
LEGACY_CHARACTER = ASSETS / "character.png"
LAYOUT_FILE = BASE / "data" / "layout.json"

DEFAULT_LATITUDE, DEFAULT_LONGITUDE = 31.27, 121.50
DEFAULT_LOCATION = "位置获取中"
LEGACY_DEFAULT_LOCATION = "上海 · 虹口"
CACHE_MINUTES = 30

_FONT_CANDIDATES = [
    BASE / "fonts" / "NotoSansSC-Regular.ttf",
    Path("/system/fonts/MiSansVF.ttf"),
    Path("/system/fonts/NotoSansCJK-Regular.ttc"),
]

def font(size, bold=False):
    for path in _FONT_CANDIDATES:
        try:
            if path.suffix == ".ttc":
                return ImageFont.truetype(str(path), size, index=0)
            return ImageFont.truetype(str(path), size)
        except Exception:
            continue
    raise OSError(
        f"找不到可用的中文字体，已尝试: {[str(p) for p in _FONT_CANDIDATES]}"
    )

F_TIME=font(165,True); F_DATE=font(38,True); F_LOC=font(36,True)
F_TEMP=font(98,True); F_WEATHER=font(44,True); F_SMALL=font(30,True)
F_TINY=font(23); F_BUBBLE=font(38,True); F_AI_INPUT=font(28,True)
F_HOUR=font(25,True); F_HOUR_SMALL=font(21,True)

DEFAULT_LAYOUT = {
    "date": {"x": 48, "y": 42, "visible": 1},
    "clock": {"x": 48, "y": 118, "scale": 100, "visible": 1},
    "location": {"x": 50, "y": 315, "visible": 1},
    "weather": {"x": 48, "y": 425, "visible": 1},
    "forecast": {"x": 48, "y": 655, "visible": 1},
    "refresh": {"x": 976, "y": 8, "scale": 100, "visible": 1},
    "maid": {"x": 382, "y": 194, "right": 18, "bottom": 6, "scale": 100, "visible": 1},
    "bubble": {
        "x1": 500, "y1": 170, "x2": 860, "y2": 395,
        "tail_x": 890, "tail_y": 435,
        "radius": 42, "padding": 30, "visible": 1
    },
    "ai_input": {"x": 735, "y": 704, "visible": 1},
}

def load_layout():
    try:
        user = json.loads(LAYOUT_FILE.read_text(encoding="utf-8"))
    except Exception:
        user = {}
    result = {}
    for name, default in DEFAULT_LAYOUT.items():
        cfg = user.get(name, {}) if isinstance(user, dict) else {}
        result[name] = {k: int(cfg.get(k, v)) for k, v in default.items()}
    return result

def weather_name(c):
    return {0:"晴",1:"晴间多云",2:"多云",3:"阴",45:"有雾",48:"雾凇",
            51:"小毛毛雨",53:"毛毛雨",55:"较强毛毛雨",61:"小雨",63:"中雨",65:"大雨",
            71:"小雪",73:"中雪",75:"大雪",77:"米雪",80:"阵雨",81:"较强阵雨",82:"强阵雨",
            85:"阵雪",86:"强阵雪",95:"雷雨",96:"雷雨伴冰雹",99:"强雷雨伴冰雹"}.get(c,"天气未知")

def _snapshot():
    try:
        data = json.loads(SNAPSHOT_CACHE.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            return None
        location, weather = data.get("location"), data.get("weather")
        if not isinstance(location, dict) or not isinstance(weather, dict):
            return None
        generation = data.get("generation")
        if generation and (location.get("generation") != generation or
                           weather.get("generation") != generation):
            return None
        return data
    except Exception:
        return None


def _read_dict(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else None
    except Exception:
        return None


def _module(name, path):
    snapshot = _snapshot()
    standalone = _read_dict(path)
    if not snapshot:
        return standalone
    snapshot_module = snapshot[name]
    standalone_generation = (standalone or {}).get("generation")
    if standalone_generation and standalone_generation != snapshot.get("generation"):
        return standalone
    return snapshot_module


def read_cache():
    return _module("weather", WEATHER_CACHE)

def get_location(weather=None):
    try:
        location = _module("location", LOCATION_CACHE) or {}
        label = location.get("location")
        if label and (
            label != LEGACY_DEFAULT_LOCATION or location.get("is_default") is False
        ):
            return label
    except Exception:
        pass

    # Keep useful legacy weather caches, but never present the old hard-coded
    # Shanghai placeholder as if it were a successfully acquired location.
    label = (weather or {}).get("location")
    if label and label != LEGACY_DEFAULT_LOCATION:
        return label
    return DEFAULT_LOCATION

def get_weather():
    cached = read_cache()
    if cached:
        return cached
    return {
        "temperature":"--","feels_like":"--","humidity":"--","weather_code":-1,
        "wind_speed":"--","is_day":1,"max":"--","min":"--",
        "latitude":DEFAULT_LATITUDE,"longitude":DEFAULT_LONGITUDE,
        "location":DEFAULT_LOCATION,"hourly":[],
        "updated":datetime.now().isoformat()
    }
