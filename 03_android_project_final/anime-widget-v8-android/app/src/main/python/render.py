from PIL import Image, ImageDraw, ImageFilter
from dialogue_selector import get_maid_line
import weather_icons
from datetime import datetime
import os
import random
from refresh_state import get_dialogue_sequence

from widget_config import (
    WIDTH, HEIGHT, BASE, OUTPUT, DEFAULT_LOCATION,
    F_TIME, F_DATE, F_LOC, F_TEMP, F_WEATHER, F_SMALL, F_TINY, F_BUBBLE,
    F_HOUR_SMALL, F_AI_INPUT, load_layout, weather_name, get_weather, get_location,
)
from widget_ui import rounded, centered_text
from widget_theme import theme_color
from dialogue_bubble import draw_dialogue_bubble
from ai_dialogue import active_answer
from ai_input import draw_ai_input
from layout_geometry import current_character_path

LAYOUT = load_layout()

# render.py intentionally remains the composition layer: it decides what is drawn
# and in which order. Configuration/data access and reusable drawing primitives live
# in widget_config.py and widget_ui.py. Hard-coded visual values are unchanged.

w=get_weather(); LOCATION=get_location(w); now=datetime.now(); code=w["weather_code"]
condition=weather_name(code)
weekday=["星期一","星期二","星期三","星期四","星期五","星期六","星期日"][now.weekday()]
night=not bool(w.get("is_day",1))
rainy=code in {51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99}
snowy=code in {71,73,75,77,85,86}
rng=random.Random(now.strftime("%Y%m%d%H"))

# ---- bright anime sky background ----
# These two widget-wide colors are isolated in widget_theme.py so a future
# character palette extractor can replace them without touching composition.
top = theme_color("gradient_top", (72, 164, 242))
bottom = theme_color("gradient_bottom", (174, 220, 250))
border_color = theme_color("border", (225, 245, 255, 145))
pill_color = theme_color("pill_background", (20, 88, 174, 165))
weather_color = theme_color("weather_background", (55, 116, 192, 128))
forecast_color = theme_color("forecast_background", (35, 100, 175, 135))
text_color = theme_color("text", (255, 255, 255))
text_stroke_color = theme_color("text_stroke", (30, 74, 150))

img=Image.new("RGBA",(WIDTH,HEIGHT),(0,0,0,255)); draw=ImageDraw.Draw(img)
for y in range(HEIGHT):
    t=y/HEIGHT
    rgb=tuple(int(top[i]+(bottom[i]-top[i])*t) for i in range(3))
    draw.line((0,y,WIDTH,y),fill=(*rgb,255))

# soft clouds on a separate blurred layer
cloud=Image.new("RGBA",(WIDTH,HEIGHT),(0,0,0,0)); cd=ImageDraw.Draw(cloud)
for cx,cy,s in [(120,90,1.0),(390,50,.8),(820,110,1.0),(1080,230,.75),(520,660,.55)]:
    if rainy: col=(220,230,240,90)
    else: col=(255,255,255,105)
    for ox,oy,r in [(-55,10,55),(0,-5,72),(60,12,52),(15,30,75)]:
        rr=int(r*s); x=int(cx+ox*s); y=int(cy+oy*s)
        cd.ellipse((x-rr,y-rr,x+rr,y+rr),fill=col)
cloud=cloud.filter(ImageFilter.GaussianBlur(14)); img.alpha_composite(cloud); draw=ImageDraw.Draw(img)

# weather atmosphere
if night:
    for _ in range(28):
        x=rng.randrange(WIDTH); y=rng.randrange(240); r=rng.choice([1,1,2])
        draw.ellipse((x-r,y-r,x+r,y+r),fill=(255,250,225,150))
    draw.ellipse((1050,45,1150,145),fill=(255,246,200,190))
elif rainy:
    for _ in range(45):
        x=rng.randrange(WIDTH); y=rng.randrange(HEIGHT)
        draw.line((x,y,x-5,y+18),fill=(225,242,255,95),width=2)
elif snowy:
    for _ in range(36):
        x=rng.randrange(WIDTH); y=rng.randrange(HEIGHT); r=rng.randint(2,4)
        draw.ellipse((x-r,y-r,x+r,y+r),fill=(255,255,255,150))

# border
draw.rounded_rectangle((14,14,1186,786),radius=42,outline=border_color,width=3)

# drawing helpers are in widget_ui.py

# date pill
DATE_X,DATE_Y=LAYOUT["date"]["x"],LAYOUT["date"]["y"]
if LAYOUT["date"].get("visible", 1):
    rounded(draw, (DATE_X,DATE_Y,DATE_X+452,DATE_Y+70),26,pill_color)
    centered_text(draw, (DATE_X,DATE_Y,DATE_X+452,DATE_Y+70),f"{now:%Y / %m / %d}  ·  {weekday}",F_DATE,text_color)

# Time is rendered by Android TextClock so it keeps ticking without waking Python.

# location pill — two-line live location, kept clear of the character
parts=LOCATION.split(" · ",1)
if len(parts)==2:
    loc_city,loc_district=parts
else:
    loc_city,loc_district=LOCATION,""

