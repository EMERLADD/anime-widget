"""Character two-color extraction and deterministic theme generation."""
import colorsys, json, math, os
from pathlib import Path
from PIL import Image
BASE=Path.home()/"anime-widget"; DATA=BASE/"data"; USER=BASE/"user"
THEME_FILE=DATA/"theme.json"; EXTRACTION_FILE=DATA/"theme_extraction.json"; PREFERENCES_FILE=DATA/"theme_preferences.json"
USER_CHARACTER=USER/"character.png"; BUILTIN_CHARACTER=BASE/"assets"/"character_only.png"
DEFAULT_PRIMARY="#48A4F2"; DEFAULT_SECONDARY="#AEDCFA"
DEFAULT_TEXT="#FFFFFF"; DEFAULT_BUBBLE_TEXT=DEFAULT_TEXT
DEFAULT_PREFERENCES={"schema_version":1,"mode":"auto","manual":{"primary":DEFAULT_PRIMARY,"secondary":DEFAULT_SECONDARY,"text":DEFAULT_TEXT,"bubble_text":DEFAULT_BUBBLE_TEXT},"manual_initialized":False,"app_follow_theme":True}
DEFAULT_EXTRACTION={"schema_version":1,"sample_alpha_min":48,"maximum_dimension":8192,"maximum_pixels":33554432,"minimum_color_distance":42.0,"white_black":{"enabled":True,"white_luminance_min":.92,"white_saturation_max":.10,"black_luminance_max":.08,"allow_as_primary":False,"allow_as_secondary":True,"weight":.25},"skin_filter":{"enabled":True,"strength":1.0,"hue_min":5.0,"hue_max":48.0,"saturation_min":.10,"saturation_max":.78,"value_min":.30}}
def _merge(a,b):
 r=dict(a)
 for k,v in b.items(): r[k]=_merge(r[k],v) if k in r and isinstance(r[k],dict) and isinstance(v,dict) else v
 return r
def _atomic(path,value):
 path.parent.mkdir(parents=True,exist_ok=True); tmp=path.with_name(path.name+".tmp")
 with tmp.open("w",encoding="utf-8") as f: json.dump(value,f,ensure_ascii=False,indent=2,sort_keys=True); f.flush(); os.fsync(f.fileno())
 os.replace(tmp,path)
def _best_effort_preferences(value):
 try: _atomic(PREFERENCES_FILE,value)
 except OSError: pass
 return value
def _color(value,name):
 if not isinstance(value,str) or len(value)!=7 or value[0]!="#" or any(c not in "0123456789abcdefABCDEF" for c in value[1:]): raise ValueError(name+" 必须是 #RRGGBB")
 return value.upper()
def load_extraction_config():
 try:
  value=json.loads(EXTRACTION_FILE.read_text(encoding="utf-8")); return _merge(DEFAULT_EXTRACTION,value) if isinstance(value,dict) else _merge({},DEFAULT_EXTRACTION)
 except (OSError,ValueError): return _merge({},DEFAULT_EXTRACTION)
def _hex(c): return "#%02X%02X%02X"%tuple(max(0,min(255,round(x))) for x in c)
def _rgb(s):
 s=str(s).lstrip("#")[-6:]; return tuple(int(s[i:i+2],16) for i in (0,2,4))
def _mix(a,b,x): return tuple(a[i]*(1-x)+b[i]*x for i in range(3))
def _lum(c):
 v=[x/255/12.92 if x/255<=.04045 else ((x/255+.055)/1.055)**2.4 for x in c]; return .2126*v[0]+.7152*v[1]+.0722*v[2]
def _contrast(a,b):
 hi,lo=sorted((_lum(a),_lum(b)),reverse=True); return (hi+.05)/(lo+.05)
def _contrast_text(background):
 white=(255,255,255); black=(18,22,30)
 return white if _contrast(white,background)>=_contrast(black,background) else black
def _app_resolved(primary,secondary):
 p,s=_rgb(primary),_rgb(secondary); white=(255,255,255)
 background=_mix(s,white,.88); surface=_mix(s,white,.96)
 return {"app_primary":_hex(p),"app_secondary":_hex(s),"app_background":_hex(background),"app_surface":_hex(surface),"app_on_primary":_hex(_contrast_text(p)),"app_text":_hex(_contrast_text(background)),"app_outline":_hex(_mix(p,s,.52))}
