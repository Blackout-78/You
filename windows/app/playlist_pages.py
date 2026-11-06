"""Your own song lists. Made and kept on this PC only."""
from pathlib import Path

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QBrush, QColor
from PySide6.QtWidgets import (
    QDialog, QHBoxLayout, QHeaderView, QLabel, QLineEdit, QMenu, QMessageBox, QPushButton,
    QTreeWidget, QTreeWidgetItem, QVBoxLayout, QWidget,
)

from .look import C
from . import db


def _with_db(action):
    conn = db.connect()
    try:
        return action(conn)
    finally:
        conn.close()


def _link(text):
    button = QPushButton(text)
    button.setObjectName("link")
    button.setCursor(Qt.CursorShape.PointingHandCursor)
    button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
    return button


def _action(text):
    button = QPushButton(text)
    button.setObjectName("action")
    button.setCursor(Qt.CursorShape.PointingHandCursor)
    button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
    return button


class NameDialog(QDialog):
    def __init__(self, parent=None, title="New playlist"):
        super().__init__(parent)
        self.setWindowTitle(title)
        self.resize(440, 140)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(24, 24, 24, 24)
        lay.setSpacing(14)
        self.editor = QLineEdit()
        self.editor.setPlaceholderText("Name of the playlist")
        lay.addWidget(self.editor)
        row = QHBoxLayout()
        row.addStretch(1)
        cancel, create = _action("Cancel"), _action("Create")
        row.addWidget(cancel)
        row.addWidget(create)
        lay.addLayout(row)
        cancel.clicked.connect(self.reject)
        create.clicked.connect(self.accept)

    def text(self):
        return self.editor.text().strip()


def choose_playlist_and_add(parent, path, global_pos):
    """The + next to a song: pick one of your playlists, or make a new one. Returns the playlist name or None."""
    lists = _with_db(db.playlists)
    menu = QMenu(parent)
    chosen = {}
    for entry in lists:
        action = menu.addAction(entry["name"])
        chosen[action] = entry
    if lists:
        menu.addSeparator()
    new_action = menu.addAction("New playlist…")
    picked = menu.exec(global_pos)
    if picked is None:
        return None
    if picked is new_action:
        dialog = NameDialog(parent.window() if parent else None)
        if not dialog.exec() or not dialog.text():
            return None
        name = dialog.text()
        playlist_id = _with_db(lambda c: db.create_playlist(c, name))
        if playlist_id is None:
            QMessageBox.information(parent, "Playlist", "A playlist with that name already exists.")
            return None
    else:
        playlist_id, name = chosen[picked]["id"], chosen[picked]["name"]
    _with_db(lambda c: db.add_to_playlist(c, playlist_id, path))
    return name


class PlaylistsPage(QWidget):
    backRequested = Signal()
    playlistActivated = Signal(object)

    def __init__(self, header_factory, hline):
        super().__init__()
        self._rows = []
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        header, self.back, self.title, self.count = header_factory("←  Music")
        self.title.setText("Playlists")
        root.addWidget(header)
        root.addWidget(hline())

        body = QVBoxLayout()
        body.setContentsMargins(32, 24, 32, 0)
        body.setSpacing(14)
        top = QHBoxLayout()
        self.new_button = _action("New playlist")
        hint = QLabel("Make a list of songs. Press the + next to any song to add it.")
        hint.setObjectName("muted")
        top.addWidget(self.new_button)
        top.addSpacing(16)
        top.addWidget(hint)
        top.addStretch(1)
        body.addLayout(top)

        self.empty = QLabel("No playlists yet.\nPress New playlist to make one.")
        self.empty.setObjectName("muted")
        self.empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        body.addWidget(self.empty)

        self.tree = QTreeWidget()
        self.tree.setColumnCount(2)
        self.tree.setHeaderHidden(True)
        self.tree.setRootIsDecorated(False)
        self.tree.setIndentation(0)
        self.tree.setUniformRowHeights(True)
        self.tree.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.tree.setEditTriggers(QTreeWidget.EditTrigger.NoEditTriggers)
        self.tree.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        head = self.tree.header()
        head.setStretchLastSection(False)
        head.setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        head.setSectionResizeMode(1, QHeaderView.ResizeMode.Fixed)
        self.tree.setColumnWidth(1, 120)
        self.tree.itemClicked.connect(self._clicked)
        body.addWidget(self.tree, 1)
        root.addLayout(body, 1)

        self.back.clicked.connect(self.backRequested.emit)
        self.new_button.clicked.connect(self._new)

    def reload(self):
        self._rows = _with_db(db.playlists)
        self.tree.clear()
        right = Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        for row in self._rows:
            n = row["count"]
            item = QTreeWidgetItem([row["name"], f"{n} song{'s' if n != 1 else ''}"])
            item.setTextAlignment(1, right)
            self.tree.addTopLevelItem(item)
        n = len(self._rows)
        self.count.setText(f"{n} playlist{'s' if n != 1 else ''}")
        self.tree.setVisible(n > 0)
        self.empty.setVisible(n == 0)

    def _new(self):
        dialog = NameDialog(self.window())
        if dialog.exec() and dialog.text():
            if _with_db(lambda c: db.create_playlist(c, dialog.text())) is None:
                QMessageBox.information(self, "Playlist", "A playlist with that name already exists.")
            self.reload()

    def _clicked(self, item, column):
        index = self.tree.indexOfTopLevelItem(item)
        if 0 <= index < len(self._rows):
            self.playlistActivated.emit(self._rows[index])


