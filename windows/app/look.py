"""Your colors. Three tones (light, middle, dark) that you choose, and an optional background picture.

A "look" is a small dictionary, saved in settings.json on this computer only:
    {"light": [hue, saturation, brightness], "middle": [...], "dark": [...], "picture": "path", "dim": 70}
hue is 0-359, the others are 0-100.
"""
import shutil
import time
from pathlib import Path

from PySide6.QtGui import QColor

DEFAULT = {
    "light": [0, 0, 100],
    "middle": [0, 0, 54],
    "dark": [0, 0, 0],
    "picture": "",
    "dim": 70,
}

PRESETS = [
    ("Classic", DEFAULT),
    ("Ocean", {"light": [200, 10, 100], "middle": [195, 55, 80], "dark": [215, 80, 14], "picture": "", "dim": 70}),
    ("Sunset", {"light": [35, 12, 100], "middle": [25, 85, 95], "dark": [345, 75, 18], "picture": "", "dim": 70}),
    ("Forest", {"light": [95, 10, 100], "middle": [120, 45, 65], "dark": [140, 75, 10], "picture": "", "dim": 70}),
    ("Berry", {"light": [320, 8, 100], "middle": [330, 55, 85], "dark": [285, 80, 15], "picture": "", "dim": 70}),
]


def normalize(raw):
    """Fills in anything missing, so a half-empty or damaged saved look still works."""
    out = {k: list(v) if isinstance(v, list) else v for k, v in DEFAULT.items()}
    if isinstance(raw, dict):
        for key in ("light", "middle", "dark"):
            value = raw.get(key)
            if isinstance(value, list) and len(value) == 3 and all(isinstance(x, (int, float)) for x in value):
                out[key] = [int(value[0]) % 360, max(0, min(100, int(value[1]))), max(0, min(100, int(value[2])))]
        if isinstance(raw.get("picture"), str):
            out["picture"] = raw["picture"]
        if isinstance(raw.get("dim"), (int, float)):
            out["dim"] = max(0, min(100, int(raw["dim"])))
    return out


def tone_color(tone):
    h, s, v = tone
    return QColor.fromHsvF((h % 360) / 360.0, s / 100.0, v / 100.0)


def mix(a, b, amount):
    """A color part of the way from a to b (amount 0 = a, 1 = b)."""
    return QColor(
        round(a.red() + (b.red() - a.red()) * amount),
        round(a.green() + (b.green() - a.green()) * amount),
        round(a.blue() + (b.blue() - a.blue()) * amount),
    )


def rgba(color, alpha):
    """A style-sheet color with see-through amount alpha (0 = invisible, 1 = solid)."""
    return f"rgba({color.red()},{color.green()},{color.blue()},{round(max(0.0, min(1.0, alpha)) * 255)})"


def has_picture(look):
    return bool(look["picture"]) and Path(look["picture"]).is_file()


class Colors:
    """Every color the program uses, worked out from one look. Painted widgets read these when they draw."""

    def __init__(self, look=None):
        self.set(look)

    def set(self, look):
        look = normalize(look)
        self.look = look
        self.picture = look["picture"] if has_picture(look) else ""
        self.light = tone_color(look["light"])
        self.middle = tone_color(look["middle"])
        self.dark = tone_color(look["dark"])
        self.soft = mix(self.light, self.middle, 0.25)
        self.line = mix(self.dark, self.middle, 0.38)
        self.faint = mix(self.dark, self.middle, 0.20)
        self.disabled = mix(self.dark, self.middle, 0.50)
        self.panel = mix(self.dark, self.light, 0.10)
        self.panel_low = mix(self.dark, self.light, 0.05)
        self.panel_lower = mix(self.dark, self.light, 0.035)
        self.hover = mix(self.dark, self.light, 0.22)
        self.placeholder = mix(self.dark, self.light, 0.11)
        self.dim_amount = 0.30 + 0.65 * look["dim"] / 100.0


C = Colors()          # the look being shown right now


def apply(look):
    C.set(look)


# ---- saving pictures -------------------------------------------------------

def keep_picture(source, folder, prefix, max_side):
    """Copies a picture you chose into the program's own folder (shrunk), and returns the new path."""
    from PySide6.QtCore import Qt
    from PySide6.QtGui import QImage

    image = QImage(str(source))
    if image.isNull():
        return ""
    if max(image.width(), image.height()) > max_side:
        image = image.scaled(max_side, max_side, Qt.AspectRatioMode.KeepAspectRatio,
                             Qt.TransformationMode.SmoothTransformation)
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=True)
    out = folder / f"{prefix}_{int(time.time() * 1000)}.png"
    return str(out) if image.save(str(out), "PNG") else ""


def forget_picture(path):
    try:
        if path:
            Path(path).unlink()
    except OSError:
        pass


def write_ico(source, ico_path):
    """Makes a Windows .ico (one 256 px picture inside) from any picture. Used for shortcuts."""
    from PySide6.QtCore import QBuffer, QByteArray, QIODevice, Qt
    from PySide6.QtGui import QImage

    image = QImage(str(source))
    if image.isNull():
        return False
    side = min(image.width(), image.height())
    image = image.copy((image.width() - side) // 2, (image.height() - side) // 2, side, side)
    image = image.scaled(256, 256, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.SmoothTransformation)
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    image.save(buffer, "PNG")
    png = bytes(data)
    header = (b"\x00\x00\x01\x00\x01\x00" + bytes([0, 0, 0, 0]) + (1).to_bytes(2, "little")
              + (32).to_bytes(2, "little") + len(png).to_bytes(4, "little") + (22).to_bytes(4, "little"))
    Path(ico_path).write_bytes(header + png)
    return True
