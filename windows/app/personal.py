"""Making the program yours: a profile (picture and name) and your own colors."""
import subprocess
import sys
from pathlib import Path

from PySide6.QtCore import QPointF, QRectF, Qt, QTimer, Signal
from PySide6.QtGui import QBrush, QColor, QConicalGradient, QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QButtonGroup, QCheckBox, QDialog, QFileDialog, QFrame, QHBoxLayout, QLabel, QLineEdit,
    QPushButton, QSlider, QVBoxLayout, QWidget,
)

from . import look as looks
from .settings import data_dir
from .util import resource_path

SCOPES = (("all", "Whole app"), ("music", "Music"), ("movies", "Movies"), ("tv", "TV Shows"))
PICTURE_FILTER = "Pictures (*.png *.jpg *.jpeg *.bmp *.gif *.webp)"


def round_pixmap(path, size):
    """A round picture. Without a path (or if it cannot be read), the plain person shape."""
    pix = QPixmap(path) if path else QPixmap()
    out = QPixmap(size, size)
    out.fill(Qt.GlobalColor.transparent)
    painter = QPainter(out)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
    clip = QPainterPathCircle(size)
    painter.setClipPath(clip)
    if pix.isNull():
        painter.fillRect(0, 0, size, size, looks.C.dark)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QBrush(looks.C.light))
        painter.drawEllipse(QRectF(size * 0.36, size * 0.20, size * 0.28, size * 0.28))
        painter.drawEllipse(QRectF(size * 0.24, size * 0.54, size * 0.52, size * 0.52))
    else:
        side = min(pix.width(), pix.height())
        painter.drawPixmap(0, 0, pix.copy((pix.width() - side) // 2, (pix.height() - side) // 2, side, side)
                           .scaled(size, size, Qt.AspectRatioMode.IgnoreAspectRatio,
                                   Qt.TransformationMode.SmoothTransformation))
    painter.setClipping(False)
    painter.setPen(QPen(looks.C.line, 1.5))
    painter.setBrush(Qt.BrushStyle.NoBrush)
    painter.drawEllipse(QRectF(0.75, 0.75, size - 1.5, size - 1.5))
    painter.end()
    return out


def icon_pixmap(size, key):
    """The default icon: a rounded square in the icon color you chose, with a person shape."""
    from PySide6.QtGui import QPainterPath
    spec = next((c for c in looks.ICON_COLORS if c[0] == key), looks.ICON_COLORS[0])
    out = QPixmap(size, size)
    out.fill(Qt.GlobalColor.transparent)
    painter = QPainter(out)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    square = QPainterPath()
    square.addRoundedRect(QRectF(0, 0, size, size), size * 0.19, size * 0.19)
    painter.setClipPath(square)
    painter.fillRect(0, 0, size, size, QColor(spec[2]))
    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QColor("#141414" if spec[3] else "#ebebeb"))
    painter.drawEllipse(QRectF(size * 0.36, size * 0.20, size * 0.28, size * 0.28))
    painter.drawEllipse(QRectF(size * 0.24, size * 0.54, size * 0.52, size * 0.52))
    painter.end()
    return out


def QPainterPathCircle(size):
    from PySide6.QtGui import QPainterPath
    path = QPainterPath()
    path.addEllipse(QRectF(0, 0, size, size))
    return path


class HueCircle(QWidget):
    """A ring of colors. Click or drag on the ring to pick a hue."""

    hueChanged = Signal(int)

    def __init__(self, hue=0):
        super().__init__()
        self._hue = hue
        self.setFixedSize(200, 200)
        self.setCursor(Qt.CursorShape.PointingHandCursor)

    def set_hue(self, hue):
        self._hue = int(hue) % 360
        self.update()

    def _pick(self, pos):
        dx = pos.x() - self.width() / 2
        dy = pos.y() - self.height() / 2
        import math
        angle = math.degrees(math.atan2(-dy, dx)) % 360
        self._hue = int(angle)
        self.update()
        self.hueChanged.emit(self._hue)

    def mousePressEvent(self, event):
        self._pick(event.position())

    def mouseMoveEvent(self, event):
        if event.buttons() & Qt.MouseButton.LeftButton:
            self._pick(event.position())

    def paintEvent(self, event):
        import math
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        w = self.width()
        ring = 30
        rect = QRectF(ring / 2, ring / 2, w - ring, w - ring)
        gradient = QConicalGradient(QPointF(w / 2, w / 2), 0)
        for step in range(0, 361, 30):
            gradient.setColorAt(step / 360.0, QColor.fromHsvF((step % 360) / 360.0, 1.0, 1.0))
        painter.setPen(QPen(QBrush(gradient), ring))
        painter.setBrush(Qt.BrushStyle.NoBrush)
        painter.drawEllipse(rect)
        # the color being made, in the middle
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor.fromHsvF(self._hue / 360.0, 1.0, 1.0))
        painter.drawEllipse(QRectF(w * 0.30, w * 0.30, w * 0.40, w * 0.40))
        # a marker on the ring
        radius = (w - ring) / 2
        a = math.radians(self._hue)
        center = QPointF(w / 2 + radius * math.cos(a), w / 2 - radius * math.sin(a))
        painter.setBrush(Qt.BrushStyle.NoBrush)
        painter.setPen(QPen(QColor("#000000"), 4))
        painter.drawEllipse(center, 11, 11)
        painter.setPen(QPen(QColor("#ffffff"), 2))
        painter.drawEllipse(center, 11, 11)
        painter.end()


