import os
from pathlib import Path

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QBrush, QColor, QCursor, QPixmap
from PySide6.QtWidgets import (
    QDialog, QFileDialog, QFrame, QHBoxLayout, QHeaderView, QLabel, QLineEdit, QMenu, QPlainTextEdit, QPushButton,
    QScrollArea, QStackedWidget, QTreeWidget, QTreeWidgetItem, QVBoxLayout, QWidget,
)

from .look import C
from . import db
from .covers import save_cover
from .grid import MediaGrid
from .browse import HeroBanner, SQUARE, SquareTile, TileRow, section_title
from .pages import LibraryPage
from .playlist_pages import PlaylistPage, PlaylistsPage, choose_playlist_and_add

ARTIST_COVER = 280
ALBUM_COVER = 300
LEFT_WIDTH = 360


# ---- small building blocks ------------------------------------------------------

def attach_queue_menu(tree, track_at, emit):
    """Right-click a song: Play next, or Add to queue."""
    tree.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)

    def show(pos):
        item = tree.itemAt(pos)
        if item is None:
            return
        track = track_at(tree.indexOfTopLevelItem(item))
        if not track:
            return
        menu = QMenu(tree)
        play_next = menu.addAction("Play next")
        add = menu.addAction("Add to queue")
        chosen = menu.exec(tree.viewport().mapToGlobal(pos))
        if chosen is play_next:
            emit(track, True)
        elif chosen is add:
            emit(track, False)

    tree.customContextMenuRequested.connect(show)


def _hline():
    line = QFrame()
    line.setObjectName("hairline")
    line.setFixedHeight(1)
    return line


def _vline():
    line = QFrame()
    line.setObjectName("vline")
    line.setFixedWidth(1)
    return line


def _header(back_text):
    header = QWidget()
    header.setFixedHeight(72)
    row = QHBoxLayout(header)
    row.setContentsMargins(24, 0, 32, 0)
    row.setSpacing(18)
    back = QPushButton(back_text)
    back.setObjectName("link")
    back.setCursor(Qt.CursorShape.PointingHandCursor)
    back.setFocusPolicy(Qt.FocusPolicy.NoFocus)
    title = QLabel()
    title.setObjectName("pageTitle")
    count = QLabel()
    count.setObjectName("muted")
    row.addWidget(back)
    row.addWidget(title)
    row.addStretch(1)
    row.addWidget(count)
    return header, back, title, count