def _with_app_fields(value):
 source=value.get("source",{}) if isinstance(value,dict) else {}
 primary=source.get("primary",DEFAULT_PRIMARY); secondary=source.get("secondary",DEFAULT_SECONDARY)
 try: app=_app_resolved(_color(primary,"主题色一"),_color(secondary,"主题色二"))
 except (ValueError,TypeError): app=_app_resolved(DEFAULT_PRIMARY,DEFAULT_SECONDARY)
 value["resolved"].update({k:v for k,v in app.items() if k not in value["resolved"]}); return value
def _open(path,config,transparent=False):
 try:
  with Image.open(Path(path)) as src:
   if src.format!="PNG": raise ValueError("角色图片必须是 PNG")
   w,h=src.size
   if w<1 or h<1: raise ValueError("角色图片尺寸无效")
   if max(w,h)>int(config["maximum_dimension"]) or w*h>int(config["maximum_pixels"]): raise ValueError("角色图片尺寸过大")
   if "A" not in src.getbands() and "transparency" not in src.info: raise ValueError("角色 PNG 必须包含透明通道")
   image=src.convert("RGBA"); alpha=image.getchannel("A"); box=alpha.getbbox()
   if box is None: raise ValueError("角色图片不能全透明")
   if transparent and alpha.getextrema()[0]==255: raise ValueError("角色 PNG 必须包含透明背景")
   return image,box
 except Image.DecompressionBombError as e: raise ValueError("角色图片尺寸过大") from e
 except OSError as e: raise ValueError("无法读取角色 PNG") from e
def _weight(c,config):
 hue,sat,val=colorsys.rgb_to_hsv(*(x/255 for x in c)); hue*=360; wb=config["white_black"]; lum=_lum(c)
 neutral=bool(wb["enabled"] and ((lum>=wb["white_luminance_min"] and sat<=wb["white_saturation_max"]) or lum<=wb["black_luminance_max"]))
 skin=config["skin_filter"]; is_skin=bool(skin["enabled"] and skin["hue_min"]<=hue<=skin["hue_max"] and skin["saturation_min"]<=sat<=skin["saturation_max"] and val>=skin["value_min"])
 weight=float(wb["weight"]) if neutral else 1.; weight*=max(0.,1-float(skin["strength"])) if is_skin else 1.; return neutral,weight
def extract_palette(path,config=None):
 config=_merge(DEFAULT_EXTRACTION,config or load_extraction_config()); image,_=_open(path,config); image.thumbnail((192,192),Image.Resampling.LANCZOS); bins={}
 for r,g,b,a in image.getdata():
  if a<int(config["sample_alpha_min"]): continue
  key=(r>>4,g>>4,b>>4); neutral,w=_weight((r,g,b),config); item=bins.setdefault(key,[0.,0.,0.,0.,False]); item[0]+=w; item[1]+=r*w; item[2]+=g*w; item[3]+=b*w; item[4]|=neutral
 choices=[]
 for key,(score,rs,gs,bs,neutral) in bins.items():
  if score<=0: continue
  c=(round(rs/score),round(gs/score),round(bs/score)); sat=colorsys.rgb_to_hsv(*(x/255 for x in c))[1]; choices.append((score*(.85+.30*sat),key,c,neutral))
 choices.sort(key=lambda x:(-x[0],x[1]))
 if not choices: raise ValueError("角色图片没有可用于取色的像素")
 wb=config["white_black"]; pool=[x for x in choices if wb["allow_as_primary"] or not x[3]]
 if not pool: raise ValueError("角色图片没有符合主色规则的像素")
 primary=pool[0][2]; pool=[x for x in choices if wb["allow_as_secondary"] or not x[3]]; secondary=next((x[2] for x in pool if math.dist(primary,x[2])>=float(config["minimum_color_distance"])),None)
 if secondary is None: secondary=_mix(primary,(255,255,255) if _lum(primary)<.45 else (0,0,0),.55)
 return {"primary":_hex(primary),"secondary":_hex(secondary)}
