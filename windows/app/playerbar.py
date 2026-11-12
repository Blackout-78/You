import math

from PySide6.QtCore import QPointF, QRectF, QSize, Qt, QVariantAnimation, Signal
from PySide6.QtGui import QColor, QPainter, QPen, QPixmap, QPolygonF
from PySide6.QtGui import QCursor
from PySide6.QtWidgets import (
    QAbstractButton, QHBoxLayout, QLabel, QMenu, QSlider, QStyle, QVBoxLayout, QWidget,
)

from .look import C


def format_time(ms):
    seconds = max(0, int(ms // 1000))
    return f"{seconds // 60}:{seconds % 60:02d}"


class SeekSlider(QSlider):
    """Thin progress bar. Click or drag anywhere on it to jump."""

    seekRequested = Signal(int)

    def __init__(self):
        super().__init__(Qt.Orientation.Horizontal)
        self.dragging = False
        self.setRange(0, 0)
        self.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.setCursor(Qt.CursorShape.PointingHandCursor)

    def _value_at(self, x):
        return QStyle.sliderValueFromPosition(
            self.minimum(), self.maximum(), int(x), max(1, self.width())
        )

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and self.isEnabled():
            self.dragging = True
            self.setValue(self._value_at(event.position().x()))

    def mouseMoveEvent(self, event):
        if self.dragging:
            self.setValue(self._value_at(event.position().x()))

    def mouseReleaseEvent(self, event):
        if self.dragging:
            self.dragging = False
            self.seekRequested.emit(self.value())


def _line(painter, x1, y1, x2, y2):
    """A straight line drawn as a two-point polygon (uses only the drawing call the other icons use)."""
    painter.drawPolygon(QPolygonF([QPointF(x1, y1), QPointF(x2, y2)]))


def _arc_arrow(painter, cx, cy, r, a_start, a_end):
    """An open circle drawn from a_start to a_end (degrees) with an arrowhead at the end."""
    steps = 28
    points = []
    for i in range(steps + 1):
        angle = math.radians(a_start + (a_end - a_start) * i / steps)
        points.append((cx + r * math.cos(angle), cy - r * math.sin(angle)))
    for (x1, y1), (x2, y2) in zip(points, points[1:]):
        _line(painter, x1, y1, x2, y2)
    (tx, ty), (bx, by) = points[-1], points[-2]
    dx, dy = tx - bx, ty - by
    length = math.hypot(dx, dy) or 1.0
    dx, dy = dx / length, dy / length
    for sign in (1, -1):
        turn = math.radians(150 * sign)
        hx = dx * math.cos(turn) - dy * math.sin(turn)
        hy = dx * math.sin(turn) + dy * math.cos(turn)
        _line(painter, tx, ty, tx + hx * 5.5, ty + hy * 5.5)


def _ten(painter):
    """The number 10, drawn with lines so no text or font is needed."""
    _line(painter, 17.5, 20, 19.5, 18.5)      # the flag of the 1
    _line(painter, 19.5, 18.5, 19.5, 26.5)    # the stem of the 1
    painter.drawPolygon(QPolygonF([QPointF(x, y) for x, y in
                                   ((22.5, 19), (26.5, 19), (26.5, 26), (22.5, 26))]))   # the 0


def _lerp(a, b, t):
    return a + (b - a) * t


class PlayerButton(QAbstractButton):
    """A round outline button with a thin outline icon. No fills."""

    def __init__(self, kind, tooltip):
        super().__init__()
        self.kind = kind
        self._active = False
        self.setFixedSize(44, 44)
        self.setToolTip(tooltip)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFocusPolicy(Qt.FocusPolicy.NoFocus)

    def sizeHint(self):
        return QSize(44, 44)

    def set_active(self, active):
        self._active = active
        self.update()

    def enterEvent(self, event):
        super().enterEvent(event)
        self.update()

    def leaveEvent(self, event):
        super().leaveEvent(event)
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        enabled = self.isEnabled()
        if not enabled:
            color, width = C.disabled, 1.5
        elif self._active:
            color, width = C.light, 2.2
        elif self.underMouse():
            color, width = C.light, 1.5
        else:
            color, width = C.soft, 1.5
        pen = QPen(QColor(color), width)
        pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
        pen.setCapStyle(Qt.PenCapStyle.RoundCap)
        painter.setPen(pen)
        painter.setBrush(Qt.BrushStyle.NoBrush)
        painter.drawEllipse(QRectF(1.5, 1.5, 41, 41))

        def poly(*points):
            painter.drawPolygon(QPolygonF([QPointF(x, y) for x, y in points]))

        if self.kind in ("playpause", "volume", "repeat"):
            self.paint_icon(painter)
        elif self.kind == "play":
            poly((18, 14), (18, 30), (31, 22))
        elif self.kind == "pause":
            painter.drawRect(QRectF(15, 15, 5, 14))
            painter.drawRect(QRectF(24, 15, 5, 14))
        elif self.kind == "stop":
            painter.drawRect(QRectF(15, 15, 14, 14))
        elif self.kind == "back":
            painter.drawLine(14, 15, 14, 29)
            poly((30, 15), (30, 29), (19, 22))
        elif self.kind == "forward":
            painter.drawLine(30, 15, 30, 29)
            poly((14, 15), (14, 29), (25, 22))
        elif self.kind == "heart":
            outline = []
            for i in range(0, 361, 10):
                t = math.radians(i)
                x = 16 * math.sin(t) ** 3
                y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
                outline.append(QPointF(22 + x * 0.62, 21.5 - y * 0.62))
            if self._active:
                painter.setBrush(QColor(color))
            painter.drawPolygon(QPolygonF(outline))
        elif self.kind in ("skip_back", "skip_forward"):
            if self.kind == "skip_back":
                _arc_arrow(painter, 22, 22.5, 11, 125, 415)       # counter-clockwise
            else:
                _arc_arrow(painter, 22, 22.5, 11, 55, -235)       # clockwise
            _ten(painter)
        elif self.kind == "shuffle":
            for (x1, y1), (x2, y2) in zip(((12, 29), (19, 29), (25, 15)), ((19, 29), (25, 15), (31, 15))):
                _line(painter, x1, y1, x2, y2)
            for (x1, y1), (x2, y2) in zip(((12, 15), (19, 15), (25, 29)), ((19, 15), (25, 29), (31, 29))):
                _line(painter, x1, y1, x2, y2)
            for tip_y, sign in ((15, 1), (29, 1)):
                _line(painter, 32, tip_y, 28.5, tip_y - 2.8)
                _line(painter, 32, tip_y, 28.5, tip_y + 2.8)
        elif self.kind == "lyrics":
            painter.drawLine(13, 15, 31, 15)
            painter.drawLine(13, 22, 31, 22)
            painter.drawLine(13, 29, 24, 29)
        elif self.kind == "timer":
            painter.drawEllipse(QRectF(14, 15, 16, 16))
            painter.drawLine(22, 23, 22, 19)
            painter.drawLine(22, 23, 25, 25)
            painter.drawLine(19, 12, 25, 12)


# play triangle and pause bars, both drawn as two four-point shapes so one can melt into the other
_PLAY = [
    [(18, 14), (18, 30), (24.5, 26.5), (24.5, 17.5)],
    [(24.5, 17.5), (24.5, 26.5), (31, 22), (31, 22)],
]
_PAUSE = [
    [(15, 15), (15, 29), (20, 29), (20, 15)],
    [(24, 15), (24, 29), (29, 29), (29, 15)],
]


class PlayPauseButton(PlayerButton):
    """One button: shows pause while playing and play otherwise, with a short morph between them."""

    def __init__(self):
        super().__init__("playpause", "Play")
        self._t = 0.0                       # 0 = play shape, 1 = pause shape
        self._anim = QVariantAnimation(self)
        self._anim.setDuration(200)
        self._anim.valueChanged.connect(self._on_anim)

    def _on_anim(self, value):
        self._t = float(value)
        self.update()

    def show_playing(self, playing):
        target = 1.0 if playing else 0.0
        self.setToolTip("Pause" if playing else "Play")
        self.set_active(playing)
        if abs(self._t - target) < 0.001:
            return
        self._anim.stop()
        self._anim.setStartValue(self._t)
        self._anim.setEndValue(target)
        self._anim.start()

    def paint_icon(self, painter):
        t = self._t
        for play_shape, pause_shape in zip(_PLAY, _PAUSE):
            points = [
                QPointF(_lerp(a[0], b[0], t), _lerp(a[1], b[1], t))
                for a, b in zip(play_shape, pause_shape)
            ]
            painter.drawPolygon(QPolygonF(points))


class MuteButton(PlayerButton):
    """A speaker outline. Sound waves when on, a cross when muted."""

    def __init__(self):
        super().__init__("volume", "Mute")
        self._muted = False

    def set_muted(self, muted):
        self._muted = bool(muted)
        self.setToolTip("Unmute" if self._muted else "Mute")
        self.update()

    def paint_icon(self, painter):
        painter.drawPolygon(QPolygonF([QPointF(x, y) for x, y in
                                       ((12, 19), (16.5, 19), (22, 14), (22, 30), (16.5, 25), (12, 25))]))
        if self._muted:
            _line(painter, 26, 18.5, 32, 25.5)
            _line(painter, 32, 18.5, 26, 25.5)
        else:
            for radius in (4.5, 8.5):
                points = [(24 + radius * math.cos(math.radians(a)), 22 - radius * math.sin(math.radians(a)))
                          for a in range(-45, 46, 15)]
                for (x1, y1), (x2, y2) in zip(points, points[1:]):
                    _line(painter, x1, y1, x2, y2)


class RepeatButton(PlayerButton):
    """Repeat: off, all (loop symbol lit) or one (loop symbol lit with a 1)."""

    def __init__(self):
        super().__init__("repeat", "Repeat: off")
        self._one = False

    def set_mode(self, mode):
        self._one = mode == "one"
        self.set_active(mode != "off")
        self.setToolTip({"off": "Repeat: off", "all": "Repeat: all", "one": "Repeat: this track"}[mode])

    def paint_icon(self, painter):
        _line(painter, 14, 22, 14, 17)
        _line(painter, 14, 17, 30, 17)
        _line(painter, 30, 17, 26, 14.5)
        _line(painter, 30, 17, 26, 19.5)
        _line(painter, 30, 22, 30, 27)
        _line(painter, 30, 27, 14, 27)
        _line(painter, 14, 27, 18, 24.5)
        _line(painter, 14, 27, 18, 29.5)
        if self._one:
            _line(painter, 20.5, 21.5, 22.5, 19.5)
            _line(painter, 22.5, 19.5, 22.5, 25)


class VolumeSlider(SeekSlider):
    """Same thin bar as the progress bar, 0 to 100."""

    def __init__(self):
        super().__init__()
        self.setRange(0, 100)
        self.setFixedWidth(90)


class ClickLabel(QLabel):
    """A label that can be clicked."""

    clicked = Signal()

    def __init__(self, text=""):
        super().__init__(text)
        self.setCursor(Qt.CursorShape.PointingHandCursor)

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and self.rect().contains(event.position().toPoint()):
            self.clicked.emit()
        super().mouseReleaseEvent(event)


COVER_SIZE = 52


class PlayerBar(QWidget):
    nowPlayingClicked = Signal()      # the cover or the title was clicked: open album + lyrics
    shuffleClicked = Signal()
    repeatClicked = Signal()
    volumeChanged = Signal(int)
    muteClicked = Signal()
    toggleClicked = Signal()
    stopClicked = Signal()
    skipRequested = Signal(int)       # milliseconds, negative = back
    heartClicked = Signal()
    nextClicked = Signal()
    previousClicked = Signal()
    lyricsClicked = Signal()
    seekRequested = Signal(int)
    sleepTimerChosen = Signal(int)    # minutes; 0 = off, -1 = end of this song

    def __init__(self):
        super().__init__()
        self.setFixedHeight(148)
        self._has_track = False
        self._lyrics_available = False
        self._lyrics_open = False
        self._favorite = False
        self._timer_on = False

        outer = QVBoxLayout(self)
        outer.setContentsMargins(32, 14, 32, 14)
        outer.setSpacing(8)

        top = QHBoxLayout()
        top.setSpacing(12)
        self.cover = ClickLabel()
        self.cover.setFixedSize(COVER_SIZE, COVER_SIZE)
        self.cover.setToolTip("Open album and lyrics")
        self.cover.clicked.connect(self.nowPlayingClicked.emit)
        self.cover.hide()
        top.addWidget(self.cover)
        self.title = ClickLabel("Nothing playing")
        self.title.setObjectName("trackTitle")
        self.title.clicked.connect(self.nowPlayingClicked.emit)
        self.subtitle = QLabel("")
        self.subtitle.setObjectName("muted")
        top.addWidget(self.title)
        top.addStretch(1)
        top.addWidget(self.subtitle)
        outer.addLayout(top)

        middle = QHBoxLayout()
        middle.setSpacing(12)
        self.elapsed = QLabel("0:00")
        self.elapsed.setObjectName("muted")
        self.elapsed.setFixedWidth(44)
        self.total = QLabel("0:00")
        self.total.setObjectName("muted")
        self.total.setFixedWidth(44)
        self.total.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
        self.slider = SeekSlider()
        self.slider.seekRequested.connect(self.seekRequested.emit)
        middle.addWidget(self.elapsed)
        middle.addWidget(self.slider, 1)
        middle.addWidget(self.total)
        outer.addLayout(middle)

        buttons = QHBoxLayout()
        buttons.setSpacing(14)
        self.heart_button = PlayerButton("heart", "Add to favorites")
        self.prev_button = PlayerButton("back", "Previous track")
        self.skip_back_button = PlayerButton("skip_back", "Back 10 seconds")
        self.play_button = PlayPauseButton()
        self.stop_button = PlayerButton("stop", "Stop")
        self.skip_forward_button = PlayerButton("skip_forward", "Forward 10 seconds")
        self.next_button = PlayerButton("forward", "Next track")
        self.lyrics_button = PlayerButton("lyrics", "Now playing: lyrics and queue")
        self.timer_button = PlayerButton("timer", "Sleep timer: off")
        self.mute_button = MuteButton()
        self.volume_slider = VolumeSlider()
        buttons.addWidget(self.heart_button)
        self.shuffle_button = PlayerButton("shuffle", "Shuffle: off")
        self.repeat_button = RepeatButton()
        buttons.addWidget(self.shuffle_button)
        buttons.addWidget(self.repeat_button)
        buttons.addSpacing(32)                # balances the volume controls on the right     # balances the lyrics button so the controls stay centered
        buttons.addStretch(1)
        for button in (self.prev_button, self.skip_back_button, self.play_button, self.stop_button,
                       self.skip_forward_button, self.next_button):
            buttons.addWidget(button)
        buttons.addStretch(1)
        buttons.addWidget(self.mute_button)
        buttons.addWidget(self.volume_slider)
        buttons.addWidget(self.timer_button)
        buttons.addWidget(self.lyrics_button)
        outer.addLayout(buttons)

        self.prev_button.clicked.connect(self.previousClicked.emit)
        self.skip_back_button.clicked.connect(lambda: self.skipRequested.emit(-10000))
        self.play_button.clicked.connect(self.toggleClicked.emit)
        self.stop_button.clicked.connect(self.stopClicked.emit)
        self.skip_forward_button.clicked.connect(lambda: self.skipRequested.emit(10000))
        self.next_button.clicked.connect(self.nextClicked.emit)
        self.heart_button.clicked.connect(self.heartClicked.emit)
        self.lyrics_button.clicked.connect(self.lyricsClicked.emit)
        self.timer_button.clicked.connect(self._sleep_menu)
        self.mute_button.clicked.connect(self.muteClicked.emit)
        self.shuffle_button.clicked.connect(self.shuffleClicked.emit)
        self.repeat_button.clicked.connect(self.repeatClicked.emit)
        self.volume_slider.valueChanged.connect(self.volumeChanged.emit)

        self._set_controls_enabled(False)
        self.lyrics_button.setEnabled(False)
        self.heart_button.setEnabled(False)

    def _set_controls_enabled(self, enabled):
        for widget in (self.prev_button, self.skip_back_button, self.play_button, self.stop_button,
                       self.skip_forward_button, self.next_button, self.slider):
            widget.setEnabled(enabled)

    # ---- driven by the player ---------------------------------------------

    def set_track(self, track):
        self._has_track = True
        self.title.setText(track["title"])
        parts = [part for part in (track.get("artist"), track.get("album")) if part]
        self.subtitle.setText("  \u00b7  ".join(parts))
        self.slider.setRange(0, 0)
        self.elapsed.setText("0:00")
        self.total.setText(format_time(track.get("duration", 0) * 1000))
        self._set_controls_enabled(True)
        self.heart_button.setEnabled(True)

    def set_cover(self, pixmap):
        """The album cover next to the title; None hides it (videos have no cover here)."""
        if pixmap is None or pixmap.isNull():
            self.cover.hide()
            return
        self.cover.setPixmap(pixmap)
        self.cover.show()

    def set_state(self, state):
        self.play_button.show_playing(state == "playing")
        self.stop_button.set_active(state == "stopped" and self._has_track)

    def set_duration(self, ms):
        self.slider.setRange(0, max(0, ms))
        if ms > 0:
            self.total.setText(format_time(ms))

    def set_position(self, ms):
        if not self.slider.dragging:
            self.slider.setValue(ms)
        self.elapsed.setText(format_time(ms))

    def show_error(self, message):
        self.subtitle.setText(message)

    def set_volume_controls(self, volume, muted):
        self.volume_slider.blockSignals(True)
        self.volume_slider.setValue(int(volume))
        self.volume_slider.blockSignals(False)
        self.mute_button.set_muted(muted)
        self.mute_button.set_active(muted)

    def set_shuffle(self, on):
        self.shuffle_button.set_active(on)
        self.shuffle_button.setToolTip("Shuffle: on" if on else "Shuffle: off")

    def set_repeat(self, mode):
        self.repeat_button.set_mode(mode)

    def set_favorite(self, favorite):
        self._favorite = favorite
        self.heart_button.set_active(favorite)
        self.heart_button.setToolTip("Remove from favorites" if favorite else "Add to favorites")

    # ---- sleep timer ------------------------------------------------------

    def _sleep_menu(self):
        menu = QMenu(self)
        choices = [("15 minutes", 15), ("30 minutes", 30), ("45 minutes", 45), ("1 hour", 60),
                   ("End of this song", -1)]
        actions = {menu.addAction(text): minutes for text, minutes in choices}
        off = None
        if self._timer_on:
            menu.addSeparator()
            off = menu.addAction("Turn off the timer")
        chosen = menu.exec(QCursor.pos())
        if chosen is off and off is not None:
            self.sleepTimerChosen.emit(0)
        elif chosen in actions:
            self.sleepTimerChosen.emit(actions[chosen])

    def set_sleep_timer(self, text):
        """text = what to show in the tooltip, or "" when the timer is off."""
        self._timer_on = bool(text)
        self.timer_button.set_active(self._timer_on)
        self.timer_button.setToolTip(f"Sleep timer: {text}" if text else "Sleep timer: off")

    # ---- lyrics button ----------------------------------------------------

    def set_lyrics_available(self, available):
        self._lyrics_available = available
        self.lyrics_button.setEnabled(available or self._lyrics_open)
        self.lyrics_button.setToolTip("Now playing: lyrics and queue" if available else "Now playing is for songs")

    def set_lyrics_open(self, is_open):
        self._lyrics_open = is_open
        self.lyrics_button.set_active(is_open)
        self.lyrics_button.setEnabled(self._lyrics_available or is_open)
