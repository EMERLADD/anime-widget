"""Shared widget colors backed by the Kotlin/Python theme JSON contract."""
from pathlib import Path
THEME_FILE = Path.home() / "anime-widget" / "data" / "theme.json"
def load_theme():
    from theme_engine import load_theme as engine_load
    return engine_load()
def theme_color(name, fallback):
    try:
        value = str(load_theme()["resolved"][name]).lstrip("#")
        alpha = (int(value[:2], 16),) if len(value) == 8 else ()
        value = value[2:] if len(value) == 8 else value
        if len(value) != 6: raise ValueError
        return tuple(int(value[i:i + 2], 16) for i in (0,2,4)) + alpha
    except (KeyError, TypeError, ValueError): return fallback
WIDGET_GRADIENT_TOP = theme_color("gradient_top", (72,164,242))
WIDGET_GRADIENT_BOTTOM = theme_color("gradient_bottom", (174,220,250))