def generate_theme(primary,secondary,mode="character"):
 p,s=_rgb(primary),_rgb(secondary); white=(255,255,255); black=(18,22,30); pill=_mix(p,black,.38); weather=_mix(_mix(p,s,.25),black,.34); text=white if min(_contrast(white,pill),_contrast(white,weather))>=4.5 else black; bubble=_mix(s,white,.78); bubble_text=black if _contrast(black,bubble)>=4.5 else white
 resolved={"gradient_top":_hex(_mix(p,white,.20)),"gradient_bottom":_hex(_mix(s,white,.32)),"pill_background":"#A5"+_hex(pill)[1:],"weather_background":"#80"+_hex(weather)[1:],"forecast_background":"#87"+_hex(_mix(_mix(p,s,.45),black,.28))[1:],"border":"#91"+_hex(_mix(s,white,.70))[1:],"refresh_background":_hex(_mix(p,s,.25)),"text":_hex(text),"text_stroke":_hex(_mix(p,black,.68) if text==white else _mix(s,white,.75)),"bubble_fill":"#EC"+_hex(bubble)[1:],"bubble_outline":"#E1"+_hex(_mix(p,black,.25))[1:],"bubble_text":_hex(bubble_text),"bubble_shadow":"#66000000"}
 resolved.update(_app_resolved(_hex(p),_hex(s)))
 return {"schema_version":1,"mode":mode,"source":{"primary":_hex(p),"secondary":_hex(s)},"resolved":resolved}
def classic_theme():
 value={"schema_version":1,"mode":"classic","source":{"primary":DEFAULT_PRIMARY,"secondary":DEFAULT_SECONDARY,"text":DEFAULT_TEXT},"resolved":{"gradient_top":"#48A4F2","gradient_bottom":"#AEDCFA","pill_background":"#A51458AE","weather_background":"#803774C0","forecast_background":"#872364AF","border":"#91E1F5FF","refresh_background":"#66C2E8","text":"#FFFFFF","text_stroke":"#1E4A96","bubble_fill":"#ECFAFDFF","bubble_outline":"#E1749DD6","bubble_text":"#1B3777","bubble_shadow":"#66000000"}}
 value["resolved"].update(_app_resolved(DEFAULT_PRIMARY,DEFAULT_SECONDARY)); return value
def _default(): return classic_theme()
def _manual_theme(manual):
 p=_color(manual.get("primary"),"主题色一"); s=_color(manual.get("secondary"),"主题色二"); text=_color(manual.get("text"),"字体颜色"); bubble_text=_color(manual.get("bubble_text",text),"对话文字颜色"); value=generate_theme(p,s,"manual"); value["source"]["text"]=text; value["source"]["bubble_text"]=bubble_text; value["resolved"]["text"]=text; value["resolved"]["bubble_text"]=bubble_text
 rgb=_rgb(text); dark=(18,22,30); white=(255,255,255); value["resolved"]["text_stroke"]=_hex(dark if _contrast(rgb,dark)>=_contrast(rgb,white) else white); return value
def _validate_preferences(value):
 if not isinstance(value,dict): raise ValueError("主题偏好必须是对象")
 mode=value.get("mode")
 if mode not in ("auto","manual","classic"): raise ValueError("主题模式必须是 auto、manual 或 classic")
 manual=value.get("manual",{})
 if not isinstance(manual,dict): raise ValueError("手动配色必须是对象")
 manual_initialized=value.get("manual_initialized",False); app_follow_theme=value.get("app_follow_theme",True)
 if not isinstance(manual_initialized,bool): raise ValueError("manual_initialized 必须是布尔值")
 if not isinstance(app_follow_theme,bool): raise ValueError("app_follow_theme 必须是布尔值")
 text=_color(manual.get("text",DEFAULT_TEXT),"字体颜色")
 return {"schema_version":1,"mode":mode,"manual":{"primary":_color(manual.get("primary",DEFAULT_PRIMARY),"主题色一"),"secondary":_color(manual.get("secondary",DEFAULT_SECONDARY),"主题色二"),"text":text,"bubble_text":_color(manual.get("bubble_text",text),"对话文字颜色")},"manual_initialized":manual_initialized,"app_follow_theme":app_follow_theme}