def cover_pixmap(path, width, dpr, height=None):
    """A cover, sharp on any display scaling. A dark tile if there is no picture."""
    px_w = int(width * dpr)
    px_h = int((height or width) * dpr)
    source = QPixmap(path) if path else QPixmap()
    if source.isNull():
        out = QPixmap(px_w, px_h)
        out.fill(C.placeholder)
    else:
        scaled = source.scaled(
            px_w, px_h, Qt.AspectRatioMode.KeepAspectRatioByExpanding,
            Qt.TransformationMode.SmoothTransformation,
        )
        out = scaled.copy((scaled.width() - px_w) // 2, (scaled.height() - px_h) // 2, px_w, px_h)
    out.setDevicePixelRatio(dpr)
    return out


def pick_cover(parent, box):
    """Let you choose a picture. Returns the saved cover path, or None."""
    path, _ = QFileDialog.getOpenFileName(
        parent, "Choose a cover picture", "",
        "Pictures (*.jpg *.jpeg *.png *.webp *.bmp);;All files (*)",
    )
    if not path:
        return None
    try:
        saved = save_cover(Path(path).read_bytes())
    except OSError:
        saved = None
    box.set_note("" if saved else "That file couldn't be read as a picture.")
    return saved


def format_total(seconds):
    seconds = int(seconds)
    hours, rest = divmod(seconds, 3600)
    minutes, secs = divmod(rest, 60)
    return f"{hours}:{minutes:02d}:{secs:02d}" if hours else f"{minutes}:{secs:02d}"


class TitleDialog(QDialog):
    def __init__(self, text, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Edit title")
        self.resize(480, 150)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(24, 24, 24, 24)
        lay.setSpacing(14)
        self.editor = QLineEdit(text)
        self.editor.setPlaceholderText("Leave empty to use the name from the folder.")
        lay.addWidget(self.editor)
        row = QHBoxLayout()
        row.addStretch(1)
        cancel = QPushButton("Cancel")
        save = QPushButton("Save")
        for button in (cancel, save):
            button.setObjectName("action")
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            row.addWidget(button)
        lay.addLayout(row)
        cancel.clicked.connect(self.reject)
        save.clicked.connect(self.accept)

    def text(self):
        return self.editor.text().strip()


class DescriptionDialog(QDialog):
    def __init__(self, text, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Edit description")
        self.resize(560, 360)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(24, 24, 24, 24)
        lay.setSpacing(14)
        self.editor = QPlainTextEdit()
        self.editor.setPlainText(text)
        self.editor.setPlaceholderText("Write your own description here.")
        lay.addWidget(self.editor, 1)
        row = QHBoxLayout()
        row.addStretch(1)
        cancel = QPushButton("Cancel")
        save = QPushButton("Save")
        for button in (cancel, save):
            button.setObjectName("action")
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            row.addWidget(button)
        lay.addLayout(row)
        cancel.clicked.connect(self.reject)
        save.clicked.connect(self.accept)

    def text(self):
        return self.editor.toPlainText().strip()


class BlurbBox(QWidget):
    """The description under a cover, with a button to write or change it."""

    changed = Signal(str)

    def __init__(self):
        super().__init__()
        self._text = ""
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(8)
        self.label = QLabel()
        self.label.setWordWrap(True)
        self.label.setAlignment(Qt.AlignmentFlag.AlignTop | Qt.AlignmentFlag.AlignLeft)
        self.label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        lay.addWidget(self.label)
        row = QHBoxLayout()
        self.edit_button = QPushButton("Edit description")
        self.edit_button.setObjectName("link")
        self.edit_button.setCursor(Qt.CursorShape.PointingHandCursor)
        self.edit_button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.edit_button.clicked.connect(self._edit)
        row.addWidget(self.edit_button)
        row.addStretch(1)
        lay.addLayout(row)
        self.set_text("")

    def text(self):
        return self._text

    def set_text(self, text):
        self._text = text or ""
        if self._text:
            self.label.setText(self._text)
            self.label.setStyleSheet(f"color: {C.soft.name()};")
        else:
            self.label.setText("No description yet.")
            self.label.setStyleSheet(f"color: {C.middle.name()};")

    def _edit(self):
        dialog = DescriptionDialog(self._text, self.window())
        if dialog.exec():
            self.set_text(dialog.text())
            self.changed.emit(self._text)


class CoverBox(QWidget):
    """The big cover, with buttons to use your own picture."""

    changeRequested = Signal()
    resetRequested = Signal()
    renameRequested = Signal()

    def __init__(self, width, height=None, rename=False):
        super().__init__()
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(8)
        self.picture = QLabel()
        self.picture.setFixedSize(width, height or width)
        lay.addWidget(self.picture, 0, Qt.AlignmentFlag.AlignHCenter)

        row = QHBoxLayout()
        row.setSpacing(4)
        self.change_button = QPushButton("Change cover")
        self.reset_button = QPushButton("Use original")
        self.rename_button = QPushButton("Edit title")
        for button in (self.change_button, self.reset_button, self.rename_button):
            button.setObjectName("link")
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
            row.addWidget(button)
        row.addStretch(1)
        lay.addLayout(row)
        self.note = QLabel("")
        self.note.setObjectName("muted")
        self.note.setWordWrap(True)
        self.note.hide()
        lay.addWidget(self.note)

        self.change_button.clicked.connect(self.changeRequested.emit)
        self.reset_button.clicked.connect(self.resetRequested.emit)
        self.rename_button.clicked.connect(self.renameRequested.emit)
        self.reset_button.hide()
        self.rename_button.setVisible(rename)

    def set_custom(self, is_custom):
        self.reset_button.setVisible(is_custom)

    def set_note(self, text):
        self.note.setText(text)
        self.note.setVisible(bool(text))


def _left_column(cover_label, blurb, width=LEFT_WIDTH):
    """Cover with its description underneath, in a scrolling column."""
    scroll = QScrollArea()
    scroll.setFixedWidth(width)
    scroll.setWidgetResizable(True)
    scroll.setFrameShape(QFrame.Shape.NoFrame)
    inner = QWidget()
    lay = QVBoxLayout(inner)
    lay.setContentsMargins(32, 28, 32, 28)
    lay.setSpacing(18)
    lay.addWidget(cover_label, 0, Qt.AlignmentFlag.AlignHCenter)
    lay.addWidget(blurb)
    lay.addStretch(1)
    scroll.setWidget(inner)
    return scroll


# ---- artist page -----------------------------------------------------------------

class ArtistPage(QWidget):
    """Big picture, then songs, then rows of albums and of singles and EPs."""

    backRequested = Signal()
    albumActivated = Signal(object)
    playRequested = Signal(list, int)
    queueRequested = Signal(dict, bool)       # song, True = play next / False = add to queue

    SHOWN = 5

    def __init__(self):
        super().__init__()
        self._artist = None
        self._songs = []
        self._show_all = False
        self._hero_cover = None
        self._current_path = None
        self._state = "stopped"

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        header, self.back, self.title, self.count = _header("\u2190  Music")
        root.addWidget(header)
        root.addWidget(_hline())

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        body = QWidget()
        lay = QVBoxLayout(body)
        lay.setContentsMargins(0, 0, 0, 24)
        lay.setSpacing(0)

        self.hero = HeroBanner(primary="Shuffle", secondary="Play")
        self.hero.primaryClicked.connect(self._shuffle)
        self.hero.secondaryClicked.connect(lambda: self._play(0))
        lay.addWidget(self.hero)

        links = QHBoxLayout()
        links.setContentsMargins(24, 10, 32, 0)
        self.change_button = self._link("Change picture")
        self.reset_button = self._link("Use original")
        links.addWidget(self.change_button)
        links.addWidget(self.reset_button)
        links.addStretch(1)
        lay.addLayout(links)
        self.note = QLabel("")
        self.note.setObjectName("muted")
        self.note.setContentsMargins(32, 0, 32, 0)
        self.note.hide()
        lay.addWidget(self.note)

        blurb_wrap = QWidget()
        blurb_lay = QVBoxLayout(blurb_wrap)
        blurb_lay.setContentsMargins(32, 6, 32, 0)
        self.blurb = BlurbBox()
        blurb_lay.addWidget(self.blurb)
        lay.addWidget(blurb_wrap)

        lay.addWidget(section_title("Songs"))
        self.songs = QTreeWidget()
        self.songs.setColumnCount(5)
        self.songs.setHeaderHidden(True)
        self.songs.setRootIsDecorated(False)
        self.songs.setIndentation(0)
        self.songs.setUniformRowHeights(True)
        self.songs.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.songs.setEditTriggers(QTreeWidget.EditTrigger.NoEditTriggers)
        self.songs.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.songs.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        head = self.songs.header()
        head.setStretchLastSection(False)
        head.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        head.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        for column in (2, 3, 4):
            head.setSectionResizeMode(column, QHeaderView.ResizeMode.Fixed)
        self.songs.setColumnWidth(0, 72)
        self.songs.setColumnWidth(2, 300)
        self.songs.setColumnWidth(3, 70)
        self.songs.setColumnWidth(4, 50)
        self.songs.itemClicked.connect(self._clicked)
        attach_queue_menu(self.songs, lambda i: (self._queue()[i] if 0 <= i < len(self._songs) else None),
                          lambda t, nxt: self.queueRequested.emit(t, nxt))
        self.songs.itemDoubleClicked.connect(self._double_clicked)
        songs_wrap = QWidget()
        songs_lay = QVBoxLayout(songs_wrap)
        songs_lay.setContentsMargins(32, 0, 32, 0)
        songs_lay.addWidget(self.songs)
        lay.addWidget(songs_wrap)
        more = QHBoxLayout()
        more.setContentsMargins(24, 4, 32, 0)
        self.more_button = self._link("")
        more.addWidget(self.more_button)
        more.addStretch(1)
        lay.addLayout(more)

        self.albums_title = section_title("Albums")
        self.albums_row = TileRow(SQUARE + 52)
        self.singles_title = section_title("Singles and EPs")
        self.singles_row = TileRow(SQUARE + 52)
        for widget in (self.albums_title, self.albums_row, self.singles_title, self.singles_row):
            lay.addWidget(widget)
        lay.addStretch(1)
        scroll.setWidget(body)
        root.addWidget(scroll, 1)

        self.blurb.changed.connect(self._save_note)
        self.change_button.clicked.connect(self._choose_cover)
        self.reset_button.clicked.connect(self._reset_cover)
        self.more_button.clicked.connect(self._toggle_all)
        self.back.clicked.connect(self.backRequested.emit)

    @staticmethod
    def _link(text):
        button = QPushButton(text)
        button.setObjectName("link")
        button.setCursor(Qt.CursorShape.PointingHandCursor)
        button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        return button

    def show_artist(self, artist, album_items, note, songs):
        self._artist = artist
        self._songs = songs
        self._show_all = False
        self.title.setText(artist["name"])
        n = len(album_items)
        self.count.setText(f"{n} album" if n == 1 else f"{n} albums")
        self.blurb.set_text(note)
        self.note.hide()

        # the big picture: your own, the artist's, or the cover of one of their albums
        cover = artist.get("cover") or next((a["cover_path"] for a in album_items if a.get("cover_path")), None)
        self._hero_cover = cover
        songs_word = f"{len(songs)} song{'s' if len(songs) != 1 else ''}"
        self.hero.set_content(artist["name"], f"{n} album{'s' if n != 1 else ''}  \u00b7  {songs_word}", "", cover)
        self.reset_button.setVisible(bool(artist.get("custom_cover")))
        self._fill_songs()

        dpr = self.devicePixelRatioF()
        albums, singles = [], []
        for item in album_items:
            tracks = item.get("tracks", 0)
            year = item.get("year") or ""
            is_single = tracks <= 4
            sub = (f"Single  \u00b7  {year}" if tracks == 1 else f"EP  \u00b7  {year}") if is_single else (
                f"{year}  \u00b7  {tracks} tracks" if year else f"{tracks} tracks")
            tile = SquareTile(item["title"], sub.strip(" \u00b7"), item.get("cover_path"), dpr)
            tile.clicked.connect(lambda i=item: self.albumActivated.emit(i))
            (singles if is_single else albums).append(tile)
        self.albums_title.setVisible(bool(albums))
        self.albums_row.setVisible(bool(albums))
        self.albums_row.set_tiles(albums)
        self.singles_title.setVisible(bool(singles))
        self.singles_row.setVisible(bool(singles))
        self.singles_row.set_tiles(singles)

    # ---- songs ------------------------------------------------------------

    def _visible_songs(self):
        return self._songs if self._show_all else self._songs[:self.SHOWN]

    def _fill_songs(self):
        self.songs.clear()
        right = Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        for i, t in enumerate(self._visible_songs(), 1):
            item = QTreeWidgetItem([f"{i:02d}", t["title"], t["album"] or "", format_total(t["duration"] or 0), "+"])
            item.setTextAlignment(3, right)
            item.setTextAlignment(4, right)
            item.setToolTip(4, "Add to a playlist")
            self.songs.addTopLevelItem(item)
        rows = self.songs.topLevelItemCount()
        row_h = self.songs.sizeHintForRow(0) if rows else 0
        self.songs.setFixedHeight(rows * row_h + 4)
        extra = len(self._songs) - self.SHOWN
        self.more_button.setVisible(extra > 0)
        self.more_button.setText("Show fewer" if self._show_all else f"Show all {len(self._songs)}")
        self._refresh_marks()

    def _toggle_all(self):
        self._show_all = not self._show_all
        self._fill_songs()

    def _queue(self):
        return [
            {"path": t["path"], "title": t["title"], "artist": t["artist"] or "", "album": t["album"] or "",
             "duration": t["duration"] or 0, "lyrics_path": t["lyrics_path"]}
            for t in self._songs
        ]

    def _play(self, index):
        if self._songs:
            self.playRequested.emit(self._queue(), index)

    def _shuffle(self):
        if not self._songs:
            return
        import random
        queue = self._queue()
        random.shuffle(queue)
        self.playRequested.emit(queue, 0)

    def _clicked(self, item, column):
        index = self.songs.indexOfTopLevelItem(item)
        if column == 0:
            self._play(index)
        elif column == 4 and 0 <= index < len(self._songs):
            choose_playlist_and_add(self.songs, self._songs[index]["path"], QCursor.pos())

    def _double_clicked(self, item, column):
        if column in (1, 2):
            self._play(self.songs.indexOfTopLevelItem(item))

    def mark_playing(self, path):
        self._current_path = path
        self._refresh_marks()

    def set_play_state(self, state):
        self._state = state
        self._refresh_marks()

    def _refresh_marks(self):
        white = QBrush(C.light)
        for i, t in enumerate(self._visible_songs()):
            item = self.songs.topLevelItem(i)
            if item is None:
                continue
            if t["path"] == self._current_path and self._state != "stopped":
                item.setText(0, "\u25b6" if self._state == "playing" else "\u2016")
                for column in range(5):
                    item.setForeground(column, white)
            else:
                item.setText(0, f"{i + 1:02d}")
                for column in range(5):
                    item.setData(column, Qt.ItemDataRole.ForegroundRole, None)

    # ---- picture and description --------------------------------------------

    def _choose_cover(self):
        path, _ = QFileDialog.getOpenFileName(
            self, "Choose a picture", "",
            "Pictures (*.jpg *.jpeg *.png *.webp *.bmp);;All files (*)",
        )
        if not path:
            return
        try:
            saved = save_cover(Path(path).read_bytes())
        except OSError:
            saved = None
        if not saved:
            self.note.setText("That file couldn't be read as a picture.")
            self.note.show()
            return
        self.note.hide()
        self._set_custom(saved)
        self._artist["cover"], self._artist["custom_cover"] = saved, True
        self.hero.set_content(self._artist["name"], self.hero.meta.text(), "", saved)
        self.reset_button.show()

    def _reset_cover(self):
        self._set_custom(None)
        conn = db.connect()
        try:
            original = db.get_artist(conn, self._artist["id"])
        finally:
            conn.close()
        self._artist = original
        self.note.hide()
        self.hero.set_content(original["name"], self.hero.meta.text(), "", original.get("cover") or self._hero_cover)
        self.reset_button.hide()

    def _set_custom(self, path):
        conn = db.connect()
        try:
            db.set_custom_cover(conn, "artist", db.artist_note_key(self._artist["name"]), path)
        finally:
            conn.close()

    def _save_note(self, text):
        conn = db.connect()
        try:
            db.set_note(conn, "artist", db.artist_note_key(self._artist["name"]), text)
        finally:
            conn.close()


class ArtistGridPage(QWidget):
    """The Grid look: cover and description on the left, albums in a grid on the right."""
    backRequested = Signal()
    albumActivated = Signal(object)

    def __init__(self):
        super().__init__()
        self._artist = None
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        header, self.back, self.title, self.count = _header("\u2190  Music")
        root.addWidget(header)
        root.addWidget(_hline())

        self.cover_box = CoverBox(ARTIST_COVER)
        self.cover_box.changeRequested.connect(self._choose_cover)
        self.cover_box.resetRequested.connect(self._reset_cover)
        self.blurb = BlurbBox()
        self.blurb.changed.connect(self._save_note)

        self.albums = MediaGrid(
            aspect=1.0, empty_title="No albums", empty_hint="", columns=3, blurb_lines=3
        )

        body = QHBoxLayout()
        body.setSpacing(0)
        body.addWidget(_left_column(self.cover_box, self.blurb))
        body.addWidget(_vline())
        body.addWidget(self.albums, 1)
        root.addLayout(body, 1)

        self.back.clicked.connect(self.backRequested.emit)
        self.albums.itemActivated.connect(self.albumActivated.emit)

    def show_artist(self, artist, album_items, note):
        self._artist = artist
        self.title.setText(artist["name"])
        self._show_cover(artist["cover"], artist.get("custom_cover", False))
        self.blurb.set_text(note)
        self.albums.set_items(album_items)
        n = len(album_items)
        self.count.setText(f"{n} album" if n == 1 else f"{n} albums")

    def _show_cover(self, path, is_custom):
        self.cover_box.picture.setPixmap(cover_pixmap(path, ARTIST_COVER, self.devicePixelRatioF()))
        self.cover_box.set_custom(is_custom)

    def _choose_cover(self):
        path, _ = QFileDialog.getOpenFileName(
            self, "Choose a cover picture", "",
            "Pictures (*.jpg *.jpeg *.png *.webp *.bmp);;All files (*)",
        )
        if not path:
            return
        try:
            saved = save_cover(Path(path).read_bytes())
        except OSError:
            saved = None
        if not saved:
            self.cover_box.set_note("That file couldn't be read as a picture.")
            return
        self._set_custom(saved)
        self.cover_box.set_note("")
        self._show_cover(saved, True)

    def _reset_cover(self):
        self._set_custom(None)
        conn = db.connect()
        try:
            original = db.get_artist(conn, self._artist["id"])
        finally:
            conn.close()
        self._artist = original
        self.cover_box.set_note("")
        self._show_cover(original["cover"], False)

    def _set_custom(self, path):
        conn = db.connect()
        try:
            db.set_custom_cover(conn, "artist", db.artist_note_key(self._artist["name"]), path)
        finally:
            conn.close()

    def _save_note(self, text):
        conn = db.connect()
        try:
            db.set_note(conn, "artist", db.artist_note_key(self._artist["name"]), text)
        finally:
            conn.close()


# ---- album page ------------------------------------------------------------------

class AlbumPage(QWidget):
    backRequested = Signal()
    playRequested = Signal(list, int)
    queueRequested = Signal(dict, bool)

    def __init__(self):
        super().__init__()
        self._album = None
        self._tracks = []
        self._labels = []
        self._current_path = None
        self._state = "stopped"

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        header, self.back, self.title, self.count = _header("\u2190  Back")
        root.addWidget(header)
        root.addWidget(_hline())

        self.cover = QLabel()
        self.cover.setFixedSize(ALBUM_COVER, ALBUM_COVER)
        self.blurb = BlurbBox()
        self.blurb.changed.connect(self._save_note)

        right = QWidget()
        right_lay = QVBoxLayout(right)
        right_lay.setContentsMargins(32, 24, 32, 0)
        right_lay.setSpacing(14)

        top = QHBoxLayout()
        self.play_album = QPushButton("Play album")
        self.play_album.setObjectName("action")
        self.play_album.setCursor(Qt.CursorShape.PointingHandCursor)
        self.play_album.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.play_album.clicked.connect(lambda: self._play(0))
        hint = QLabel("Click a track number, or double-click a title, to play it.")
        hint.setObjectName("muted")
        top.addWidget(self.play_album)
        top.addSpacing(16)
        top.addWidget(hint)
        top.addStretch(1)
        right_lay.addLayout(top)

        self.tree = QTreeWidget()
        self.tree.setColumnCount(4)
        self.tree.setHeaderHidden(True)
        self.tree.setRootIsDecorated(False)
        self.tree.setIndentation(0)
        self.tree.setUniformRowHeights(True)
        self.tree.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.tree.setEditTriggers(QTreeWidget.EditTrigger.NoEditTriggers)
        self.tree.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        header_view = self.tree.header()
        header_view.setStretchLastSection(False)
        header_view.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        header_view.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        header_view.setSectionResizeMode(2, QHeaderView.ResizeMode.Fixed)
        header_view.setSectionResizeMode(3, QHeaderView.ResizeMode.Fixed)
        self.tree.setColumnWidth(0, 72)
        self.tree.setColumnWidth(2, 90)
        self.tree.setColumnWidth(3, 50)
        self.tree.itemClicked.connect(self._clicked)
        attach_queue_menu(self.tree, lambda i: (self._queue()[i] if 0 <= i < len(self._tracks) else None),
                          lambda t, nxt: self.queueRequested.emit(t, nxt))
        self.tree.itemDoubleClicked.connect(self._double_clicked)
        right_lay.addWidget(self.tree, 1)

        body = QHBoxLayout()
        body.setSpacing(0)
        body.addWidget(_left_column(self.cover, self.blurb, ALBUM_COVER + 64))
        body.addWidget(_vline())
        body.addWidget(right, 1)
        root.addLayout(body, 1)

        self.back.clicked.connect(self.backRequested.emit)

    def show_album(self, album, tracks, note):
        self._album = album
        self._tracks = tracks
        self.title.setText(album["title"])
        self.back.setText(f"\u2190  {album['artist']}")
        self.cover.setPixmap(cover_pixmap(album["cover"], ALBUM_COVER, self.devicePixelRatioF()))
        self.blurb.set_text(note)

        discs = {t["disc"] for t in tracks}
        multi_disc = len(discs) > 1
        self.tree.clear()
        self._labels = []
        right = Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        for track in tracks:
            if not track["number"]:
                label = "\u2013"
            elif multi_disc:
                label = f"{track['disc']}-{track['number']:02d}"
            else:
                label = f"{track['number']:02d}"
            self._labels.append(label)
            item = QTreeWidgetItem([label, track["title"], format_total(track["duration"] or 0), "+"])
            item.setTextAlignment(2, right)
            item.setTextAlignment(3, right)
            item.setToolTip(3, "Add to a playlist")
            self.tree.addTopLevelItem(item)

        total = sum(t["duration"] or 0 for t in tracks)
        n = len(tracks)
        self.count.setText(f"{n} track{'s' if n != 1 else ''}  \u00b7  {format_total(total)}")
        self._refresh_marks()

    # ---- playing ----------------------------------------------------------

    def _queue(self):
        return [
            {"path": t["path"], "title": t["title"], "artist": t["artist"],
             "album": t["album"], "duration": t["duration"] or 0,
             "lyrics_path": t["lyrics_path"]}
            for t in self._tracks
        ]

    def _play(self, index):
        if self._tracks:
            self.playRequested.emit(self._queue(), index)

    def _clicked(self, item, column):
        index = self.tree.indexOfTopLevelItem(item)
        if column == 0:
            self._play(index)
        elif column == 3 and 0 <= index < len(self._tracks):
            choose_playlist_and_add(self.tree, self._tracks[index]["path"], QCursor.pos())

    def _double_clicked(self, item, column):
        if column in (1, 2):
            self._play(self.tree.indexOfTopLevelItem(item))

    def mark_playing(self, path):
        self._current_path = path
        self._refresh_marks()

    def set_play_state(self, state):
        self._state = state
        self._refresh_marks()

    def _refresh_marks(self):
        white = QBrush(C.light)
        for i, track in enumerate(self._tracks):
            item = self.tree.topLevelItem(i)
            if item is None:
                continue
            is_current = track["path"] == self._current_path and self._state != "stopped"
            if is_current:
                item.setText(0, "\u25b6" if self._state == "playing" else "\u2016")
                for column in range(4):
                    item.setForeground(column, white)
            else:
                item.setText(0, self._labels[i])
                for column in range(4):
                    item.setData(column, Qt.ItemDataRole.ForegroundRole, None)

    def _save_note(self, text):
        conn = db.connect()
        try:
            key = db.album_note_key(self._album["artist"], self._album["title"])
            db.set_note(conn, "album", key, text)
        finally:
            conn.close()


class FavoritesPage(QWidget):
    """Every song and video you gave a heart. Click the number or double-click a title to play."""

    backRequested = Signal()
    playRequested = Signal(list, int)
    queueRequested = Signal(dict, bool)
    changed = Signal()                  # a favorite was removed here

    def __init__(self):
        super().__init__()
        self._rows = []
        self._current_path = None
        self._state = "stopped"

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        header, self.back, self.title, self.count = _header("\u2190  Music")
        self.title.setText("Favorites")
        root.addWidget(header)
        root.addWidget(_hline())

        body = QVBoxLayout()
        body.setContentsMargins(32, 24, 32, 0)
        body.setSpacing(14)

        top = QHBoxLayout()
        self.play_all = QPushButton("Play all")
        self.play_all.setObjectName("action")
        self.play_all.setCursor(Qt.CursorShape.PointingHandCursor)
        self.play_all.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.play_all.clicked.connect(lambda: self._play(0))
        hint = QLabel("Click a number, or double-click a title, to play it. Click the heart to remove it.")
        hint.setObjectName("muted")
        top.addWidget(self.play_all)
        top.addSpacing(16)
        top.addWidget(hint)
        top.addStretch(1)
        body.addLayout(top)

        self.empty = QLabel("No favorites yet.\nPress the heart on the player bar while something plays.")
        self.empty.setObjectName("muted")
        self.empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        body.addWidget(self.empty)

        self.tree = QTreeWidget()
        self.tree.setColumnCount(4)
        self.tree.setHeaderHidden(True)
        self.tree.setRootIsDecorated(False)
        self.tree.setIndentation(0)
        self.tree.setUniformRowHeights(True)
        self.tree.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.tree.setEditTriggers(QTreeWidget.EditTrigger.NoEditTriggers)
        self.tree.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        header_view = self.tree.header()
        header_view.setStretchLastSection(False)
        header_view.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        header_view.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        header_view.setSectionResizeMode(2, QHeaderView.ResizeMode.Fixed)
        header_view.setSectionResizeMode(3, QHeaderView.ResizeMode.Fixed)
        self.tree.setColumnWidth(0, 72)
        self.tree.setColumnWidth(2, 300)
        self.tree.setColumnWidth(3, 70)
        self.tree.itemClicked.connect(self._clicked)
        attach_queue_menu(self.tree, lambda i: (self._queue()[i] if 0 <= i < len(self._rows) else None),
                          lambda t, nxt: self.queueRequested.emit(t, nxt))
        self.tree.itemDoubleClicked.connect(self._double_clicked)
        body.addWidget(self.tree, 1)
        root.addLayout(body, 1)

        self.back.clicked.connect(self.backRequested.emit)

    def reload(self):
        conn = db.connect()
        try:
            rows = db.favorite_tracks(conn)
        finally:
            conn.close()
        self._rows = [r for r in rows if os.path.exists(r["path"])]       # skip files you moved or deleted
        self.tree.clear()
        right = Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        for i, row in enumerate(self._rows, 1):
            who = " \u00b7 ".join(x for x in (row["artist"], row["album"]) if x)
            item = QTreeWidgetItem([f"{i:02d}", row["title"] or Path(row["path"]).stem, who, "\u2665"])
            item.setTextAlignment(3, right)
            self.tree.addTopLevelItem(item)
        n = len(self._rows)
        self.count.setText(f"{n} favorite{'s' if n != 1 else ''}")
        self.tree.setVisible(n > 0)
        self.empty.setVisible(n == 0)
        self.play_all.setEnabled(n > 0)
        self._refresh_marks()

    def _queue(self):
        return [
            {"path": r["path"], "title": r["title"] or Path(r["path"]).stem, "artist": r["artist"] or "",
             "album": r["album"] or "", "duration": r["duration"] or 0, "lyrics_path": r["lyrics_path"],
             **({"video": True} if r["video"] else {})}
            for r in self._rows
        ]

    def _play(self, index):
        if self._rows:
            self.playRequested.emit(self._queue(), index)

    def _clicked(self, item, column):
        index = self.tree.indexOfTopLevelItem(item)
        if column == 0:
            self._play(index)
        elif column == 3 and 0 <= index < len(self._rows):
            conn = db.connect()
            try:
                db.remove_favorite(conn, self._rows[index]["path"])
            finally:
                conn.close()
            self.reload()
            self.changed.emit()

    def _double_clicked(self, item, column):
        if column in (1, 2):
            self._play(self.tree.indexOfTopLevelItem(item))

    def mark_playing(self, path):
        self._current_path = path
        self._refresh_marks()

    def set_play_state(self, state):
        self._state = state
        self._refresh_marks()

    def _refresh_marks(self):
        white = QBrush(C.light)
        for i, row in enumerate(self._rows):
            item = self.tree.topLevelItem(i)
            if item is None:
                continue
            if row["path"] == self._current_path and self._state != "stopped":
                item.setText(0, "\u25b6" if self._state == "playing" else "\u2016")
                for column in range(4):
                    item.setForeground(column, white)
            else:
                item.setText(0, f"{i + 1:02d}")
                for column in range(4):
                    item.setData(column, Qt.ItemDataRole.ForegroundRole, None)


# ---- the Music section: artists -> artist -> album ---------------------------------

class MusicSection(QStackedWidget):
    playRequested = Signal(list, int)
    queueRequested = Signal(dict, bool)
    favoritesChanged = Signal()

    def __init__(self):
        super().__init__()
        self._artist_id = None
        self.library = LibraryPage(
            "Music", aspect=1.0, nouns=("artist", "artists"),
            empty_title="No music yet", empty_hint="Add your music folder in Settings.",
        )
        self.artist_page = ArtistPage()
        self.artist_grid_page = ArtistGridPage()
        self._theme = "browse"
        self.album_page = AlbumPage()
        self.favorites_page = FavoritesPage()
        self.playlists_page = PlaylistsPage(_header, _hline)
        self.playlist_page = PlaylistPage(_header, _hline)
        for page in (self.library, self.artist_page, self.album_page, self.favorites_page,
                     self.playlists_page, self.playlist_page, self.artist_grid_page):
            self.addWidget(page)

        playlists_button = QPushButton("Playlists")
        playlists_button.setObjectName("link")
        playlists_button.setCursor(Qt.CursorShape.PointingHandCursor)
        playlists_button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        playlists_button.clicked.connect(self.open_playlists)
        self.library.add_header_button(playlists_button)
        self.playlists_page.backRequested.connect(self.show_library)
        self.playlists_page.playlistActivated.connect(self.open_playlist)
        self.playlist_page.backRequested.connect(self.open_playlists)
        self.playlist_page.playRequested.connect(self.playRequested.emit)
        self.artist_page.playRequested.connect(self.playRequested.emit)

        favorites_button = QPushButton("\u2665  Favorites")
        favorites_button.setObjectName("link")
        favorites_button.setCursor(Qt.CursorShape.PointingHandCursor)
        favorites_button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        favorites_button.clicked.connect(self.open_favorites)
        self.library.add_header_button(favorites_button)
        self.favorites_page.backRequested.connect(self.show_library)
        self.favorites_page.playRequested.connect(self.playRequested.emit)
        self.favorites_page.changed.connect(self.favoritesChanged.emit)

        self.library.grid.itemActivated.connect(self.open_artist)
        self.artist_page.albumActivated.connect(self.open_album)
        self.artist_page.backRequested.connect(self.reload)
        self.artist_grid_page.albumActivated.connect(self.open_album)
        self.artist_grid_page.backRequested.connect(self.reload)
        self.album_page.backRequested.connect(self._back_to_artist)
        self.album_page.playRequested.connect(self.playRequested.emit)
        for page in (self.album_page, self.artist_page, self.favorites_page):
            page.queueRequested.connect(self.queueRequested.emit)

    # same small API the other sections have
    def set_items(self, items):
        self.library.set_items(items)

    def set_inches(self, inches):
        self.library.set_inches(inches)
        self.artist_grid_page.albums.set_inches(inches)

    def set_ignore_articles(self, ignore):
        self.library.set_ignore_articles(ignore)
        self.artist_grid_page.albums.set_ignore_articles(ignore)

    def set_theme(self, name):
        """'browse' (big picture and rows) or 'grid' (cover on the left, albums in a grid)."""
        self._theme = "grid" if name == "grid" else "browse"

    def show_library(self):
        self.setCurrentWidget(self.library)

    def reload(self):
        """Re-read the artist list from the library (new covers, new scan) and show it."""
        conn = db.connect()
        try:
            self.library.set_items(db.music_artist_items(conn))
        finally:
            conn.close()
        self.show_library()

    # ---- navigation -------------------------------------------------------

    def open_artist(self, item):
        if "id" not in item:      # demo placeholders have no library entry
            return
        conn = db.connect()
        try:
            artist = db.get_artist(conn, item["id"])
            if artist is None:
                return
            album_items = []
            for album in db.albums_for_artist(conn, artist["id"]):
                key = db.album_note_key(artist["name"], album["title"])
                album_items.append({
                    "id": album["id"], "title": album["title"], "cover_path": album["cover"],
                    "blurb": db.get_note(conn, "album", key), "tracks": album["n"],
                    "year": (album["year"] or "")[:4],
                })
            note = db.get_note(conn, "artist", db.artist_note_key(artist["name"]))
            hearts = {r["path"] for r in db.favorite_tracks(conn)}
            songs = db.tracks_for_artist(conn, artist["id"])
            songs = [t for t in songs if t["path"] in hearts] + [t for t in songs if t["path"] not in hearts]
        finally:
            conn.close()
        self._artist_id = artist["id"]
        if self._theme == "grid":
            self.artist_grid_page.show_artist(artist, album_items, note)
            self.setCurrentWidget(self.artist_grid_page)
        else:
            self.artist_page.show_artist(artist, album_items, note, songs)
            self.setCurrentWidget(self.artist_page)

    def open_playlists(self):
        self.playlists_page.reload()
        self.setCurrentWidget(self.playlists_page)

    def open_playlist(self, playlist):
        self.playlist_page.show_playlist(playlist)
        self.setCurrentWidget(self.playlist_page)

    def open_favorites(self):
        self.favorites_page.reload()
        self.setCurrentWidget(self.favorites_page)

    def _back_to_artist(self):
        # reopen so any description you just edited shows under the album cover
        if self._artist_id is not None:
            self.open_artist({"id": self._artist_id})

    def open_album(self, item):
        conn = db.connect()
        try:
            album = db.get_album(conn, item["id"])
            if album is None:
                return
            tracks = db.tracks_for_album(conn, album["id"])
            note = db.get_note(conn, "album", db.album_note_key(album["artist"], album["title"]))
        finally:
            conn.close()
        self.album_page.show_album(album, tracks, note)
        self.setCurrentWidget(self.album_page)

    # ---- player feedback --------------------------------------------------

    def track_changed(self, track):
        self.album_page.mark_playing(track["path"])
        self.favorites_page.mark_playing(track["path"])
        self.artist_page.mark_playing(track["path"])
        self.playlist_page.mark_playing(track["path"])

    def state_changed(self, state):
        self.album_page.set_play_state(state)
        self.favorites_page.set_play_state(state)
        self.artist_page.set_play_state(state)
        self.playlist_page.set_play_state(state)