def _chip(text, checkable=True):
    button = QPushButton(text)
    button.setObjectName("chip")
    button.setCheckable(checkable)
    button.setCursor(Qt.CursorShape.PointingHandCursor)
    button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
    return button


def _action(text):
    button = QPushButton(text)
    button.setObjectName("action")
    button.setCursor(Qt.CursorShape.PointingHandCursor)
    button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
    return button


def _slider(low, high):
    slider = QSlider(Qt.Orientation.Horizontal)
    slider.setRange(low, high)
    slider.setFixedWidth(260)
    return slider


def _heading(text):
    label = QLabel(text)
    label.setObjectName("sectionTitle")
    return label


def _note(text):
    label = QLabel(text)
    label.setObjectName("muted")
    label.setWordWrap(True)
    return label


class ColorsPanel(QWidget):
    """Three tones you choose (light, middle, dark), presets, and an optional background picture.
    The whole app has one look, and each of Music, Movies and TV Shows can have its own."""

    lookChanged = Signal()

    def __init__(self, settings):
        super().__init__()
        self.settings = settings
        self._scope = "all"
        self._tone = "light"
        self._loading = False
        self._timer = QTimer(self)
        self._timer.setSingleShot(True)
        self._timer.setInterval(150)
        self._timer.timeout.connect(self.lookChanged.emit)

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(8)
        lay.addWidget(_heading("Colors"))
        lay.addWidget(_note(
            "The program has three colors: a light one for text, a middle one for small words, and a dark one "
            "for the background. Choose any color for each one, or use a picture as the background."))

        row = QHBoxLayout()
        row.setSpacing(8)
        self.scope_group = QButtonGroup(self)
        self.scope_group.setExclusive(True)
        for key, text in SCOPES:
            button = _chip(text)
            button.setChecked(key == "all")
            self.scope_group.addButton(button)
            button.clicked.connect(lambda _=False, k=key: self._set_scope(k))
            row.addWidget(button)
        row.addStretch(1)
        lay.addLayout(row)

        self.own = QCheckBox("Use my own colors here")
        self.own.toggled.connect(self._own_toggled)
        lay.addWidget(self.own)
        self.follow_note = _note("This section uses the whole-app colors.")
        lay.addWidget(self.follow_note)

        self.editor = QWidget()
        ed = QVBoxLayout(self.editor)
        ed.setContentsMargins(0, 0, 0, 0)
        ed.setSpacing(10)

        self.preview = QLabel()
        self.preview.setFixedHeight(90)
        self.preview.setMaximumWidth(520)
        self.preview.setTextFormat(Qt.TextFormat.RichText)
        ed.addWidget(self.preview)

        ed.addWidget(_note("Ready-made"))
        presets = QHBoxLayout()
        presets.setSpacing(8)
        for name, preset in looks.PRESETS:
            button = _chip(name, checkable=False)
            button.clicked.connect(lambda _=False, p=preset: self._apply_preset(p))
            presets.addWidget(button)
        presets.addStretch(1)
        ed.addLayout(presets)

        ed.addWidget(_note("Choose a color to change"))
        tones = QHBoxLayout()
        tones.setSpacing(8)
        self.tone_group = QButtonGroup(self)
        self.tone_group.setExclusive(True)
        self.tone_buttons = {}
        for key, text in (("light", "Light"), ("middle", "Middle"), ("dark", "Dark")):
            button = _chip(text)
            button.setChecked(key == "light")
            self.tone_group.addButton(button)
            button.clicked.connect(lambda _=False, k=key: self._set_tone(k))
            self.tone_buttons[key] = button
            tones.addWidget(button)
        tones.addStretch(1)
        ed.addLayout(tones)

        wheel_row = QHBoxLayout()
        self.wheel = HueCircle()
        self.wheel.hueChanged.connect(self._hue_changed)
        wheel_row.addWidget(self.wheel)
        wheel_row.addSpacing(20)
        sliders = QVBoxLayout()
        sliders.setSpacing(6)
        self.sat = _slider(0, 100)
        self.bright = _slider(0, 100)
        self.sat.valueChanged.connect(self._sliders_changed)
        self.bright.valueChanged.connect(self._sliders_changed)
        sliders.addStretch(1)
        sliders.addWidget(QLabel("Saturation"))
        sliders.addWidget(self.sat)
        sliders.addSpacing(8)
        sliders.addWidget(QLabel("Brightness"))
        sliders.addWidget(self.bright)
        sliders.addStretch(1)
        wheel_row.addLayout(sliders)
        wheel_row.addStretch(1)
        ed.addLayout(wheel_row)

        ed.addWidget(_note("Background picture (optional): a picture of your choice can replace the dark color."))
        pic_row = QHBoxLayout()
        pic_row.setSpacing(8)
        self.choose_picture = _action("Choose picture…")
        self.choose_picture.clicked.connect(self._choose_picture)
        self.remove_picture = _action("Remove picture")
        self.remove_picture.clicked.connect(self._remove_picture)
        pic_row.addWidget(self.choose_picture)
        pic_row.addWidget(self.remove_picture)
        pic_row.addStretch(1)
        ed.addLayout(pic_row)
        self.dim_label = QLabel("Dim picture")
        self.dim = _slider(0, 100)
        self.dim.valueChanged.connect(self._dim_changed)
        ed.addWidget(self.dim_label)
        ed.addWidget(self.dim)

        self.reset = _action("Back to black, white and gray")
        self.reset.clicked.connect(self._reset)
        reset_row = QHBoxLayout()
        reset_row.addWidget(self.reset)
        reset_row.addStretch(1)
        ed.addLayout(reset_row)

        lay.addWidget(self.editor)
        self._refresh()

    # ---- the look being edited ---------------------------------------------

    def _key(self):
        return f"look_{self._scope}"

    def _has_own(self):
        return self._scope == "all" or bool(self.settings.get(self._key()))

    def _look(self):
        return looks.normalize(self.settings.get(self._key()))

    def _save(self, look):
        self.settings.set(self._key(), look)
        self._refresh_preview(look)
        self._timer.start()

    # ---- showing it --------------------------------------------------------

    def _refresh(self):
        self._loading = True
        is_all = self._scope == "all"
        self.own.setVisible(not is_all)
        has_own = self._has_own()
        self.own.setChecked(has_own)
        self.follow_note.setVisible(not is_all and not has_own)
        self.editor.setVisible(has_own)
        if has_own:
            look = self._look()
            h, s, v = look[self._tone]
            self.wheel.set_hue(h)
            self.sat.setValue(s)
            self.bright.setValue(v)
            self.dim.setValue(look["dim"])
            has_picture = bool(look["picture"])
            self.remove_picture.setEnabled(has_picture)
            self.dim.setVisible(has_picture)
            self.dim_label.setVisible(has_picture)
            self.choose_picture.setText("Change picture…" if has_picture else "Choose picture…")
            self._refresh_preview(look)
        self._loading = False

    def _refresh_preview(self, look):
        light = looks.tone_color(look["light"]).name()
        middle = looks.tone_color(look["middle"]).name()
        dark = looks.tone_color(look["dark"]).name()
        swatch = lambda c: (f'<span style="background:{c}; border:1px solid {light};">'
                            f'&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>&nbsp;')
        self.preview.setText(
            f'<div style="font-size:17pt; font-weight:bold; color:{light};">Your look</div>'
            f'<div style="color:{middle};">Songs, movies and shows</div>'
            f'<div>{swatch(light)}{swatch(middle)}{swatch(dark)}</div>')
        self.preview.setStyleSheet(f"background: {dark}; padding: 12px; border: 1px solid {middle};")

    # ---- what you do -------------------------------------------------------

    def _set_scope(self, scope):
        self._scope = scope
        self._refresh()

    def _set_tone(self, tone):
        self._tone = tone
        self._refresh()

    def _own_toggled(self, on):
        if self._loading or self._scope == "all":
            return
        if on:
            self.settings.set(self._key(), looks.normalize(self.settings.get("look_all")))
        else:
            old = self._look()["picture"]
            self.settings.set(self._key(), {})
            looks.forget_picture(old)
        self._refresh()
        self._timer.start()

    def _hue_changed(self, hue):
        if self._loading:
            return
        look = self._look()
        look[self._tone][0] = hue
        self._save(look)

    def _sliders_changed(self, _=0):
        if self._loading:
            return
        look = self._look()
        look[self._tone][1] = self.sat.value()
        look[self._tone][2] = self.bright.value()
        self._save(look)

    def _dim_changed(self, value):
        if self._loading:
            return
        look = self._look()
        look["dim"] = value
        self._save(look)

    def _apply_preset(self, preset):
        look = looks.normalize(preset)
        look["picture"] = self._look()["picture"]
        look["dim"] = self._look()["dim"]
        self._save(look)
        self._refresh()

    def _choose_picture(self):
        path, _ = QFileDialog.getOpenFileName(self, "Choose a background picture", "", PICTURE_FILTER)
        if not path:
            return
        kept = looks.keep_picture(path, data_dir() / "pictures", f"background_{self._scope}", 1920)
        if not kept:
            return
        look = self._look()
        looks.forget_picture(look["picture"])
        look["picture"] = kept
        self._save(look)
        self._refresh()

    def _remove_picture(self):
        look = self._look()
        looks.forget_picture(look["picture"])
        look["picture"] = ""
        self._save(look)
        self._refresh()

    def _reset(self):
        old = self._look()["picture"]
        looks.forget_picture(old)
        if self._scope == "all":
            self.settings.set("look_all", {})
        else:
            self.settings.set(self._key(), {})
        self._refresh()
        self._timer.start()


