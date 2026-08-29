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
CHARACTER = ASSETS / "character.png"
LAYOUT_FILE = BASE / "data" / "layout.json"

DEFAULT_LATITUDE, DEFAULT_LONGITUDE = 31.27, 121.50
DEFAULT_LOCATION = "上海 · 虹口"
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
F_TINY=font(23); F_BUBBLE=font(38,True)
F_HOUR=font(25,True); F_HOUR_SMALL=font(21,True)

DEFAULT_LAYOUT = {
    "date": {"x": 48, "y": 42},
    "clock": {"x": 48, "y": 118},
    "location": {"x": 50, "y": 315},
    "weather": {"x": 48, "y": 425},
    "forecast": {"x": 48, "y": 655},
    "maid": {"right": 18, "bottom": 6},
    "bubble": {"x1": -100, "y1": -225, "x2": 1430, "y2": 875}
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

def read_cache():
    try:
        return json.loads(WEATHER_CACHE.read_text(encoding="utf-8"))
    except Exception:
        return None

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
