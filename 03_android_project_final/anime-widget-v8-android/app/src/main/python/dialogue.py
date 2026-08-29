from pathlib import Path
import random

# Priority: severe/specific weather -> late night -> daypart -> global fallback.
# Each category is an independent txt file. One non-empty line = one candidate.
# Write literal \n in a line if you want to force a line break in the bubble.

DEFAULTS = {
    "global": ["今天也陪着主人。"],
    "morning": ["早上好，\\n今天也请多关照。"],
    "day": ["下午好，\\n要来杯茶吗？"],
    "evening": ["欢迎回来，\\n今天辛苦了。"],
    "night": ["今晚也\\n辛苦啦。"],
    "late_night": ["已经很晚了，\\n要记得休息哦。"],
    "sunny": ["天气很好呢，\\n适合出去走走。"],
    "cloudy": ["今天云有点多呢。"],
    "overcast": ["今天是阴天呢。"],
    "fog": ["外面有雾，\\n出门注意安全哦。"],
    "drizzle": ["有一点毛毛雨，\\n出门记得带伞。"],
    "rain": ["外面可能会下雨，\\n记得带伞。"],
    "heavy_rain": ["雨有点大，\\n出门一定要小心哦。"],
    "snow": ["下雪啦，\\n出门要穿暖一点。"],
    "thunder": ["有雷雨呢，\\n尽量早点回来哦。"],
    "hot": ["今天很热，\\n要记得补充水分。"],
    "cold": ["今天有点冷，\\n主人要穿暖一点哦。"],
}

def _load(folder: Path, category: str):
    path = folder / f"{category}.txt"
    try:
        lines = [
            s.strip().replace("\\\\n", "\n")
            for s in path.read_text(encoding="utf-8").splitlines()
            if s.strip() and not s.lstrip().startswith("#")
        ]
        if lines:
            return lines
    except Exception:
        pass
    return [s.replace("\\\\n", "\n") for s in DEFAULTS.get(category, DEFAULTS["global"])]

def _pick(folder: Path, category: str, now, variation=0):
    lines = _load(folder, category)
    # Stable inside each 30-minute slot. Manual refresh advances deterministically,
    # avoiding accidental repeats when the category contains multiple lines.
    slot = f"{now:%Y-%m-%d-%H}-{now.minute // 30}-{category}"
    base_index = random.Random(slot).randrange(len(lines))
    return lines[(base_index + int(variation)) % len(lines)]

def _weather_category(code: int):
    if code in (0, 1):
        return "sunny"
    if code == 2:
        return "cloudy"
    if code == 3:
        return "overcast"
    if code in (45, 48):
        return "fog"
    if code in (51, 53, 55):
        return "drizzle"
    if code in (61, 63, 80, 81):
        return "rain"
    if code in (65, 82):
        return "heavy_rain"
    if code in (71, 73, 75, 77, 85, 86):
        return "snow"
    if code in (95, 96, 99):
        return "thunder"
    return None

# 问题天气（暴雨/雷雨/大雪）：权重最高，直接顶掉 global 与普通天气。
SEVERE_CODES = {65, 82, 95, 96, 99, 75, 86}
# 普通天气时，约有 30% 概率顺带聊一句天气，其余时间闲聊。
WEATHER_PROB = 0.3
# 记录上次天气类别，用于“天气变化时强制提醒一句”（同目录隐藏文件）。
_LAST_CAT_FILE = ".last_weather_cat"

def _load_last_cat(folder):
    try:
        return (folder / _LAST_CAT_FILE).read_text(encoding="utf-8").strip() or None
    except Exception:
        return None

def _save_last_cat(folder, cat_str):
    try:
        (folder / _LAST_CAT_FILE).write_text(cat_str, encoding="utf-8")
    except Exception:
        pass

def get_maid_line(weather, now, folder, variation=0):
    folder = Path(folder)
    code = int(weather.get("weather_code", -1))
    temp = weather.get("temperature")
    cat = _weather_category(code)
    cat_str = cat if cat is not None else "none"

    # 跨渲染记录上次类别（当前每次 render 都是独立进程，需落盘才能记住）。
    last = _load_last_cat(folder)
    changed = last is not None and last != cat_str
    _save_last_cat(folder, cat_str)

    # 第一层（最高/覆盖）：问题天气 + 极端温度，顶掉下面两层。
    if code in SEVERE_CODES:
        return _pick(folder, cat, now, variation)
    if isinstance(temp, (int, float)) and temp >= 33:
        return _pick(folder, "hot", now, variation)
    if isinstance(temp, (int, float)) and temp <= 5:
        return _pick(folder, "cold", now, variation)

    # 第二层（主：global 闲聊） vs 第三层（次：普通天气）。
    # 天气类别变化时强制聊一句；否则按概率偶尔聊。
    slot = f"{now:%Y%m%d%H}{now.minute // 30}-{variation}"
    if cat is not None and (changed or random.Random("blend:" + slot).random() < WEATHER_PROB):
        return _pick(folder, cat, now, variation)
    return _pick(folder, "global", now, variation)
