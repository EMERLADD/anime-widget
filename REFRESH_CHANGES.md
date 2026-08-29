# Refresh changes

- Pillow still draws the visible ↻ icon.
- The Android overlay is now a real transparent `ImageButton` (`refresh_button`) rather than a transparent `TextView`.
- Scheduled 60-second ticks use `ACTION_RENDER` and only re-render the PNG (no weather network request).
- Tapping ↻ uses `ACTION_MANUAL_REFRESH`, refreshes location/weather, then renders.
- Initial widget placement/update performs a weather refresh so a fresh install can populate data.
- `PythonBridge.renderWidgetPng` is render-only; `refreshAndRenderWidgetPng` is the manual full refresh path.
