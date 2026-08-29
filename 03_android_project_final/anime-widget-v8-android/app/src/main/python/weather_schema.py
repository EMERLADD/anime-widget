"""Canonical weather schema consumed by dialogue and rendering."""
from datetime import datetime

DEFAULT_WEATHER = {"schema_version": 1, "provider": "unknown", "temperature": -1, "feels_like": -1, "humidity": -1, "weather_code": -1, "weather_text": "天气未知", "wind_speed": -1, "is_day": -1, "max": -1, "min": -1, "latitude": 0.0, "longitude": 0.0, "location": "位置未知", "hourly": [], "attribution": "", "updated": None}

def number(value, default=-1):
    try: return float(value)
    except (TypeError, ValueError): return default

def integer(value, default=-1):
    try: return int(float(value))
    except (TypeError, ValueError): return default

def normalize(data):
    result = dict(DEFAULT_WEATHER); result.update({k:v for k,v in (data or {}).items() if v is not None and k in result})
    for key in ("temperature", "feels_like", "humidity", "wind_speed", "max", "min"): result[key] = number(result[key])
    for key in ("weather_code", "is_day"): result[key] = integer(result[key])
    clean=[]
    for item in result["hourly"] if isinstance(result["hourly"], list) else []:
        if not isinstance(item, dict): continue
        clean.append({"time": str(item.get("time", "--:--")), "temperature": number(item.get("temperature")), "weather_code": integer(item.get("weather_code")), "rain_probability": integer(item.get("rain_probability"))})
        if len(clean)>=3: break
    result["hourly"]=clean; result["updated"] = result["updated"] or datetime.now().isoformat()
    return result