LOC_X1,LOC_Y1=LAYOUT["location"]["x"],LAYOUT["location"]["y"]
LOC_X2,LOC_Y2=LOC_X1+340,LOC_Y1+95
if LAYOUT["location"].get("visible", 1):
    rounded(draw, (LOC_X1,LOC_Y1,LOC_X2,LOC_Y2),27,pill_color)

if LAYOUT["location"].get("visible", 1) and loc_district:
    # City on the first line, district/county on the second.
    centered_text(draw, (LOC_X1+12,LOC_Y1+8,LOC_X2-12,LOC_Y1+48),
                  f"●  {loc_city}",F_LOC,text_color)
    centered_text(draw, (LOC_X1+12,LOC_Y1+48,LOC_X2-12,LOC_Y2-7),
                  loc_district,F_LOC,text_color)
elif LAYOUT["location"].get("visible", 1):
    centered_text(draw, (LOC_X1+12,LOC_Y1,LOC_X2-12,LOC_Y2),
                  f"●  {loc_city}",F_LOC,text_color)

# weather card
WX,WY=LAYOUT["weather"]["x"],LAYOUT["weather"]["y"]
if LAYOUT["weather"].get("visible", 1):
    rounded(draw, (WX,WY,WX+562,WY+225),36,weather_color)
    draw.text((WX+27,WY+23),f"{w['temperature']}°",font=F_TEMP,fill=text_color,
              stroke_width=2,stroke_fill=text_stroke_color)
    main_icon = weather_icons.get_weather_icon(w["weather_code"], 64, night)
    if main_icon:
        img.alpha_composite(main_icon, (WX+270, WY+28))
        condition_x = WX+342
    else:
        condition_x = WX+282
    draw.text((condition_x,WY+37),condition,font=F_WEATHER,fill=text_color)
    draw.text((WX+284,WY+105),f"体感 {w['feels_like']}°",font=F_SMALL,fill=text_color)

# next 3 hours forecast — three larger cards
future=w.get("hourly",[])
if future and LAYOUT["forecast"].get("visible", 1):
    FC_X1,FC_Y1=LAYOUT["forecast"]["x"],LAYOUT["forecast"]["y"]
    CARD_W=210
    CARD_H=118
    GAP=16

    for i,item in enumerate(future[:3]):
        x1=FC_X1+i*(CARD_W+GAP)
        y1=FC_Y1
        x2=x1+CARD_W
        y2=y1+CARD_H

        rounded(draw, (x1,y1,x2,y2),22,forecast_color,outline=border_color,width=1)

        centered_text(draw, (x1,y1+8,x2,y1+38),
                      item["time"],F_HOUR_SMALL,text_color)
        centered_text(draw, (x1,y1+40,x2,y1+76),
                      f"{weather_name(item['weather_code'])}  {item['temperature']}°",
                      F_HOUR_SMALL,text_color)
        centered_text(draw, (x1,y1+78,x2,y2-5),
                      f"降水概率：{item['rain_probability']}%",
                      F_TINY,text_color)

# bottom-left area reserved for future chat UI

# dialogue decision is decoupled into dialogue.py
line = active_answer() or os.environ.get("ANIME_WIDGET_DIALOGUE_OVERRIDE") or get_maid_line(
    w, now, BASE / "dialogue", variation=get_dialogue_sequence()
)

# The dialogue bubble is its own layer and is drawn behind the character.
if LAYOUT["bubble"].get("visible", 1):
    draw_dialogue_bubble(img, line, F_BUBBLE, LAYOUT["bubble"])
    draw=ImageDraw.Draw(img)

# Character-only layer on lower-right. The legacy combined image is a fallback
# for installations which have not received the split asset yet.
character_path = current_character_path()
if character_path.exists() and LAYOUT["maid"].get("visible", 1):
    char=Image.open(character_path).convert("RGBA")
    maid_scale=max(50,min(150,int(LAYOUT["maid"].get("scale",100))))
    target_h=600*maid_scale//100
    sc=target_h/char.height
    char=char.resize((int(char.width*sc),target_h),Image.Resampling.LANCZOS)
    maid_x=LAYOUT["maid"].get("x",WIDTH-char.width-LAYOUT["maid"]["right"])
    maid_y=LAYOUT["maid"].get("y",HEIGHT-char.height-LAYOUT["maid"]["bottom"])
    img.alpha_composite(char,(maid_x,maid_y))
    draw=ImageDraw.Draw(img)

# tiny update stamp
try: stamp=datetime.fromisoformat(w["updated"]).strftime("%H:%M")
except: stamp="--:--"
if LAYOUT["weather"].get("visible", 1):
    draw.text((WX+287,WY+160),f"更新 {stamp}",font=F_TINY,fill=text_color)

# The native transparent touch target uses the same layout JSON coordinates.
if LAYOUT["ai_input"].get("visible", 1):
    draw_ai_input(img, F_AI_INPUT, LAYOUT["ai_input"])

temp_output = OUTPUT.with_suffix(".tmp.png")
img.convert("RGB").save(temp_output,"PNG",optimize=True)
temp_output.replace(OUTPUT)
print("Rendered:",OUTPUT)
print(f"{LOCATION} | {w['temperature']}° {condition} | 体感 {w['feels_like']}°")
