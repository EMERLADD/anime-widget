"""Independent location/weather refresh with an atomic combined snapshot."""
from datetime import datetime, timezone
from pathlib import Path
import json
import os
import subprocess
import uuid

from config_store import atomic_write_json

BASE = Path.home() / "anime-widget"
CACHE = BASE / "cache"
CACHE.mkdir(parents=True, exist_ok=True)
WEATHER_CACHE = CACHE / "weather.json"
LOCATION_CACHE = CACHE / "location.json"
SNAPSHOT_CACHE = CACHE / "widget_snapshot.json"
REFRESH_STATUS = CACHE / "refresh_status.json"


def read_json(path):
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else None
    except Exception:
        return None


def write_json(path, data):
    atomic_write_json(path, data)


def _now():
    return datetime.now(timezone.utc).isoformat()


def get_device_location():
    try:
        process = subprocess.run(
            ["termux-location", "-p", "network", "-r", "once"],
            capture_output=True, text=True, timeout=15,
        )
        if process.returncode != 0:
            raise RuntimeError(process.stderr.strip() or f"exit={process.returncode}")
        data = json.loads(process.stdout)
        return float(data["latitude"]), float(data["longitude"]), data.get("accuracy")
    except Exception as error:
        print("定位失败:", error)
        return None


def reverse_geocode(lat, lon, amap_key="", amap_private_key=""):
    from geocoding_service import reverse_geocode as amap_reverse
    return amap_reverse(lat, lon, amap_key, amap_private_key)["location"]


def fetch_weather(location, amap_key="", amap_private_key=""):
    from weather_service import fetch_weather as provider_fetch
    return provider_fetch(location, amap_key=amap_key, amap_private_key=amap_private_key)


def _safe_error(error, secrets=()):
    text = str(error)
    for secret in secrets:
        if secret:
            text = text.replace(str(secret), "***")
    return text[:500]


def _write_status(success, generation, stage, errors=None, modules=None):
    status = {"success": bool(success), "generation": generation,
              "stage": stage, "updated": _now(), "modules": modules or {}}
    if errors:
        status["error"] = "；".join(errors)[:1000]
    write_json(REFRESH_STATUS, status)


def refresh_for_location(lat=None, lon=None, label=None, accuracy=None,
                         provider="android", acquired_at=None,
                         amap_key="", amap_private_key=""):
    """Refresh location and weather independently; publish a snapshot if both succeed."""
    generation = uuid.uuid4().hex
    secrets = (amap_key, amap_private_key)
    if lat is None or lon is None:
        error = "本轮强制刷新未取得当前坐标"
        _write_status(False, generation, "location", [error])
        raise RuntimeError(error)
    lat, lon = float(lat), float(lon)
    if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
        error = "定位坐标超出有效范围"
        _write_status(False, generation, "location", [error])
        raise ValueError(error)

    base_location = {
        "generation": generation, "latitude": lat, "longitude": lon,
        "accuracy": accuracy, "provider": str(provider or "android"),
        "acquired_at": acquired_at or _now(), "updated": _now(),
        "source": "android", "is_default": False,
    }
    location = None
    weather = None
    errors = []
    modules = {"location": False, "weather": False, "snapshot": False}

    try:
        from geocoding_service import reverse_geocode as amap_reverse
        place = amap_reverse(lat, lon, amap_key, amap_private_key)
        location = dict(base_location)
        location.update({"location": str(label or place["location"]), "place": place})
        write_json(LOCATION_CACHE, location)
        modules["location"] = True
    except Exception as error:
        errors.append("地名解析：" + _safe_error(error, secrets))

    weather_input = location or {
        **base_location, "location": "位置解析失败", "place": {}
    }
    try:
        weather = fetch_weather(weather_input, amap_key, amap_private_key)
        if not isinstance(weather, dict):
            raise RuntimeError("天气服务未返回有效对象")
        weather = dict(weather)
        weather.update({"generation": generation, "latitude": lat, "longitude": lon,
                        "updated": weather.get("updated") or _now()})
        write_json(WEATHER_CACHE, weather)
        modules["weather"] = True
    except Exception as error:
        errors.append("天气刷新：" + _safe_error(error, secrets))

    if location is not None and weather is not None:
        snapshot = {"schema_version": 1, "generation": generation,
                    "location": location, "weather": weather, "published_at": _now()}
        write_json(SNAPSHOT_CACHE, snapshot)
        modules["snapshot"] = True
        _write_status(True, generation, "complete", modules=modules)
        return weather

    _write_status(False, generation, "partial" if any(modules.values()) else "failed",
                  errors, modules)
    raise RuntimeError("；".join(errors) or "刷新失败")


def main():
    position = get_device_location()
    if not position:
        raise RuntimeError("无法取得当前坐标")
    lat, lon, accuracy = position
    return refresh_for_location(
        lat, lon, accuracy=accuracy, provider="termux-location",
        amap_key=os.environ.get("AMAP_WEB_KEY", ""),
        amap_private_key=os.environ.get("AMAP_PRIVATE_KEY", ""),
    )


if __name__ == "__main__":
    main()
