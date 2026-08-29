"""User dialogue storage and selection. Built-in dialogue remains in dialogue.py."""
import random
from config_store import CONFIG_DIR, atomic_write_json, read_json

CONFIG_FILE = CONFIG_DIR / "dialogue_customize.json"
CATEGORIES = ("global", "morning", "day", "evening", "night", "late_night", "sunny", "cloudy", "overcast", "fog", "drizzle", "rain", "heavy_rain", "snow", "thunder", "hot", "cold")
MODES = {"builtin", "custom", "mixed"}


def default_config():
    return {"schema_version": 1, "mode": "mixed", "custom_weight": 50, "categories": {name: [] for name in CATEGORIES}}


def normalize_config(data):
    result = default_config()
    if not isinstance(data, dict): return result
    mode = str(data.get("mode", "mixed"))
    result["mode"] = mode if mode in MODES else "mixed"
    try: result["custom_weight"] = max(0, min(100, int(data.get("custom_weight", 50))))
    except Exception: pass
    source = data.get("categories", {})
    for category in CATEGORIES:
        entries = source.get(category, []) if isinstance(source, dict) else []
        clean, seen = [], set()
        for index, entry in enumerate(entries if isinstance(entries, list) else []):
            if isinstance(entry, str): text, enabled = entry, True
            elif isinstance(entry, dict): text, enabled = entry.get("text", ""), bool(entry.get("enabled", True))
            else: continue
            text = str(text).strip()[:120]
            if not text or text in seen: continue
            seen.add(text)
            clean.append({"id": f"custom_{category}_{index + 1:03d}", "text": text, "enabled": enabled})
            if len(clean) >= 100: break
        result["categories"][category] = clean
    return result


def load_config(): return normalize_config(read_json(CONFIG_FILE, default_config()))
def save_config(data):
    clean = normalize_config(data); atomic_write_json(CONFIG_FILE, clean); return clean

def _lines(config, category):
    return [item["text"].replace("\\n", "\n") for item in config["categories"].get(category, []) if item.get("enabled", True) and item.get("text")]


def _candidate_categories(weather, now):
    from dialogue import SEVERE_CODES, _weather_category
    code, temp = int(weather.get("weather_code", -1)), weather.get("temperature")
    if code in SEVERE_CODES: yield _weather_category(code)
    if isinstance(temp, (int, float)) and temp >= 33: yield "hot"
    if isinstance(temp, (int, float)) and temp <= 5: yield "cold"
    category = _weather_category(code)
    if category: yield category
    hour = now.hour
    yield "late_night" if hour < 5 else "morning" if hour < 10 else "day" if hour < 17 else "evening" if hour < 22 else "night"
    yield "global"


def get_custom_line(weather, now, variation=0, config=None):
    config = config or load_config()
    for category in _candidate_categories(weather, now):
        lines = _lines(config, category)
        if lines:
            seed = f"custom-{now:%Y%m%d%H}-{now.minute // 30}-{category}"
            base = random.Random(seed).randrange(len(lines))
            return lines[(base + int(variation)) % len(lines)]
    return None