class ProfilePanel(QWidget):
    """Your picture and name. They stay on this computer. Also makes a shortcut that uses your picture."""

    profileChanged = Signal()

    def __init__(self, settings):
        super().__init__()
        self.settings = settings
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(8)
        lay.addWidget(_heading("Profile"))
        lay.addWidget(_note("Your picture and name stay on this computer. They make the program feel like yours."))

        row = QHBoxLayout()
        row.setSpacing(16)
        self.avatar = QLabel()
        self.avatar.setFixedSize(96, 96)
        row.addWidget(self.avatar)
        buttons = QVBoxLayout()
        buttons.setSpacing(8)
        self.choose = _action("Choose picture…")
        self.choose.clicked.connect(self._choose)
        self.remove = _action("Remove picture")
        self.remove.clicked.connect(self._remove)
        buttons.addWidget(self.choose)
        buttons.addWidget(self.remove)
        row.addLayout(buttons)
        row.addStretch(1)
        lay.addLayout(row)

        self.name = QLineEdit()
        self.name.setPlaceholderText("Your name (optional)")
        self.name.setMaxLength(30)
        self.name.setMaximumWidth(320)
        self.name.setText(settings.get("profile_name"))
        self.name.editingFinished.connect(self._name_done)
        lay.addWidget(self.name)

        lay.addWidget(_note("Icon color: used for the window, taskbar and shortcut icon when you have no picture."))
        dots = QHBoxLayout()
        dots.setSpacing(6)
        self.dot_group = QButtonGroup(self)
        self.dot_group.setExclusive(True)
        for key, label, hexv, _dark in looks.ICON_COLORS:
            dot = QPushButton()
            dot.setCheckable(True)
            dot.setFixedSize(26, 26)
            dot.setToolTip(label)
            dot.setCursor(Qt.CursorShape.PointingHandCursor)
            dot.setFocusPolicy(Qt.FocusPolicy.NoFocus)
            dot.setStyleSheet(
                f"QPushButton {{ background: {hexv}; border: 1px solid #777777; border-radius: 13px; }}"
                f"QPushButton:checked {{ border: 3px solid #ffffff; }}")
            dot.setChecked(settings.get("icon_color") == key)
            self.dot_group.addButton(dot)
            dot.clicked.connect(lambda _=False, k=key: self._pick_icon_color(k))
            dots.addWidget(dot)
        dots.addStretch(1)
        lay.addLayout(dots)

        self.shortcut = _action("Make a shortcut")
        self.shortcut.clicked.connect(self._make_shortcut)
        shortcut_row = QHBoxLayout()
        shortcut_row.addWidget(self.shortcut)
        shortcut_row.addStretch(1)
        lay.addLayout(shortcut_row)
        self.message = _note("")
        lay.addWidget(self.message)
        self.refresh()

    def refresh(self):
        path = self.settings.get("profile_picture")
        has_picture = bool(path and Path(path).is_file())
        self.avatar.setPixmap(round_pixmap(path, 96) if has_picture else icon_pixmap(96, self.settings.get("icon_color")))
        self.remove.setEnabled(bool(path))
        self.choose.setText("Change picture…" if path else "Choose picture…")

    def _choose(self):
        path, _ = QFileDialog.getOpenFileName(self, "Choose your picture", "", PICTURE_FILTER)
        if not path:
            return
        kept = looks.keep_picture(path, data_dir() / "pictures", "profile", 512)
        if kept:
            looks.forget_picture(self.settings.get("profile_picture"))
            self.settings.set("profile_picture", kept)
            self.refresh()
            self.profileChanged.emit()

    def _remove(self):
        looks.forget_picture(self.settings.get("profile_picture"))
        self.settings.set("profile_picture", "")
        self.refresh()
        self.profileChanged.emit()

    def _pick_icon_color(self, key):
        self.settings.set("icon_color", key)
        self.refresh()
        self.profileChanged.emit()

    def _name_done(self):
        self.settings.set("profile_name", self.name.text().strip())
        self.profileChanged.emit()

    def _make_shortcut(self):
        self.message.setText(make_shortcut(self.settings.get("profile_picture"), self.settings.get("icon_color")))


