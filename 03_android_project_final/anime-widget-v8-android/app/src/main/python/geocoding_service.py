"""Amap Web Service reverse geocoding with optional digital signatures."""
import hashlib

import requests

from weather_coordinates import wgs84_to_gcj02

AMAP_REVERSE_URL = "https://restapi.amap.com/v3/geocode/regeo"


def _text(value):
    return value.strip() if isinstance(value, str) else ""


def signed_amap_params(params, api_key, private_key):
    api_key, private_key = _text(api_key), _text(private_key)
    if not api_key:
        raise ValueError("请先在应用中保存高德 Web Service Key")
    result = {str(k): str(v) for k, v in params.items()}
    result["key"] = api_key
    if private_key:
        source = "&".join(f"{key}={result[key]}" for key in sorted(result)) + private_key
        result["sig"] = hashlib.md5(source.encode("utf-8")).hexdigest()
    return result


def _payload(response, operation):
    if response.status_code < 200 or response.status_code >= 300:
        raise RuntimeError(f"高德{operation} HTTP {response.status_code}")
    payload = response.json()
    if str(payload.get("status")) != "1":
        info = _text(payload.get("info")) or "未知错误"
        code = _text(payload.get("infocode")) or "UNKNOWN"
        raise RuntimeError(f"高德{operation}失败：{info}（{code}）")
    return payload


def reverse_geocode(lat, lon, api_key, private_key, timeout=8):
    gcj_lat, gcj_lon = wgs84_to_gcj02(float(lat), float(lon))
    params = signed_amap_params({
        "location": f"{gcj_lon:.6f},{gcj_lat:.6f}",
        "extensions": "base",
        "output": "JSON",
    }, api_key, private_key)
    payload = _payload(
        requests.get(AMAP_REVERSE_URL, params=params, timeout=timeout), "逆地理编码"
    )
    component = ((payload.get("regeocode") or {}).get("addressComponent") or {})
    place = {name: _text(component.get(name)) for name in
             ("province", "city", "district", "township", "towncode", "adcode")}
    parts = []
    for value in (place["district"], place["township"]):
        if value and value not in parts:
            parts.append(value)
    if not parts:
        for value in (place["city"], place["province"]):
            if value and value not in parts:
                parts.append(value)
    if not parts:
        raise RuntimeError("高德逆地理编码未返回可显示的行政区划")
    place.update({"location": " · ".join(parts), "coordinate_system": "gcj02",
                  "geocoder": "amap"})
    return place