class PlaylistPage(QWidget):
    backRequested = Signal()
    playRequested = Signal(list, int)

    def __init__(self, header_factory, hline):
        super().__init__()
        self._playlist = None
        self._tracks = []
        self._current_path = None
        self._state = "stopped"
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        header, self.back, self.title, self.count = header_factory("←  Playlists")
        root.addWidget(header)
        root.addWidget(hline())

        body = QVBoxLayout()
        body.setContentsMargins(32, 24, 32, 0)
        body.setSpacing(14)
        top = QHBoxLayout()
        self.play_all = _action("Play all")
        hint = QLabel("Click a number, or double-click a title, to play it. Click × to remove a song.")
        hint.setObjectName("muted")
        self.delete_button = _link("Delete playlist")
        top.addWidget(self.play_all)
        top.addSpacing(16)
        top.addWidget(hint)
        top.addStretch(1)
        top.addWidget(self.delete_button)
        body.addLayout(top)

        self.empty = QLabel("This playlist is empty.\nOpen an album and press the + next to a song.")
        self.empty.setObjectName("muted")
        self.empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        body.addWidget(self.empty)

        self.tree = QTreeWidget()
        self.tree.setColumnCount(5)
        self.tree.setHeaderHidden(True)
        self.tree.setRootIsDecorated(False)
        self.tree.setIndentation(0)
        self.tree.setUniformRowHeights(True)
        self.tree.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.tree.setEditTriggers(QTreeWidget.EditTrigger.NoEditTriggers)
        self.tree.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        head = self.tree.header()
        head.setStretchLastSection(False)
        head.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        head.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        for column in (2, 3, 4):
            head.setSectionResizeMode(column, QHeaderView.ResizeMode.Fixed)
        self.tree.setColumnWidth(0, 72)
        self.tree.setColumnWidth(2, 300)
        self.tree.setColumnWidth(3, 70)
        self.tree.setColumnWidth(4, 50)
        self.tree.itemClicked.connect(self._clicked)
        self.tree.itemDoubleClicked.connect(self._double_clicked)
        body.addWidget(self.tree, 1)
        root.addLayout(body, 1)

        self.back.clicked.connect(self.backRequested.emit)
        self.play_all.clicked.connect(lambda: self._play(0))
        self.delete_button.clicked.connect(self._delete)

    def show_playlist(self, playlist):
        self._playlist = playlist
        self.title.setText(playlist["name"])
        self._tracks = _with_db(lambda c: db.playlist_tracks(c, playlist["id"]))
        self.tree.clear()
        right = Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        for i, t in enumerate(self._tracks, 1):
            who = " · ".join(x for x in (t["artist"], t["album"]) if x)
            minutes, seconds = divmod(int(t["duration"] or 0), 60)
            item = QTreeWidgetItem([f"{i:02d}", t["title"] or Path(t["path"]).stem, who,
                                    f"{minutes}:{seconds:02d}", "×"])
            item.setTextAlignment(3, right)
            item.setTextAlignment(4, right)
            self.tree.addTopLevelItem(item)
        n = len(self._tracks)
        self.count.setText(f"{n} song{'s' if n != 1 else ''}")
        self.tree.setVisible(n > 0)
        self.empty.setVisible(n == 0)
        self.play_all.setEnabled(n > 0)
        self._refresh_marks()

    def _queue(self):
        return [
            {"path": t["path"], "title": t["title"], "artist": t["artist"] or "", "album": t["album"] or "",
             "duration": t["duration"] or 0, "lyrics_path": t["lyrics_path"]}
            for t in self._tracks
        ]

    def _play(self, index):
        if self._tracks:
            self.playRequested.emit(self._queue(), index)

    def _clicked(self, item, column):
        index = self.tree.indexOfTopLevelItem(item)
        if column == 0:
            self._play(index)
        elif column == 4 and 0 <= index < len(self._tracks):
            path = self._tracks[index]["path"]
            _with_db(lambda c: db.remove_from_playlist(c, self._playlist["id"], path))
            self.show_playlist(self._playlist)

    def _double_clicked(self, item, column):
        if column in (1, 2):
            self._play(self.tree.indexOfTopLevelItem(item))

    def _delete(self):
        answer = QMessageBox.question(
            self, "Delete playlist",
            f"Delete “{self._playlist['name']}”? Your songs stay on your PC.",
        )
        if answer == QMessageBox.StandardButton.Yes:
            _with_db(lambda c: db.delete_playlist(c, self._playlist["id"]))
            self.backRequested.emit()

    def mark_playing(self, path):
        self._current_path = path
        self._refresh_marks()

    def set_play_state(self, state):
        self._state = state
        self._refresh_marks()

    def _refresh_marks(self):
        white = QBrush(C.light)
        for i, t in enumerate(self._tracks):
            item = self.tree.topLevelItem(i)
            if item is None:
                continue
            if t["path"] == self._current_path and self._state != "stopped":
                item.setText(0, "▶" if self._state == "playing" else "‖")
                for column in range(5):
                    item.setForeground(column, white)
            else:
                item.setText(0, f"{i + 1:02d}")
                for column in range(5):
                    item.setData(column, Qt.ItemDataRole.ForegroundRole, None)
