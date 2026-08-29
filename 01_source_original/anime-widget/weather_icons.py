"""Weather icon rendering, decoupled from render.py.

render.py only ever calls get_weather_icon(code, size, night).
The drawing backend is pluggable:

  * default : draw_procedural() -> geometric icons drawn with PIL
  * future  : set ICON_MODE = "asset" and drop <kind>.png files into
              ICON_DIR; those override the procedural ones. No change
              to render.py required. (dormant for now)
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageChops

# ---- WMO weather code -> semantic icon kind (pure data) ----
WMO_TO_KIND = {
    0: "sun", 1: "sun",
    2: "partly_cloudy",
    3: "cloud",
    45: "fog", 48: "fog",
    51: "rain", 53: "rain", 55: "rain",
    61: "rain", 63: "rain", 65: "heavy_rain",
    71: "snow", 73: "snow", 75: "snow", 77: "snow",
    80: "rain", 81: "rain", 82: "heavy_rain",
    85: "snow", 86: "snow",
    95: "thunder", 96: "thunder", 99: "thunder",
}

# ---- customization seam (dormant) ----
ICON_MODE = "procedural"                         # "procedural" | "asset"
ICON_DIR = Path.home() / "anime-widget" / "icons"

# ---- palette (whites / blues; warm for sun & lightning) ----
SUN = (255, 214, 92, 255)
MOON = (233, 240, 255, 255)
CL = (245, 250, 255, 230)      # cloud
RN = (150, 200, 245, 255)      # rain
SW = (255, 255, 255, 255)      # snow
BT = (255, 221, 92, 255)       # bolt
FG = (220, 235, 245, 220)      # fog lines


def _canvas(size):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def _cloud(draw, size, color, cx=None, cy=None, scale=1.0):
    """A lumpy cloud made of overlapping ellipses."""
    if cx is None:
        cx = size * 0.5
    if cy is None:
        cy = size * 0.5
    s = size * scale
    r = s * 0.18
    for ox, oy, k in ((-0.34, 0.10, 1.0), (0.0, -0.08, 1.3),
                      (0.34, 0.10, 1.0), (0.0, 0.20, 1.5)):
        x, y, rad = cx + ox * s, cy + oy * s, r * k
        draw.ellipse([x - rad, y - rad, x + rad, y + rad], fill=color)


def _sun(draw, size, cx=None, cy=None, r=None):
    if cx is None:
        cx = size * 0.5
    if cy is None:
        cy = size * 0.5
    if r is None:
        r = size * 0.20
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=SUN)
    for i in range(8):
        a = i * math.pi / 4
        r0, r1 = r * 1.25, r * 1.6
        draw.line([cx + r0 * math.cos(a), cy + r0 * math.sin(a),
                   cx + r1 * math.cos(a), cy + r1 * math.sin(a)],
                  fill=SUN, width=max(2, int(size * 0.045)))


def _moon(size):
    """Crescent moon on its own transparent canvas."""
    img = _canvas(size)
    d = ImageDraw.Draw(img)
    c = size * 0.5
    r = size * 0.26
    d.ellipse([c - r, c - r, c + r, c + r], fill=MOON)
    cut = Image.new("L", (size, size), 0)
    ImageDraw.Draw(cut).ellipse(
        [c - r * 0.85 + size * 0.16, c - r, c + r * 0.85 + size * 0.16, c + r],
        fill=255)
    img.putalpha(ImageChops.multiply(img.split()[3], ImageChops.invert(cut)))
    return img


def draw_procedural(kind, size=64, night=False):
    img = _canvas(size)
    d = ImageDraw.Draw(img)

    if kind == "sun":
        if night:
            img.alpha_composite(_moon(size), (0, 0))
        else:
            _sun(d, size)

    elif kind == "partly_cloudy":
        if night:
            img.alpha_composite(_moon(size), (int(size * 0.30), 0))
            _cloud(d, size, CL, cx=size * 0.44, cy=size * 0.64, scale=0.95)
        else:
            _sun(d, size, cx=size * 0.66, cy=size * 0.32, r=size * 0.15)
            _cloud(d, size, CL, cx=size * 0.44, cy=size * 0.66, scale=0.95)

    elif kind == "cloud":
        _cloud(d, size, CL, cy=size * 0.5)

    elif kind == "fog":
        _cloud(d, size, CL, cy=size * 0.38, scale=0.9)
        for i in range(3):
            y = size * (0.60 + i * 0.12)
            d.line([size * 0.18, y, size * 0.82, y],
                   fill=FG, width=max(2, int(size * 0.04)))

    elif kind in ("rain", "heavy_rain"):
        _cloud(d, size, CL, cy=size * 0.36, scale=0.95)
        n = 5 if kind == "heavy_rain" else 3
        w = max(2, int(size * 0.05)) if kind == "heavy_rain" else max(2, int(size * 0.035))
        for i in range(n):
            x = size * 0.5 if n == 1 else size * (0.26 + i * 0.48 / (n - 1))
            d.line([x, size * 0.56, x - size * 0.06, size * 0.80],
                   fill=RN, width=w)

    elif kind == "snow":
        _cloud(d, size, CL, cy=size * 0.36, scale=0.95)
        for i in range(4):
            x = size * (0.30 + i * 0.14)
            y = size * (0.60 + (i % 2) * 0.12)
            r = size * 0.035
            d.ellipse([x - r, y - r, x + r, y + r], fill=SW)

    elif kind == "thunder":
        _cloud(d, size, CL, cy=size * 0.34, scale=0.95)
        pts = [(size * 0.54, size * 0.54), (size * 0.40, size * 0.72),
               (size * 0.52, size * 0.72), (size * 0.38, size * 0.90)]
        d.line(pts, fill=BT, width=max(2, int(size * 0.05)))

    else:
        _cloud(d, size, CL, cy=size * 0.5)

    return img


def get_icon(kind, size=64, night=False):
    if ICON_MODE == "asset":
        p = Path(ICON_DIR) / f"{kind}.png"
        try:
            im = Image.open(p).convert("RGBA")
            im.thumbnail((size, size), Image.LANCZOS)
            return im
        except Exception:
            pass            # fall back to procedural
    return draw_procedural(kind, size, night)


def get_weather_icon(code, size=64, night=False):
    kind = WMO_TO_KIND.get(int(code), "cloud")
    return get_icon(kind, size, night)
