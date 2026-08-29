from datetime import datetime
from pathlib import Path
import json
import math
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
    from weather_service import fetch_weather as provider_fetch
    return provider_fetch(loc)

def _distance_meters(lat1, lon1, lat2, lon2):
    radius = 6_371_000.0
    p1, p2 = math.radians(float(lat1)), math.radians(float(lat2))
    dp = math.radians(float(lat2) - float(lat1))
    dl = math.radians(float(lon2) - float(lon1))
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


def refresh_for_location(lat=None, lon=None, label=None):
    """Android path: use supplied/cached coordinates and never call termux-location."""
    old_location = read_json(LOCATION_CACHE) or {}
    if lat is not None and lon is not None:
        moved_far = True
        try:
            moved_far = _distance_meters(
                old_location["latitude"], old_location["longitude"], lat, lon
            ) >= 1_000
        except Exception:
            pass

        cached_label = old_location.get("location")
        if label:
            location_name = label
        elif cached_label and not moved_far:
            location_name = cached_label
        else:
            location_name = reverse_geocode(lat, lon) or cached_label or DEFAULT_LOCATION

        loc = {
            "latitude": float(lat),
            "longitude": float(lon),
            "location": location_name,
            "accuracy": None,
            "updated": datetime.now().isoformat(),
        }
    elif old_location:
        loc = old_location
    else:
        loc = {
            "latitude": DEFAULT_LATITUDE,
            "longitude": DEFAULT_LONGITUDE,
            "location": DEFAULT_LOCATION,
            "accuracy": None,
            "updated": datetime.now().isoformat(),
        }

    write_json(LOCATION_CACHE, loc)
    old_weather = read_json(WEATHER_CACHE)
    try:
        weather = fetch_weather(loc)
        write_json(WEATHER_CACHE, weather)
        print("Android 天气数据更新成功:", weather["location"])
        return weather
    except Exception as e:
        print("Android 天气更新失败:", e)
        if old_weather:
            print("保留旧天气缓存")
            return old_weather
        raise


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
