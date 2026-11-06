from PySide6.QtCore import Qt, Signal
from PySide6.QtMultimediaWidgets import QVideoWidget
from PySide6.QtWidgets import QFrame, QHBoxLayout, QLabel, QPushButton, QVBoxLayout, QWidget


class VideoWidget(QVideoWidget):
    """The picture. Click to pause or play, double-click for full screen, Esc to leave it."""

    toggleRequested = Signal()
    skipRequested = Signal(int)

    def __init__(self):
        super().__init__()
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.setStyleSheet("background: #000000;")

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self.toggleRequested.emit()

    def mouseDoubleClickEvent(self, event):
        self.setFullScreen(not self.isFullScreen())

    def keyPressEvent(self, event):
        if event.key() == Qt.Key.Key_Escape and self.isFullScreen():
            self.setFullScreen(False)
        elif event.key() == Qt.Key.Key_Space:
            self.toggleRequested.emit()
        elif event.key() == Qt.Key.Key_Left:
            self.skipRequested.emit(-10000)
        elif event.key() == Qt.Key.Key_Right:
            self.skipRequested.emit(10000)
        else:
            super().keyPressEvent(event)


class VideoView(QWidget):
    """Shown in place of the library while a video plays. The player bar stays underneath."""

    backRequested = Signal()
    stopRequested = Signal()

    def __init__(self):
        super().__init__()
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        header = QWidget()
        header.setFixedHeight(56)
        row = QHBoxLayout(header)
        row.setContentsMargins(24, 0, 32, 0)
        row.setSpacing(18)
        back = QPushButton("←  Back")
        back.setObjectName("link")
        back.setCursor(Qt.CursorShape.PointingHandCursor)
        back.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        back.clicked.connect(self.backRequested.emit)
        self.title = QLabel("")
        self.title.setObjectName("trackTitle")
        hint = QLabel("Click the picture to pause. Double-click for full screen.")
        hint.setObjectName("muted")
        row.addWidget(back)
        row.addWidget(self.title)
        row.addStretch(1)
        row.addWidget(hint)
        stop = QPushButton("Stop")
        stop.setObjectName("link")
        stop.setCursor(Qt.CursorShape.PointingHandCursor)
        stop.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        stop.clicked.connect(self.stopRequested.emit)
        row.addWidget(stop)
        root.addWidget(header)

        line = QFrame()
        line.setObjectName("hairline")
        line.setFixedHeight(1)
        root.addWidget(line)

        self.video = VideoWidget()
        root.addWidget(self.video, 1)

    def set_title(self, text):
        self.title.setText(text)
