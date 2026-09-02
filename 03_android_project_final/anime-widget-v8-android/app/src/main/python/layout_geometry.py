"""Shared 1200x800 module geometry and partial-visibility constraints."""
from copy import deepcopy
from pathlib import Path
from PIL import Image

WIDTH, HEIGHT = 1200, 800
MIN_VISIBLE = 32
MODULE_SIZES = {
    "date": (452, 70), "clock": (480, 180), "location": (340, 95),
    "weather": (562, 225), "forecast": (662, 118), "bubble": (450, 314),
    "maid": (800, 600), "refresh": (176, 176),
}
MODULE_NAMES = ("date", "clock", "location", "weather", "forecast", "bubble", "maid", "refresh")


def current_character_path():
    """Return the first available character source in render priority."""
    base = Path.home() / "anime-widget"
    candidates = (base / "user" / "character.png", base / "assets" / "character_only.png",
                  base / "assets" / "character.png")
    return next((path for path in candidates if path.exists()), candidates[-1])


def _maid_geometry():
    """Geometry at scale=100 without cropping the source image."""
    try:
        with Image.open(current_character_path()) as image:
            rgba = image.convert("RGBA")
            if rgba.height <= 0:
                raise ValueError("empty character")
            ratio = 600 / rgba.height
            frame_w = max(1, int(rgba.width * ratio))
            bbox = rgba.getchannel("A").getbbox()
            if bbox is None:
                raise ValueError("fully transparent character")
            left, top, right, bottom = bbox
            visual = {
                "left": int(left * ratio), "top": int(top * ratio),
                "width": max(1, int((right - left) * ratio)),
                "height": max(1, int((bottom - top) * ratio)),
            }
            return {"frame": {"width": frame_w, "height": 600}, "visual": visual}
    except Exception:
        return {"frame": {"width": 800, "height": 600},
                "visual": {"left": 0, "top": 0, "width": 800, "height": 600}}


def module_geometry_dict(layout=None):
    """Return scale=100 frame and visible-content bounds for all modules."""
    result = {}
    for name in MODULE_NAMES:
        width, height = MODULE_SIZES[name]
        result[name] = {"frame": {"width": width, "height": height},
                        "visual": {"left": 0, "top": 0, "width": width, "height": height}}
    result["maid"] = _maid_geometry()
    return result


def _scale_for(layout, name, explicit=None):
    value = explicit if explicit is not None else (layout or {}).get(name, {}).get("scale", 100)
    try:
        return max(1, int(value)) / 100.0
    except (TypeError, ValueError):
        return 1.0


def clamp_position(name, x, y, layout=None, scale=None):
    """Keep at least 32 design pixels of visible content on the canvas."""
    if name not in MODULE_SIZES:
        raise ValueError(f"未知模块: {name}")
    visual = module_geometry_dict(layout)[name]["visual"]
    factor = _scale_for(layout, name, scale)
    left, top = visual["left"] * factor, visual["top"] * factor
    width, height = visual["width"] * factor, visual["height"] * factor
    keep_x, keep_y = min(MIN_VISIBLE, width), min(MIN_VISIBLE, height)
    min_x, max_x = keep_x - (left + width), WIDTH - keep_x - left
    min_y, max_y = keep_y - (top + height), HEIGHT - keep_y - top
    return int(max(min_x, min(max_x, int(x)))), int(max(min_y, min(max_y, int(y))))


def get_position(layout, name):
    if name not in MODULE_SIZES:
        raise ValueError(f"未知模块: {name}")
    module = layout[name]
    if name == "bubble":
        x, y = module["x1"], module["y1"]
    elif name == "maid":
        frame = module_geometry_dict(layout)[name]["frame"]
        factor = _scale_for(layout, name)
        if "x" in module and "y" in module:
            x, y = module["x"], module["y"]
        else:
            x = WIDTH - int(frame["width"] * factor) - module["right"]
            y = HEIGHT - int(frame["height"] * factor) - module["bottom"]
    else:
        x, y = module["x"], module["y"]
    return clamp_position(name, x, y, layout)


def move_module(layout, name, x, y):
    if name not in MODULE_SIZES:
        raise ValueError(f"未知模块: {name}")
    new_layout = deepcopy(layout)
    module = new_layout[name]
    new_x, new_y = clamp_position(name, x, y, new_layout)
    if name == "bubble":
        dx, dy = new_x - module["x1"], new_y - module["y1"]
        for key, delta in (("x1", dx), ("y1", dy), ("x2", dx), ("y2", dy),
                           ("tail_x", dx), ("tail_y", dy)):
            module[key] += delta
        return new_layout
    module["x"], module["y"] = new_x, new_y
    if name == "maid":
        frame = module_geometry_dict(new_layout)[name]["frame"]
        factor = _scale_for(new_layout, name)
        module["right"] = WIDTH - int(frame["width"] * factor) - new_x
        module["bottom"] = HEIGHT - int(frame["height"] * factor) - new_y
    return new_layout


def positions_json_dict(layout):
    result = {}
    geometry = module_geometry_dict(layout)
    for name in MODULE_NAMES:
        x, y = get_position(layout, name)
        frame = geometry[name]["frame"]
        result[name] = {"x": x, "y": y, "width": frame["width"], "height": frame["height"]}
    return result
