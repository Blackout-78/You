import bisect

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QBrush, QColor
from PySide6.QtWidgets import (
    QAbstractItemView, QFrame, QHBoxLayout, QLabel, QListWidget, QListWidgetItem,
    QPlainTextEdit, QPushButton, QStackedWidget, QVBoxLayout, QWidget,
)

from .look import C
from .lyrics import load_lyrics



class LyricsPanel(QWidget):
    """Shows the lyrics of the current track. Synced lyrics highlight and scroll with the song."""

    seekRequested = Signal(int)
    closeRequested = Signal()

    def __init__(self, embedded=False):
        super().__init__()
        self._times = []
        self._current = -1

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        header = QWidget()
        header.setFixedHeight(72)
        row = QHBoxLayout(header)
        row.setContentsMargins(24, 0, 32, 0)
        row.setSpacing(18)
        back = QPushButton("\u2190  Back")
        back.setObjectName("link")
        back.setCursor(Qt.CursorShape.PointingHandCursor)
        back.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        back.clicked.connect(self.closeRequested.emit)
        self.title = QLabel("Lyrics")
        self.title.setObjectName("pageTitle")
        self.source = QLabel("")
        self.source.setObjectName("muted")
        row.addWidget(back)
        row.addWidget(self.title)
        row.addStretch(1)
        row.addWidget(self.source)
        root.addWidget(header)

        line = QFrame()
        line.setObjectName("hairline")
        line.setFixedHeight(1)
        root.addWidget(line)
        if embedded:                 # inside the Now Playing page: no header of its own
            header.hide()
            line.hide()

        self.pages = QStackedWidget()
        self.message = QLabel("No lyrics for this track.")
        self.message.setObjectName("muted")
        self.message.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.list = QListWidget()
        self.list.setObjectName("lyricsList")
        self.list.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.list.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.list.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.list.setCursor(Qt.CursorShape.PointingHandCursor)
        self.list.itemClicked.connect(self._clicked)

        self.text = QPlainTextEdit()
        self.text.setObjectName("lyricsText")
        self.text.setReadOnly(True)

        for page in (self.message, self.list, self.text):
            self.pages.addWidget(page)
        root.addWidget(self.pages, 1)

    def set_track(self, track):
        """Load this track's lyrics. Returns True if it has any."""
        self.title.setText(track["title"])
        self._times = []
        self._current = -1
        self.list.clear()
        self.text.clear()

        found = load_lyrics(track)
        if found is None:
            self.source.setText("")
            self.pages.setCurrentWidget(self.message)
            return False

        center = Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignVCenter
        if found.synced:
            for ms, words in found.lines:
                item = QListWidgetItem(words or "\u00a0")
                item.setTextAlignment(center)
                item.setForeground(QBrush(C.middle))
                self.list.addItem(item)
                self._times.append(ms)
            self.source.setText("Synced lyrics. Click a line to jump to it.")
            self.pages.setCurrentWidget(self.list)
        else:
            self.text.setPlainText("\n".join(words for _, words in found.lines))
            self.source.setText("Lyrics")
            self.pages.setCurrentWidget(self.text)
        return True

    def set_position(self, ms):
        if not self._times or self.pages.currentWidget() is not self.list:
            return
        index = bisect.bisect_right(self._times, ms) - 1      # -1 before the first line
        if index == self._current:
            return
        old = self.list.item(self._current) if self._current >= 0 else None
        if old is not None:
            old.setForeground(QBrush(C.middle))
        self._current = index
        if index >= 0:
            item = self.list.item(index)
            item.setForeground(QBrush(C.light))
            self.list.scrollToItem(item, QAbstractItemView.ScrollHint.PositionAtCenter)

    def _clicked(self, item):
        row = self.list.row(item)
        if 0 <= row < len(self._times):
            self.seekRequested.emit(self._times[row])
