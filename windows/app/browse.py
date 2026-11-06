"""The browse look: a big picture at the top, sort chips, rows of tiles and a poster grid.

Built only from ordinary widgets (labels, buttons, scroll areas) so it stays safe and simple.
Everything is black and white; only the pictures have color.
"""
import random

from PySide6.QtCore import QSize, Qt, QTimer, Signal
from PySide6.QtGui import QColor, QFontMetrics, QPixmap
from PySide6.QtWidgets import (
    QButtonGroup, QFrame, QHBoxLayout, QLabel, QPushButton, QScrollArea, QVBoxLayout, QWidget,
)

from .look import C
from .grid import MediaGrid
from .util import sort_key

HERO_HEIGHT = 380
TILE_W, TILE_H = 260, 146          # wide tiles (continue watching, collections)
SQUARE = 170                       # square tiles (albums)

SORTS = (("added", "Recently added"), ("title", "Title A–Z"), ("oldest", "Oldest"), ("shuffle", "Shuffle"))

_source_cache = {}


def _source(path):
    pixmap = _source_cache.get(path)
    if pixmap is None:
        if len(_source_cache) > 200:
            _source_cache.clear()
        pixmap = QPixmap(path) if path else QPixmap()
        _source_cache[path] = pixmap
    return pixmap


def fitted_pixmap(path, width, height, dpr=1.0):
    """The picture scaled to fill width x height, centered and cropped. A dark tile if there is none."""
    px_w, px_h = max(1, int(width * dpr)), max(1, int(height * dpr))
    source = _source(path)
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


