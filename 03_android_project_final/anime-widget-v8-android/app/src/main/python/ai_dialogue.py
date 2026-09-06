"""Read the optional one-turn AI answer without changing normal dialogue files."""
import json
from pathlib import Path

STATE_FILE = Path.home() / "anime-widget" / "data" / "ai_dialogue.json"


def active_answer():
    try:
        value = json.loads(STATE_FILE.read_text(encoding="utf-8"))
        if not isinstance(value, dict) or not value.get("active"):
            return None
        answer = str(value.get("answer", "")).strip()
        return answer or None
    except Exception:
        return None
