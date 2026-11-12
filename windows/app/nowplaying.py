"""The Now Playing page: big cover, lyrics in the middle, the queue ("Up next") on the right."""
from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import (
    QAbstractItemView, QFrame, QHBoxLayout, QLabel, QListWidget, QListWidgetItem, QMenu,
    QPushButton, QVBoxLayout, QWidget,
)

from . import db
from .browse import fitted_pixmap
from .lyricspanel import LyricsPanel

COVER = 340
QUEUE_W = 330
MAX_SHOWN = 200


class QueueDrawer(QFrame):
    """What plays next. Click a song to jump to it, right-click to remove it."""

    jumpRequested = Signal(int)
    removeRequested = Signal(int)
    clearRequested = Signal()

    def __init__(self):
        super().__init__()
        self.setObjectName("lyricsBox")
        self.setFixedWidth(QUEUE_W)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(20, 18, 12, 12)
        lay.setSpacing(8)
        top = QHBoxLayout()
        label = QLabel("UP NEXT")
        label.setObjectName("npKicker")
        self.count = QLabel("")
        self.count.setObjectName("muted")
        clear = QPushButton("Clear")
        clear.setObjectName("link")
        clear.setCursor(Qt.CursorShape.PointingHandCursor)
        clear.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        clear.clicked.connect(self.clearRequested.emit)
        top.addWidget(label)
        top.addStretch(1)
        top.addWidget(self.count)
        top.addSpacing(10)
        top.addWidget(clear)
        lay.addLayout(top)

        self.list = QListWidget()
        self.list.setObjectName("queueList")
        self.list.setIconSize(QSize(44, 44))
        self.list.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.list.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.list.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.list.setCursor(Qt.CursorShape.PointingHandCursor)
        self.list.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.list.itemClicked.connect(lambda item: self.jumpRequested.emit(self.list.row(item)))
        self.list.customContextMenuRequested.connect(self._menu)
        lay.addWidget(self.list, 1)
        self.empty = QLabel("Nothing queued.\nRight-click a song and choose\nPlay next or Add to queue.")
        self.empty.setObjectName("muted")
        self.empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lay.addWidget(self.empty, 1)

    def _menu(self, pos):
        item = self.list.itemAt(pos)
        if item is None:
            return
        row = self.list.row(item)
        menu = QMenu(self)
        play = menu.addAction("Play now")
        remove = menu.addAction("Remove from queue")
        chosen = menu.exec(self.list.viewport().mapToGlobal(pos))
        if chosen is play:
            self.jumpRequested.emit(row)
        elif chosen is remove:
            self.removeRequested.emit(row)

    def set_queue(self, upcoming):
        self.list.clear()
        n = len(upcoming)
        self.count.setText(f"{n} song{'s' if n != 1 else ''}" if n else "")
        self.list.setVisible(n > 0)
        self.empty.setVisible(n == 0)
        if not n:
            return
        dpr = self.devicePixelRatioF()
        conn = db.connect()
        try:
            for track in upcoming[:MAX_SHOWN]:
                who = " · ".join(x for x in (track.get("artist"), track.get("album")) if x)
                item = QListWidgetItem(f"{track['title']}\n{who}" if who else track["title"])
                cover = db.cover_for_track(conn, track["path"])
                item.setIcon(QIcon(fitted_pixmap(cover, 44, 44, dpr)))
                item.setSizeHint(QSize(0, 58))
                self.list.addItem(item)
        finally:
            conn.close()


class NowPlayingPage(QWidget):
    """Same small API the old lyrics page had (set_track, set_position, seekRequested, closeRequested)."""

    seekRequested = Signal(int)
    closeRequested = Signal()
    jumpRequested = Signal(int)
    removeRequested = Signal(int)
    clearRequested = Signal()

    def __init__(self):
        super().__init__()
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        header = QWidget()
        header.setFixedHeight(72)
        row = QHBoxLayout(header)
        row.setContentsMargins(24, 0, 32, 0)
        back = QPushButton("←  Back")
        back.setObjectName("link")
        back.setCursor(Qt.CursorShape.PointingHandCursor)
        back.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        back.clicked.connect(self.closeRequested.emit)
        kicker = QLabel("NOW PLAYING")
        kicker.setObjectName("npKicker")
        row.addWidget(back)
        row.addSpacing(18)
        row.addWidget(kicker)
        row.addStretch(1)
        root.addWidget(header)
        line = QFrame()
        line.setObjectName("hairline")
        line.setFixedHeight(1)
        root.addWidget(line)

        body = QHBoxLayout()
        body.setContentsMargins(32, 24, 32, 24)
        body.setSpacing(28)

        left = QWidget()
        left.setFixedWidth(COVER)
        left_lay = QVBoxLayout(left)
        left_lay.setContentsMargins(0, 0, 0, 0)
        left_lay.setSpacing(8)
        self.cover = QLabel()
        self.cover.setFixedSize(COVER, COVER)
        self.title = QLabel("")
        self.title.setObjectName("npTitle")
        self.title.setWordWrap(True)
        self.artist = QLabel("")
        self.artist.setObjectName("muted")
        self.artist.setWordWrap(True)
        left_lay.addWidget(self.cover)
        left_lay.addSpacing(10)
        left_lay.addWidget(self.title)
        left_lay.addWidget(self.artist)
        left_lay.addStretch(1)
        body.addWidget(left)

        middle = QFrame()
        middle.setObjectName("lyricsBox")
        mid_lay = QVBoxLayout(middle)
        mid_lay.setContentsMargins(20, 18, 20, 12)
        mid_lay.setSpacing(8)
        top = QHBoxLayout()
        label = QLabel("LYRICS")
        label.setObjectName("npKicker")
        self.source = QLabel("")
        self.source.setObjectName("muted")
        top.addWidget(label)
        top.addStretch(1)
        top.addWidget(self.source)
        mid_lay.addLayout(top)
        self.lyrics = LyricsPanel(embedded=True)
        self.lyrics.seekRequested.connect(self.seekRequested.emit)
        mid_lay.addWidget(self.lyrics, 1)
        body.addWidget(middle, 1)

        self.queue = QueueDrawer()
        self.queue.jumpRequested.connect(self.jumpRequested.emit)
        self.queue.removeRequested.connect(self.removeRequested.emit)
        self.queue.clearRequested.connect(self.clearRequested.emit)
        body.addWidget(self.queue)
        root.addLayout(body, 1)

    def set_track(self, track):
        """Show this song. Returns True if it has lyrics."""
        self.title.setText(track["title"])
        who = " · ".join(x for x in (track.get("artist"), track.get("album")) if x)
        self.artist.setText(who)
        conn = db.connect()
        try:
            cover = db.cover_for_track(conn, track["path"])
        finally:
            conn.close()
        self.cover.setPixmap(fitted_pixmap(cover, COVER, COVER, self.devicePixelRatioF()))
        has = self.lyrics.set_track(track)
        self.source.setText(self.lyrics.source.text())
        return has

    def set_position(self, ms):
        self.lyrics.set_position(ms)

    def set_queue(self, upcoming):
        self.queue.set_queue(upcoming)
