"""Saves cover pictures into the local cover folder, shrunk to a sensible size."""
import hashlib

from PySide6.QtCore import Qt
from PySide6.QtGui import QImage

from .settings import data_dir

COVER_SIZE = 512


def covers_dir():
    path = data_dir() / "covers"
    path.mkdir(parents=True, exist_ok=True)
    return path


def save_cover(data):
    """Shrink to 512 px and keep one copy in the local cover folder. Returns the file path."""
    if not data:
        return None
    data = bytes(data)
    target = covers_dir() / (hashlib.sha1(data).hexdigest() + ".jpg")
    if target.exists():
        return str(target)
    image = QImage.fromData(data)
    if image.isNull():
        return None
    if max(image.width(), image.height()) > COVER_SIZE:
        image = image.scaled(
            COVER_SIZE, COVER_SIZE,
            Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation,
        )
    image = image.convertToFormat(QImage.Format.Format_RGB32)
    return str(target) if image.save(str(target), "JPG", 90) else None