def length_label(seconds):
    minutes = max(1, int(seconds // 60))
    return f"{minutes // 60}h {minutes % 60}m" if minutes >= 60 else f"{minutes}m"


def _button(text, name):
    button = QPushButton(text)
    button.setObjectName(name)
    button.setCursor(Qt.CursorShape.PointingHandCursor)
    button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
    return button


# ---- the big picture ---------------------------------------------------------------

class HeroBanner(QFrame):
    primaryClicked = Signal()
    secondaryClicked = Signal()

    def __init__(self, primary="Play", secondary="More info"):
        super().__init__()
        self.setObjectName("hero")
        self.setFixedHeight(HERO_HEIGHT)
        self._path = None
        self._art = QLabel(self)
        self._art.setObjectName("heroArt")
        self._shade = QWidget(self)
        self._shade.setObjectName("heroShade")
        self._content = QWidget(self)
        self._content.setObjectName("heroContent")

        lay = QVBoxLayout(self._content)
        lay.setContentsMargins(32, 0, 32, 28)
        lay.setSpacing(6)
        lay.addStretch(1)
        self.title = QLabel()
        self.title.setObjectName("heroTitle")
        self.title.setWordWrap(True)
        self.meta = QLabel()
        self.meta.setObjectName("heroMeta")
        self.text = QLabel()
        self.text.setObjectName("heroText")
        self.text.setWordWrap(True)
        self.text.setMaximumHeight(60)
        lay.addWidget(self.title)
        lay.addWidget(self.meta)
        lay.addWidget(self.text)
        lay.addSpacing(10)
        buttons = QHBoxLayout()
        buttons.setSpacing(10)
        self.primary = _button(primary, "heroPlay")
        self.secondary = _button(secondary, "heroInfo")
        buttons.addWidget(self.primary)
        buttons.addWidget(self.secondary)
        buttons.addStretch(1)
        lay.addLayout(buttons)
        self.primary.clicked.connect(self.primaryClicked.emit)
        self.secondary.clicked.connect(self.secondaryClicked.emit)

        self._redraw = QTimer(self)
        self._redraw.setSingleShot(True)
        self._redraw.setInterval(60)
        self._redraw.timeout.connect(self._paint_art)

    def set_content(self, title, meta, text, cover_path):
        self.title.setText(title)
        self.meta.setText(meta)
        self.text.setText(text or "")
        self.text.setVisible(bool(text))
        self._path = cover_path
        self._paint_art()

    def _paint_art(self):
        if self.width() < 10:
            return
        self._art.setPixmap(fitted_pixmap(self._path, self.width(), self.height(), self.devicePixelRatioF()))

    def resizeEvent(self, event):
        super().resizeEvent(event)
        for child in (self._art, self._shade, self._content):
            child.setGeometry(0, 0, self.width(), self.height())
        self._redraw.start()


# ---- sort chips --------------------------------------------------------------------

class SortChips(QWidget):
    changed = Signal(str)

    def __init__(self):
        super().__init__()
        lay = QHBoxLayout(self)
        lay.setContentsMargins(32, 18, 32, 6)
        lay.setSpacing(8)
        label = QLabel("SORT")
        label.setObjectName("muted")
        lay.addWidget(label)
        lay.addSpacing(8)
        self._group = QButtonGroup(self)
        self._group.setExclusive(True)
        for key, text in SORTS:
            button = _button(text, "chip")
            button.setCheckable(True)
            self._group.addButton(button)
            button.clicked.connect(lambda _=False, k=key: self.changed.emit(k))
            if key == "added":
                button.setChecked(True)
            lay.addWidget(button)
        lay.addStretch(1)


# ---- tiles -------------------------------------------------------------------------

class _Clickable(QFrame):
    clicked = Signal()

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and self.rect().contains(event.position().toPoint()):
            self.clicked.emit()
        super().mouseReleaseEvent(event)


class WideTile(_Clickable):
    """A wide picture with the name written over the bottom of it."""

    def __init__(self, title, sub, cover_path, dpr=1.0):
        super().__init__()
        self.setObjectName("tile")
        self.setFixedSize(TILE_W, TILE_H)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        art = QLabel(self)
        art.setObjectName("heroArt")
        art.setGeometry(0, 0, TILE_W, TILE_H)
        art.setPixmap(fitted_pixmap(cover_path, TILE_W, TILE_H, dpr))
        shade = QWidget(self)
        shade.setObjectName("tileShade")
        shade.setGeometry(0, 0, TILE_W, TILE_H)
        metrics = QFontMetrics(self.font())
        name = QLabel(metrics.elidedText(title, Qt.TextElideMode.ElideRight, TILE_W - 28), self)
        name.setObjectName("tileTitle")
        name.setGeometry(14, TILE_H - 50, TILE_W - 28, 22)
        detail = QLabel(sub, self)
        detail.setObjectName("tileSub")
        detail.setGeometry(14, TILE_H - 28, TILE_W - 28, 18)
        self.setToolTip(title)


class SquareTile(_Clickable):
    """A square cover with the name and a small line under it."""

    def __init__(self, title, sub, cover_path, dpr=1.0):
        super().__init__()
        self.setObjectName("plain")
        self.setFixedSize(SQUARE, SQUARE + 52)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        art = QLabel(self)
        art.setObjectName("heroArt")
        art.setGeometry(0, 0, SQUARE, SQUARE)
        art.setPixmap(fitted_pixmap(cover_path, SQUARE, SQUARE, dpr))
        metrics = QFontMetrics(self.font())
        name = QLabel(metrics.elidedText(title, Qt.TextElideMode.ElideRight, SQUARE), self)
        name.setObjectName("tileName")
        name.setGeometry(0, SQUARE + 8, SQUARE, 20)
        detail = QLabel(metrics.elidedText(sub, Qt.TextElideMode.ElideRight, SQUARE), self)
        detail.setObjectName("muted")
        detail.setGeometry(0, SQUARE + 28, SQUARE, 18)
        self.setToolTip(title)


class TileRow(QScrollArea):
    """A row of tiles that scrolls sideways. The mouse wheel is left for the page."""

    def __init__(self, tile_height):
        super().__init__()
        self.setFrameShape(QFrame.Shape.NoFrame)
        self.setWidgetResizable(True)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self.setFixedHeight(tile_height + 26)
        self._inner = QWidget()
        self._lay = QHBoxLayout(self._inner)
        self._lay.setContentsMargins(32, 0, 32, 10)
        self._lay.setSpacing(14)
        self._lay.addStretch(1)
        self.setWidget(self._inner)

    def set_tiles(self, tiles):
        while self._lay.count() > 1:
            item = self._lay.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        for tile in tiles:
            self._lay.insertWidget(self._lay.count() - 1, tile)

    def wheelEvent(self, event):
        event.ignore()


def section_title(text):
    label = QLabel(text)
    label.setObjectName("sectionTitle")
    label.setContentsMargins(32, 22, 32, 10)
    return label


# ---- the page: Movies or TV Shows -----------------------------------------------------

class BrowsePage(QWidget):
    """Title bar, then a scrolling page: hero, sort chips, continue watching, collections, all."""

    itemActivated = Signal(object)        # a poster, a collection or "More info"
    playItem = Signal(object)             # the big Play button
    continueActivated = Signal(object)

    def __init__(self, title, nouns, empty_title, empty_hint, hero_meta, collections_name=None):
        super().__init__()
        self.nouns = nouns
        self._hero_meta = hero_meta           # function(item) -> text under the title
        self._collections_name = collections_name
        self._items = []
        self._continue = []
        self._sort = "added"
        self._seed = 1
        self._ignore_articles = True
        self._featured = None

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        header = QWidget()
        header.setFixedHeight(72)
        row = QHBoxLayout(header)
        row.setContentsMargins(32, 0, 32, 0)
        name = QLabel(title)
        name.setObjectName("pageTitle")
        self.count = QLabel()
        self.count.setObjectName("muted")
        row.addWidget(name)
        row.addStretch(1)
        self.header_row = row
        row.addWidget(self.count)
        root.addWidget(header)
        line = QFrame()
        line.setObjectName("hairline")
        line.setFixedHeight(1)
        root.addWidget(line)

        self.empty = QLabel(f"{empty_title}\n\n{empty_hint}")
        self.empty.setObjectName("muted")
        self.empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        root.addWidget(self.empty, 1)

        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setFrameShape(QFrame.Shape.NoFrame)
        body = QWidget()
        lay = QVBoxLayout(body)
        lay.setContentsMargins(0, 0, 0, 24)
        lay.setSpacing(0)

        self.hero = HeroBanner()
        self.hero.primaryClicked.connect(lambda: self._featured and self.playItem.emit(self._featured))
        self.hero.secondaryClicked.connect(lambda: self._featured and self.itemActivated.emit(self._featured))
        lay.addWidget(self.hero)

        self.chips = SortChips()
        self.chips.changed.connect(self._on_sort)
        lay.addWidget(self.chips)

        self.continue_title = section_title("Continue watching")
        self.continue_row = TileRow(TILE_H)
        lay.addWidget(self.continue_title)
        lay.addWidget(self.continue_row)

        self.collections_title = section_title(collections_name or "")
        self.collections_row = TileRow(TILE_H)
        lay.addWidget(self.collections_title)
        lay.addWidget(self.collections_row)

        self.all_title = section_title(f"All {nouns[1]}")
        lay.addWidget(self.all_title)
        self.grid = MediaGrid(aspect=1.5, empty_title=empty_title, empty_hint=empty_hint,
                              sort_items=False, embedded=True)
        self.grid.itemActivated.connect(self.itemActivated.emit)
        lay.addWidget(self.grid)
        lay.addStretch(1)
        self.scroll.setWidget(body)
        root.addWidget(self.scroll, 1)
        self._refresh()

    # ---- public API (the same small API the other pages have) -------------------

    def set_items(self, items):
        self._items = list(items)
        self._refresh()

    def set_continue(self, entries):
        self._continue = list(entries)
        self._refresh()

    def set_inches(self, inches):
        self.grid.set_inches(inches)

    def set_ignore_articles(self, ignore):
        self._ignore_articles = bool(ignore)
        self._refresh()

    def add_header_button(self, button):
        self.header_row.insertWidget(self.header_row.count() - 1, button)
        self.header_row.insertSpacing(self.header_row.count() - 1, 24)

    # ---- sorting and showing ------------------------------------------------

    def _on_sort(self, key):
        self._sort = key
        if key == "shuffle":
            self._seed += 1
        self._refresh()

    def _sorted(self):
        items = list(self._items)
        if self._sort == "title":
            items.sort(key=lambda it: sort_key(it["title"], self._ignore_articles))
        elif self._sort == "added":
            items.sort(key=lambda it: -(it.get("added") or 0))
        elif self._sort == "oldest":
            items.sort(key=lambda it: it.get("added") or 0)
        else:
            random.Random(self._seed).shuffle(items)
        return items

    def _refresh(self):
        n = len(self._items)
        self.count.setText(f"{n} {self.nouns[0] if n == 1 else self.nouns[1]}")
        self.empty.setVisible(n == 0)
        self.scroll.setVisible(n > 0)
        if n == 0:
            return
        dpr = self.devicePixelRatioF()
        items = self._sorted()
        self._featured = items[0]
        first = items[0]
        self.hero.set_content(first["title"], self._hero_meta(first), first.get("note", ""), first.get("cover_path"))
        self.grid.set_items(items)

        has_continue = bool(self._continue)
        self.continue_title.setVisible(has_continue)
        self.continue_row.setVisible(has_continue)
        tiles = []
        for entry in self._continue:
            tile = WideTile(entry["title"], f"Resume at {_clock(entry['position_ms'] / 1000)}", entry.get("cover_path"), dpr)
            tile.clicked.connect(lambda e=entry: self.continueActivated.emit(e))
            tiles.append(tile)
        self.continue_row.set_tiles(tiles)

        collections = [it for it in items if self._collections_name and it.get("count", 0) >= 2]
        self.collections_title.setVisible(bool(collections))
        self.collections_row.setVisible(bool(collections))
        tiles = []
        for it in collections:
            tile = WideTile(it["title"], f"{it['count']} films", it.get("cover_path"), dpr)
            tile.clicked.connect(lambda i=it: self.itemActivated.emit(i))
            tiles.append(tile)
        self.collections_row.set_tiles(tiles)


def _clock(seconds):
    seconds = int(seconds)
    hours, rest = divmod(seconds, 3600)
    minutes, secs = divmod(rest, 60)
    return f"{hours}:{minutes:02d}:{secs:02d}" if hours else f"{minutes}:{secs:02d}"
