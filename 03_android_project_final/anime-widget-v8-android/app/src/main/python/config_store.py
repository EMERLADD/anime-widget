"""Versioned, atomic JSON configuration storage."""
import json
from pathlib import Path
BASE = Path.home() / "anime-widget"
CONFIG_DIR = BASE / "config"


def read_json(path, default):
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else default
    except Exception:
        return default


def atomic_write_json(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    backup = path.with_suffix(path.suffix + ".backup")
    text = json.dumps(data, ensure_ascii=False, indent=2)
    temp.write_text(text, encoding="utf-8")
    if path.exists():
        try:
            backup.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")
        except Exception:
            pass
    temp.replace(path)