def make_shortcut(picture, icon_color="black"):
    """Puts a shortcut named You on the Desktop and in the Start menu, with your picture as its icon."""
    if sys.platform != "win32":
        return "Shortcuts can only be made on Windows."
    if not getattr(sys, "frozen", False):
        return "Shortcuts work from the installed You.exe, not from the source code."
    if picture and Path(picture).is_file():
        source = picture
    else:
        source = str(data_dir() / "icon-color.png")
        icon_pixmap(256, icon_color).save(source, "PNG")
    ico = data_dir() / "shortcut-icon.ico"
    if not looks.write_ico(source, ico):
        return "That picture could not be read."
    script = (
        "$s=New-Object -ComObject WScript.Shell;"
        "foreach($p in @((Join-Path ([Environment]::GetFolderPath('Desktop')) 'You.lnk'),"
        "(Join-Path $env:APPDATA 'Microsoft\\Windows\\Start Menu\\Programs\\You.lnk'))){"
        "$l=$s.CreateShortcut($p);"
        f"$l.TargetPath='{sys.executable}';"
        f"$l.WorkingDirectory='{Path(sys.executable).parent}';"
        f"$l.IconLocation='{ico}';"
        "$l.Save()}"
    )
    try:
        subprocess.run(["powershell", "-NoProfile", "-Command", script], check=True, timeout=30,
                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    except Exception:
        return "The shortcut could not be made."
    return "Done. “You” is now on your Desktop and in the Start menu, with your picture."


class WelcomeDialog(QDialog):
    """Shown the first time: pick a picture and a name, optionally a look. Everything can change later."""

    def __init__(self, settings, parent=None):
        super().__init__(parent)
        self.settings = settings
        self.setWindowTitle("Welcome to You")
        self.setModal(True)
        self.setMinimumWidth(460)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(36, 30, 36, 30)
        lay.setSpacing(12)
        title = QLabel("Welcome to You")
        title.setObjectName("pageTitle")
        title.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        lay.addWidget(title)
        lay.addWidget(_note("Make it yours. You can change all of this later in Settings."))
        self.profile = ProfilePanel(settings)
        self.profile.shortcut.hide()
        for index in range(self.profile.layout().count()):
            item = self.profile.layout().itemAt(index).widget()
            if isinstance(item, QLabel) and item.objectName() == "sectionTitle":
                item.hide()
        lay.addWidget(self.profile)
        lay.addWidget(_note("Pick a look"))
        row = QHBoxLayout()
        row.setSpacing(8)
        for name, preset in looks.PRESETS:
            button = _chip(name, checkable=False)
            button.clicked.connect(lambda _=False, p=preset: settings.set("look_all", {} if p is looks.DEFAULT else looks.normalize(p)))
            row.addWidget(button)
        row.addStretch(1)
        lay.addLayout(row)
        start = _action("Start")
        start.clicked.connect(self.accept)
        lay.addSpacing(8)
        lay.addWidget(start)