def get_theme_preferences():
 raw={}
 try:
  raw=json.loads(PREFERENCES_FILE.read_text(encoding="utf-8"))
  if not isinstance(raw,dict): raise ValueError("主题偏好必须是对象")
  migrated=dict(raw)
  if not isinstance(migrated.get("manual_initialized"),bool): migrated["manual_initialized"]=migrated.get("mode")=="manual"
  if not isinstance(migrated.get("app_follow_theme"),bool): migrated["app_follow_theme"]=True
  value=_validate_preferences(migrated)
  if value!=raw: _best_effort_preferences(value)
  return value
 except FileNotFoundError:
  try: old=json.loads(THEME_FILE.read_text(encoding="utf-8"))
  except (OSError,ValueError): old={}
  source=old.get("source",{}) if isinstance(old,dict) else {}; resolved=old.get("resolved",{}) if isinstance(old,dict) else {}; old_mode=old.get("mode") if isinstance(old,dict) else None; mode=old_mode if old_mode in ("manual","classic") else "auto"
  try: value=_validate_preferences({"mode":mode,"manual":{"primary":source.get("primary",DEFAULT_PRIMARY),"secondary":source.get("secondary",DEFAULT_SECONDARY),"text":source.get("text",resolved.get("text",DEFAULT_TEXT)),"bubble_text":source.get("bubble_text",resolved.get("bubble_text",source.get("text",resolved.get("text",DEFAULT_TEXT))))},"manual_initialized":mode=="manual","app_follow_theme":True})
  except (ValueError,TypeError): value=_merge({},DEFAULT_PREFERENCES)
  return _best_effort_preferences(value)
 except (OSError,ValueError,TypeError):
  try:
   raw=raw if isinstance(raw,dict) else {}
   mode=raw.get("mode","auto")
   value=_validate_preferences({"mode":mode,"manual":raw.get("manual",{}),"manual_initialized":mode=="manual","app_follow_theme":True})
  except (ValueError,TypeError): value=_merge({},DEFAULT_PREFERENCES)
  return _best_effort_preferences(value)
def save_theme_preferences(value):
 prefs=_validate_preferences(value); _atomic(PREFERENCES_FILE,prefs); theme=_manual_theme(prefs["manual"]) if prefs["mode"]=="manual" else classic_theme() if prefs["mode"]=="classic" else analyze_current_character(); _atomic(THEME_FILE,theme); return prefs
def load_theme():
 try:
  value=json.loads(THEME_FILE.read_text(encoding="utf-8"))
  if isinstance(value,dict) and isinstance(value.get("resolved"),dict):
   missing=any(k not in value["resolved"] for k in _app_resolved(DEFAULT_PRIMARY,DEFAULT_SECONDARY))
   upgraded=_with_app_fields(value)
   if missing: _atomic(THEME_FILE,upgraded)
   return upgraded
 except (OSError,ValueError): pass
 prefs=get_theme_preferences()
 try: value=_manual_theme(prefs["manual"]) if prefs["mode"]=="manual" else classic_theme() if prefs["mode"]=="classic" else analyze_current_character()
 except Exception: value=classic_theme()
 _atomic(THEME_FILE,value); return value
def analyze_current_character():
 try:
  path=USER_CHARACTER if USER_CHARACTER.exists() else BUILTIN_CHARACTER
  if not path.exists(): raise ValueError("角色图片不存在")
  value=generate_theme(**extract_palette(path),mode="auto")
 except Exception as e: print("自动主题取色失败，使用经典主题:",e); value=classic_theme()
 _atomic(THEME_FILE,value); return value
def save_extraction_config(config):
 if not isinstance(config,dict): raise ValueError("取色配置必须是对象")
 value=_merge(DEFAULT_EXTRACTION,config); _atomic(EXTRACTION_FILE,value)
 prefs=get_theme_preferences()
 theme=analyze_current_character() if prefs["mode"]=="auto" else _manual_theme(prefs["manual"]) if prefs["mode"]=="manual" else classic_theme()
 _atomic(THEME_FILE,theme); return value
def import_character(source_path):
 image,box=_open(source_path,load_extraction_config(),True); cropped=image.crop(box); USER.mkdir(parents=True,exist_ok=True); tmp=USER_CHARACTER.with_name(USER_CHARACTER.name+".tmp"); cropped.save(tmp,format="PNG",optimize=True); os.replace(tmp,USER_CHARACTER); prefs=get_theme_preferences(); theme=analyze_current_character() if prefs["mode"]=="auto" else _manual_theme(prefs["manual"]) if prefs["mode"]=="manual" else classic_theme(); _atomic(THEME_FILE,theme); return {"character_path":str(USER_CHARACTER),"width":cropped.width,"height":cropped.height,"theme":theme}
def restore_default_character():
 if USER_CHARACTER.exists(): USER_CHARACTER.unlink()
 prefs=get_theme_preferences(); value=analyze_current_character() if prefs["mode"]=="auto" else _manual_theme(prefs["manual"]) if prefs["mode"]=="manual" else classic_theme(); _atomic(THEME_FILE,value); return {"character_path":str(BUILTIN_CHARACTER),"theme":value}
