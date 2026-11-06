import json
import os
from pathlib import Path

SETTINGS_VERSION = 2     # 2: covers are bigger, the default gap is half an inch

DEFAULTS = {
    "settings_version": SETTINGS_VERSION,
    "grid_inches": 0.5,
    "ignore_articles": True,
    "volume": 80,
    "muted": False,
    "shuffle": False,
    "repeat": "off",
    "theme_music": "browse",
    "theme_movies": "browse",
    "theme_tv": "browse",
    "look_all": {},
    "look_music": {},
    "look_movies": {},
    "look_tv": {},
    "profile_name": "",
    "profile_picture": "",
    "welcomed": False,
    "music_folders": [],
    "movie_folders": [],
    "tv_folders": [],
}


def data_dir() -> Path:
    """Where the app keeps its own files. Local only, nothing is ever sent anywhere."""
    base = os.environ.get("APPDATA") or str(Path.home())
    path = Path(base) / "You"
    path.mkdir(parents=True, exist_ok=True)
    return path


class Settings:
    def __init__(self):
        self.path = data_dir() / "settings.json"
        self.values = dict(DEFAULTS)
        self._load()

    def _load(self):
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return
        if isinstance(data, dict):
            if data.get("settings_version") != SETTINGS_VERSION:
                data.pop("grid_inches", None)      # older saved gap was 1 inch: use the new default
            for key in DEFAULTS:
                if key in data and type(data[key]) is type(DEFAULTS[key]):
                    self.values[key] = data[key]

    def _save(self):
        try:
            self.path.write_text(json.dumps(self.values, indent=2), encoding="utf-8")
        except OSError:
            pass

    def get(self, key):
        return self.values[key]

    def set(self, key, value):
        self.values[key] = value
        self._save()
