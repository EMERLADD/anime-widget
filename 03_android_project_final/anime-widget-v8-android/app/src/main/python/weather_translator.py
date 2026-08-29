"""Hard-coded translators plus a safe declarative custom JSON translator."""
from datetime import datetime
from weather_schema import normalize, number, integer

def path(data, dotted, default=None):
    value=data
    try:
        for part in str(dotted).split("."):
            value=value[int(part)] if isinstance(value,list) else value[part]
        return value
    except (KeyError,IndexError,TypeError,ValueError): return default

def _wmo_from_qweather(code):
    c=integer(code)
    if c==100:return 0
    if c in (101,102,103):return 2
    if c==104:return 3
    if 300<=c<=304:return 95
    if c in (305,309,314):return 61
    if c in (306,315):return 63
    if 307<=c<=312 or c in (316,317,318):return 65
    if 400<=c<=410 or c==499:return 71 if c in (400,407,499) else 73 if c in (401,408) else 75
    if 500<=c<=515:return 45
    return -1

def _wmo_from_text(text):
    s=str(text or "")
    if "雷" in s:return 95
    if "暴雨" in s or "大雨" in s:return 65
    if "雨" in s:return 61
    if "雪" in s:return 71
    if "雾" in s or "霾" in s:return 45
    if "阴" in s:return 3
    if "云" in s:return 2
    if "晴" in s:return 0
    return -1

def translate_open_meteo(bundle, context):
    raw=bundle["response"]; c=raw.get("current",{}); d=raw.get("daily",{}); h=raw.get("hourly",{})
    times,temps,codes,probs=h.get("time",[]),h.get("temperature_2m",[]),h.get("weather_code",[]),h.get("precipitation_probability",[]); future=[]; now=datetime.now()
    for i,t in enumerate(times):
        try: parsed=datetime.fromisoformat(t)
        except Exception: continue
        if parsed>now:
            future.append({"time":parsed.strftime("%H:%M"),"temperature":temps[i],"weather_code":codes[i],"rain_probability":probs[i] or 0})
        if len(future)>=3:break
    return normalize({**context,"provider":"open_meteo","temperature":c.get("temperature_2m"),"feels_like":c.get("apparent_temperature"),"humidity":c.get("relative_humidity_2m"),"weather_code":c.get("weather_code"),"wind_speed":c.get("wind_speed_10m"),"is_day":c.get("is_day"),"max":(d.get("temperature_2m_max") or [-1])[0],"min":(d.get("temperature_2m_min") or [-1])[0],"hourly":future})

def translate_qweather(bundle, context):
    c=bundle["current"]; future=[]
    for item in bundle["hourly"].get("hours",[])[:3]:
        prob=number(path(item,"precipitation.probability")); prob=prob*100 if 0<=prob<=1 else prob
        future.append({"time":str(item.get("forecastTime",""))[11:16],"temperature":path(item,"temperature.value"),"weather_code":_wmo_from_qweather(path(item,"condition.code")),"rain_probability":prob})
    humidity=number(c.get("humidity")); humidity=humidity*100 if 0<=humidity<=1 else humidity
    return normalize({**context,"provider":"qweather","temperature":path(c,"temperature.value"),"feels_like":path(c,"feelsLike.value"),"humidity":humidity,"weather_code":_wmo_from_qweather(path(c,"condition.code")),"weather_text":path(c,"condition.text","天气未知"),"wind_speed":path(c,"wind.speed.value"),"is_day":1 if 6<=datetime.now().hour<18 else 0,"hourly":future,"attribution":"QWeather"})

def translate_amap(bundle, context):
    live=(bundle["live"].get("lives") or [{}])[0]; forecast=(bundle["forecast"].get("forecasts") or [{}])[0]; cast=(forecast.get("casts") or [{}])[0]; text=live.get("weather","天气未知")
    return normalize({**context,"provider":"amap","temperature":live.get("temperature"),"humidity":live.get("humidity"),"weather_code":_wmo_from_text(text),"weather_text":text,"wind_speed":-1,"is_day":1 if 6<=datetime.now().hour<18 else 0,"max":cast.get("daytemp"),"min":cast.get("nighttemp"),"hourly":[]})

def _mapped(raw, spec, default=-1):
    if isinstance(spec,str): return path(raw,spec,default)
    if not isinstance(spec,dict): return default
    value=path(raw,spec.get("path",""),spec.get("default",default)); lookup=spec.get("lookup",{})
    if isinstance(lookup,dict): value=lookup.get(str(value),value)
    try: value=number(value)*number(spec.get("multiply",1),1)+number(spec.get("offset",0),0)
    except Exception: pass
    return value

def translate_custom(bundle, context, config):
    raw=bundle["response"]; mapping=config.get("mapping",{}); hourly=[]; hs=mapping.get("hourly",{})
    items=path(raw,hs.get("path",""),[]) if isinstance(hs,dict) else []
    for item in items if isinstance(items,list) else []:
        fields=hs.get("items",{}); hourly.append({"time":str(_mapped(item,fields.get("time"),"--:--"))[-5:],"temperature":_mapped(item,fields.get("temperature")),"weather_code":integer(_mapped(item,fields.get("weather_code"))),"rain_probability":integer(_mapped(item,fields.get("rain_probability")))})
        if len(hourly)>=3:break
    return normalize({**context,"provider":"custom","temperature":_mapped(raw,mapping.get("temperature")),"feels_like":_mapped(raw,mapping.get("feels_like")),"humidity":_mapped(raw,mapping.get("humidity")),"weather_code":integer(_mapped(raw,mapping.get("weather_code"))),"weather_text":str(_mapped(raw,mapping.get("weather_text"),"天气未知")),"wind_speed":_mapped(raw,mapping.get("wind_speed")),"is_day":integer(_mapped(raw,mapping.get("is_day"))),"max":_mapped(raw,mapping.get("max")),"min":_mapped(raw,mapping.get("min")),"hourly":hourly})
