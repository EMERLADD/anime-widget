from datetime import datetime
from pathlib import Path
import json
import subprocess
import requests

BASE = Path.home() / "anime-widget"
CACHE = BASE / "cache"
CACHE.mkdir(parents=True, exist_ok=True)

WEATHER_CACHE = CACHE / "weather.json"
LOCATION_CACHE = CACHE / "location.json"

DEFAULT_LATITUDE, DEFAULT_LONGITUDE = 31.27, 121.50
DEFAULT_LOCATION = "上海 · 虹口"

def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None

def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

def get_device_location():
    try:
        p = subprocess.run(
            ["termux-location", "-p", "network", "-r", "once"],
            capture_output=True, text=True, timeout=15
        )
        if p.returncode != 0:
            raise RuntimeError(p.stderr.strip() or f"exit={p.returncode}")
        d = json.loads(p.stdout)
        return float(d["latitude"]), float(d["longitude"]), d.get("accuracy")
    except Exception as e:
        print("定位失败:", e)
        return None

def reverse_geocode(lat, lon):
    try:
        from geopy.geocoders import Nominatim
        g = Nominatim(user_agent="anime-weather-widget/1.0", timeout=8)
        r = g.reverse((lat, lon), language="zh-CN", addressdetails=True, zoom=14)
        a = r.raw.get("address", {}) if r else {}

        city = (a.get("city") or a.get("municipality") or a.get("town")
                or a.get("state") or "")
        district = (a.get("city_district") or a.get("district")
                    or a.get("borough") or a.get("suburb")
                    or a.get("county") or "")

        for s in ("特别行政区", "市"):
            if city.endswith(s):
                city = city[:-len(s)]
        for s in ("自治县", "新区", "区", "县"):
            if district.endswith(s):
                district = district[:-len(s)]

        if city and district and city != district:
            return f"{city} · {district}"
        return city or district or None
    except Exception as e:
        print("逆地理编码失败:", e)
        return None

def resolve_location():
    old = read_json(LOCATION_CACHE)
    pos = get_device_location()

    if pos:
        lat, lon, accuracy = pos
        label = reverse_geocode(lat, lon) or (old or {}).get("location") or DEFAULT_LOCATION
        data = {
            "latitude": lat,
            "longitude": lon,
            "location": label,
            "accuracy": accuracy,
            "updated": datetime.now().isoformat()
        }
        write_json(LOCATION_CACHE, data)
        print(f"当前位置: {label} | {lat:.5f}, {lon:.5f}")
        return data

    if old:
        print("定位失败，沿用上次位置:", old.get("location", DEFAULT_LOCATION))
        return old

    print("定位失败且无缓存，使用默认位置:", DEFAULT_LOCATION)
    return {
        "latitude": DEFAULT_LATITUDE,
        "longitude": DEFAULT_LONGITUDE,
        "location": DEFAULT_LOCATION,
        "accuracy": None,
        "updated": datetime.now().isoformat()
    }

def fetch_weather(loc):
    lat = float(loc["latitude"])
    lon = float(loc["longitude"])
    location_name = loc.get("location") or DEFAULT_LOCATION

    params = {
        "latitude": lat,
        "longitude": lon,
        "current": "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day",
        "daily": "temperature_2m_max,temperature_2m_min",
        "hourly": "temperature_2m,weather_code,precipitation_probability",
        "timezone": "auto",
        "forecast_days": 2
    }

    r = requests.get(
        "https://api.open-meteo.com/v1/forecast",
        params=params, timeout=8
    )
    r.raise_for_status()
    x = r.json()
    c, d, h = x["current"], x["daily"], x.get("hourly", {})

    future = []
    now_local = datetime.now()
    times = h.get("time", [])
    temps = h.get("temperature_2m", [])
    codes = h.get("weather_code", [])
    probs = h.get("precipitation_probability", [])

    for i, time_str in enumerate(times):
        try:
            t = datetime.fromisoformat(time_str)
        except Exception:
            continue
        if t > now_local:
            future.append({
                "time": t.strftime("%H:%M"),
                "temperature": round(temps[i]),
                "weather_code": int(codes[i]),
                "rain_probability": round(probs[i] or 0)
            })
        if len(future) >= 3:
            break

    return {
        "temperature": round(c["temperature_2m"]),
        "feels_like": round(c["apparent_temperature"]),
        "humidity": round(c["relative_humidity_2m"]),
        "weather_code": int(c["weather_code"]),
        "wind_speed": round(c["wind_speed_10m"]),
        "is_day": int(c.get("is_day", 1)),
        "max": round(d["temperature_2m_max"][0]),
        "min": round(d["temperature_2m_min"][0]),
        "latitude": lat,
        "longitude": lon,
        "location": location_name,
        "hourly": future,
        "updated": datetime.now().isoformat()
    }

def main():
    loc = resolve_location()
    old_weather = read_json(WEATHER_CACHE)
    try:
        weather = fetch_weather(loc)
        write_json(WEATHER_CACHE, weather)
        print("天气数据更新成功:", weather["location"])
    except Exception as e:
        print("天气更新失败:", e)
        if old_weather:
            print("保留旧天气缓存")
        else:
            raise

if __name__ == "__main__":
    main()
