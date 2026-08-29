"""Weather provider selection, requests, configuration, and normalization."""
import json
from urllib.parse import urlparse
import requests
from config_store import CONFIG_DIR, atomic_write_json, read_json
from weather_coordinates import wgs84_to_gcj02
from weather_translator import translate_amap, translate_custom, translate_open_meteo, translate_qweather

CONFIG_FILE=CONFIG_DIR/"weather_api.json"
PROVIDERS={"open_meteo","qweather","amap","custom"}

def default_config():
    return {"schema_version":1,"provider":"open_meteo","timeout_seconds":6,"open_meteo":{"base_url":"https://api.open-meteo.com/v1/forecast"},"qweather":{"api_host":"","api_key":""},"amap":{"api_key":"","adcode":""},"custom":{"url":"","headers":{},"query":{},"mapping":{}}}

def normalize_config(data):
    result=default_config()
    if not isinstance(data,dict): return result
    provider=str(data.get("provider","open_meteo")); result["provider"]=provider if provider in PROVIDERS else "open_meteo"
    try: result["timeout_seconds"]=max(2,min(20,int(data.get("timeout_seconds",6))))
    except Exception: pass
    for name in PROVIDERS:
        if isinstance(data.get(name),dict): result[name].update(data[name])
    return result

def load_config(): return normalize_config(read_json(CONFIG_FILE,default_config()))
def save_config(data):
    clean=normalize_config(data); atomic_write_json(CONFIG_FILE,clean); return clean

def _url(value):
    value=str(value).strip().rstrip("/")
    if value and not value.startswith(("http://","https://")): value="https://"+value
    return value

def _check_https(value):
    parsed=urlparse(value)
    if parsed.scheme!="https" or not parsed.netloc: raise ValueError("API 地址必须是有效的 HTTPS URL")

def _format(value,lat,lon): return str(value).replace("{latitude}",str(lat)).replace("{longitude}",str(lon)).replace("{lat}",str(lat)).replace("{lon}",str(lon))

def _fetch_open(config,lat,lon,timeout):
    url=_url(config["open_meteo"].get("base_url")); _check_https(url)
    params={"latitude":lat,"longitude":lon,"current":"temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day","daily":"temperature_2m_max,temperature_2m_min","hourly":"temperature_2m,weather_code,precipitation_probability","timezone":"auto","forecast_days":2}
    r=requests.get(url,params=params,timeout=timeout); r.raise_for_status(); return {"response":r.json()}

def _fetch_qweather(config,lat,lon,timeout):
    qc=config["qweather"]; host=_url(qc.get("api_host")); key=str(qc.get("api_key","")).strip()
    _check_https(host)
    if not key: raise ValueError("QWeather API Key 不能为空")
    lat,lon=wgs84_to_gcj02(lat,lon); headers={"X-QW-Api-Key":key,"Accept-Encoding":"gzip"}
    current=requests.get(f"{host}/weather/v1/current/{lat:.2f}/{lon:.2f}",params={"lang":"zh"},headers=headers,timeout=timeout); current.raise_for_status()
    hourly=requests.get(f"{host}/weather/v1/hourly/{lat:.2f}/{lon:.2f}",params={"hours":6,"localTime":"true","lang":"zh"},headers=headers,timeout=timeout); hourly.raise_for_status()
    return {"current":current.json(),"hourly":hourly.json()}

def _fetch_amap(config,lat,lon,timeout):
    ac=config["amap"]; key=str(ac.get("api_key","")).strip(); adcode=str(ac.get("adcode","")).strip()
    if not key: raise ValueError("高德 Web 服务 API Key 不能为空")
    lat,lon=wgs84_to_gcj02(lat,lon)
    if not adcode:
        geo=requests.get("https://restapi.amap.com/v3/geocode/regeo",params={"key":key,"location":f"{lon:.6f},{lat:.6f}","output":"JSON"},timeout=timeout); geo.raise_for_status(); adcode=str((((geo.json().get("regeocode") or {}).get("addressComponent") or {}).get("adcode") or ""))
    if not adcode: raise ValueError("无法获得高德天气所需 adcode")
    base="https://restapi.amap.com/v3/weather/weatherInfo"
    live=requests.get(base,params={"key":key,"city":adcode,"extensions":"base","output":"JSON"},timeout=timeout); live.raise_for_status()
    forecast=requests.get(base,params={"key":key,"city":adcode,"extensions":"all","output":"JSON"},timeout=timeout); forecast.raise_for_status()
    return {"live":live.json(),"forecast":forecast.json()}

def _fetch_custom(config,lat,lon,timeout):
    cc=config["custom"]; url=_format(cc.get("url",""),lat,lon); _check_https(url)
    headers={str(k):_format(v,lat,lon) for k,v in (cc.get("headers") or {}).items()}; query={str(k):_format(v,lat,lon) for k,v in (cc.get("query") or {}).items()}
    r=requests.get(url,headers=headers,params=query,timeout=timeout); r.raise_for_status(); return {"response":r.json()}

def fetch_weather(loc, config_override=None):
    config=normalize_config(config_override) if config_override is not None else load_config(); provider=config["provider"]; lat,lon=float(loc["latitude"]),float(loc["longitude"]); timeout=config["timeout_seconds"]
    context={"latitude":lat,"longitude":lon,"location":loc.get("location") or "位置未知"}
    if provider=="open_meteo": return translate_open_meteo(_fetch_open(config,lat,lon,timeout),context)
    if provider=="qweather": return translate_qweather(_fetch_qweather(config,lat,lon,timeout),context)
    if provider=="amap": return translate_amap(_fetch_amap(config,lat,lon,timeout),context)
    return translate_custom(_fetch_custom(config,lat,lon,timeout),context,config["custom"])

def test_config(data,lat=31.27,lon=121.50):
    config=normalize_config(data); sample=fetch_weather({"latitude":lat,"longitude":lon,"location":"测试位置"},config)
    return {"success":True,"provider":sample["provider"],"temperature":sample["temperature"],"weather_text":sample["weather_text"],"hourly_count":len(sample["hourly"])}

def config_json(): return json.dumps(load_config(),ensure_ascii=False)
