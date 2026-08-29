"""Persistent state used only to distinguish user-requested dialogue refreshes."""
import json
from pathlib import Path

STATE_FILE = Path.home() / "anime-widget" / "cache" / "refresh_state.json"


def _read():
    try:
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except Exception:
        return {}


def get_dialogue_sequence():
    try:
        return int(_read().get("dialogue_sequence", 0))
    except Exception:
        return 0


def advance_dialogue_sequence():
    state = _read()
    state["dialogue_sequence"] = get_dialogue_sequence() + 1
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
    return state["dialogue_sequence"]
