# Render-only refactor

This pass intentionally changes no palette/theme values and adds no Android/Kotlin behavior.

## New module boundaries

- `render.py`: composition only — decides draw order and widget sections.
- `widget_config.py`: paths, fonts, layout loading, weather-name mapping, cached-weather fallback.
- `widget_ui.py`: reusable Pillow drawing primitives (`rounded`, `centered_text`, `wrap_text`).

The same Python files are mirrored in both `01_source_original/anime-widget/` and the final Android project's `app/src/main/python/`.

## Deliberately unchanged

- Hard-coded colors and dimensions
- Weather/background visual rules
- Character compositing
- Dialogue selection
- Android/Kotlin widget behavior
- `layout.json`

This creates a clean seam for a later `theme.py` without mixing palette extraction into the composition layer.
