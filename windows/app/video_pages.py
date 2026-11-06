from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QBrush, QColor
from PySide6.QtWidgets import (
    QHBoxLayout, QHeaderView, QLabel, QPushButton, QStackedWidget, QTreeWidget,
    QTreeWidgetItem, QVBoxLayout, QWidget,
)

from .look import C
from . import db
from .grid import MediaGrid
from .music_pages import (
    BlurbBox, CoverBox, TitleDialog, _header, _hline, _left_column, _vline,
    cover_pixmap, format_total, pick_cover,
)
from .browse import BrowsePage, length_label
from .pages import LibraryPage

COVER_W, COVER_H = 300, 450      # posters are tall


def _count_text(n, one, many, total_seconds):
    text = f"{n} {one if n == 1 else many}"
    return f"{text}  ·  {format_total(total_seconds)}" if total_seconds else text


# ---- shared page: cover + description on the left -------------------------------

class DetailBase(QWidget):
    backRequested = Signal()

    def __init__(self):
        super().__init__()
        self._info = None
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        header, self.back, self.title, self.count = _header("←  Back")
        root.addWidget(header)
        root.addWidget(_hline())

        self.cover_box = CoverBox(COVER_W, COVER_H, rename=True)
        self.blurb = BlurbBox()
        self.cover_box.changeRequested.connect(self._choose_cover)
        self.cover_box.resetRequested.connect(self._reset_cover)
        self.cover_box.renameRequested.connect(self._rename)
        self.blurb.changed.connect(self._save_note)

        body = QHBoxLayout()
        body.setSpacing(0)
        body.addWidget(_left_column(self.cover_box, self.blurb, COVER_W + 64))
        body.addWidget(_vline())
        body.addWidget(self._make_right(), 1)
        root.addLayout(body, 1)
        self.back.clicked.connect(self.backRequested.emit)

    def _make_right(self):
        raise NotImplementedError

    def _load(self, info):
        self._info = info
        self.title.setText(info["title"])
        self.back.setText(info["back_text"])
        self.count.setText(info["count_text"])
        self.cover_box.rename_button.setVisible(bool(info.get("can_rename")))
        self.cover_box.set_note("")
        self._show_cover(info["cover"], info["custom_cover"])
        self.blurb.set_text(info["note"])

    def _show_cover(self, path, custom):
        self.cover_box.picture.setPixmap(
            cover_pixmap(path, COVER_W, self.devicePixelRatioF(), COVER_H)
        )
        self.cover_box.set_custom(custom)

    @staticmethod
    def _with_db(action):
        conn = db.connect()
        try:
            return action(conn)
        finally:
            conn.close()

    def _choose_cover(self):
        saved = pick_cover(self, self.cover_box)
        if not saved:
            return
        i = self._info
        self._with_db(lambda c: db.set_custom_cover(c, i["cover_kind"], i["cover_key"], saved))
        i["cover"], i["custom_cover"] = saved, True
        self._show_cover(saved, True)

    def _reset_cover(self):
        i = self._info
        self._with_db(lambda c: db.set_custom_cover(c, i["cover_kind"], i["cover_key"], None))
        i["cover"], i["custom_cover"] = i["scanned_cover"], False
        self._show_cover(i["cover"], False)

    def _rename(self):
        i = self._info
        dialog = TitleDialog(i["title"], self.window())
        if not dialog.exec():
            return
        text = dialog.text()
        self._with_db(lambda c: db.set_title(c, i["title_kind"], i["title_key"], text))
        i["title"] = text or i["scanned_title"]
        i["queue_artist"] = i["title"]
        self.title.setText(i["title"])

    def _save_note(self, text):
        i = self._info
        self._with_db(lambda c: db.set_note(c, i["note_kind"], i["note_key"], text))


# ---- a list of videos you can play: a movie's videos, or a season's episodes ----------

class ItemPage(DetailBase):
    playRequested = Signal(list, int)

    def _make_right(self):
        self._rows = []
        self._labels = []
        self._current_path = None
        self._state = "stopped"

        right = QWidget()
        lay = QVBoxLayout(right)
        lay.setContentsMargins(32, 24, 32, 0)
        lay.setSpacing(14)

        top = QHBoxLayout()
        self.play_all = QPushButton("Play all")
        self.play_all.setObjectName("action")
        self.play_all.setCursor(Qt.CursorShape.PointingHandCursor)
        self.play_all.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.play_all.clicked.connect(lambda: self._play(0))
        hint = QLabel("Click a number, or double-click a title, to play it.")
        hint.setObjectName("muted")
        top.addWidget(self.play_all)
        top.addSpacing(16)
        top.addWidget(hint)
        top.addStretch(1)
        lay.addLayout(top)

        self.tree = QTreeWidget()
        self.tree.setColumnCount(3)
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
        self.tree.setColumnWidth(0, 72)
        self.tree.setColumnWidth(2, 90)
        self.tree.itemClicked.connect(self._clicked)
        self.tree.itemDoubleClicked.connect(self._double_clicked)
        lay.addWidget(self.tree, 1)
        return right

    def show_item(self, info, rows):
        self._load(info)
        self._rows = rows
        self._labels = [r["label"] for r in rows]
        self.tree.clear()
        right = Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        for row in rows:
            length = format_total(row["duration"]) if row["duration"] else ""
            item = QTreeWidgetItem([row["label"], row["title"], length])
            item.setTextAlignment(2, right)
            self.tree.addTopLevelItem(item)
        self._refresh_marks()

    # ---- playing ----------------------------------------------------------

    def _queue(self):
        info = self._info
        return [
            {"path": r["path"], "title": r["title"], "artist": info["queue_artist"],
             "album": info["queue_album"], "duration": r["duration"] or 0,
             "video": True, "lyrics_path": None}
            for r in self._rows
        ]

    def _play(self, index):
        if self._rows:
            self.playRequested.emit(self._queue(), index)

    def _clicked(self, item, column):
        if column == 0:
            self._play(self.tree.indexOfTopLevelItem(item))

    def _double_clicked(self, item, column):
        if column != 0:
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
                item.setText(0, "▶" if self._state == "playing" else "‖")
                for column in range(3):
                    item.setForeground(column, white)
            else:
                item.setText(0, self._labels[i])
                for column in range(3):
                    item.setData(column, Qt.ItemDataRole.ForegroundRole, None)


# ---- a show: seasons on the right -----------------------------------------------------

class ShowPage(DetailBase):
    seasonActivated = Signal(object)

    def _make_right(self):
        self.seasons = MediaGrid(
            aspect=1.5, empty_title="No seasons", empty_hint="",
            columns=3, blurb_lines=3, sort_items=False,
        )
        self.seasons.itemActivated.connect(self.seasonActivated.emit)
        return self.seasons

    def show_show(self, info, season_items):
        self._load(info)
        self.seasons.set_items(season_items)


# ---- the sections ---------------------------------------------------------------------

class MovieSection(QStackedWidget):
    playRequested = Signal(list, int)

    def __init__(self):
        super().__init__()
        self._theme = "browse"
        self.grid_library = LibraryPage(
            "Movies", aspect=1.5, nouns=("movie", "movies"),
            empty_title="No movies yet", empty_hint="Add your movie folder in Settings.",
        )
        self.browse = BrowsePage(
            "Movies", nouns=("movie", "movies"),
            empty_title="No movies yet", empty_hint="Add your movie folder in Settings.",
            hero_meta=lambda m: (
                length_label(m["duration"]) + (f"  \u00b7  {m['count']} videos" if m.get("count", 0) > 1 else "")
                if m.get("duration") else (f"{m['count']} videos" if m.get("count", 0) > 1 else "")
            ),
            collections_name="Collections",
        )
        self.item_page = ItemPage()
        self.addWidget(self.browse)
        self.addWidget(self.grid_library)
        self.addWidget(self.item_page)
        self.browse.itemActivated.connect(self.open_movie)
        self.grid_library.grid.itemActivated.connect(self.open_movie)
        self.browse.playItem.connect(self._play_movie)
        self.browse.continueActivated.connect(self._continue)
        self.item_page.backRequested.connect(self.reload)
        self.item_page.playRequested.connect(self.playRequested.emit)

    @property
    def library(self):
        return self.grid_library if self._theme == "grid" else self.browse

    def set_theme(self, name):
        self._theme = "grid" if name == "grid" else "browse"
        if self.currentWidget() in (self.browse, self.grid_library):
            self.show_library()

    def set_items(self, items):
        self.browse.set_items(items)
        self.grid_library.set_items(items)

    def set_inches(self, inches):
        self.browse.set_inches(inches)
        self.grid_library.set_inches(inches)

    def set_ignore_articles(self, ignore):
        self.browse.set_ignore_articles(ignore)
        self.grid_library.set_ignore_articles(ignore)

    def show_library(self):
        self.setCurrentWidget(self.library)

    def reload(self):
        conn = db.connect()
        try:
            self.set_items(db.movie_browse(conn))
            self.browse.set_continue(db.continue_watching(conn, "movie"))
        finally:
            conn.close()
        self.show_library()

    def _play_movie(self, item):
        """The big Play button: all the movie's videos, from the first."""
        if "id" not in item:
            return
        conn = db.connect()
        try:
            files = db.movie_files(conn, item["id"])
        finally:
            conn.close()
        queue = [
            {"path": f["path"], "title": f["title"], "artist": item["title"], "album": "",
             "duration": f["duration"] or 0, "video": True, "lyrics_path": None}
            for f in files
        ]
        if queue:
            self.playRequested.emit(queue, 0)

    def _continue(self, entry):
        queue = [{"path": entry["path"], "title": entry["title"], "artist": entry["item_title"],
                  "album": "", "duration": entry["duration"], "video": True, "lyrics_path": None}]
        self.playRequested.emit(queue, 0)

    def open_movie(self, item):
        if "id" not in item:
            return
        conn = db.connect()
        try:
            movie = db.get_movie(conn, item["id"])
            if movie is None:
                return
            files = db.movie_files(conn, movie["id"])
            note = db.get_note(conn, "movie", movie["key"])
        finally:
            conn.close()
        rows = [
            {"label": f"{i + 1:02d}", "title": f["title"], "duration": f["duration"], "path": f["path"]}
            for i, f in enumerate(files)
        ]
        total = sum(f["duration"] or 0 for f in files)
        info = {
            "title": movie["title"], "scanned_title": movie["scanned_title"],
            "title_kind": "movie", "title_key": movie["key"], "can_rename": True,
            "cover": movie["cover"], "scanned_cover": movie["scanned_cover"],
            "custom_cover": movie["custom_cover"], "cover_kind": "movie", "cover_key": movie["key"],
            "note": note, "note_kind": "movie", "note_key": movie["key"],
            "back_text": "←  Movies",
            "count_text": _count_text(len(rows), "video", "videos", total),
            "queue_artist": movie["title"], "queue_album": "",
        }
        self.item_page.show_item(info, rows)
        self.setCurrentWidget(self.item_page)

    def track_changed(self, track):
        self.item_page.mark_playing(track["path"])

    def state_changed(self, state):
        self.item_page.set_play_state(state)


class TvSection(QStackedWidget):
    playRequested = Signal(list, int)

    def __init__(self):
        super().__init__()
        self._show_id = None
        self._theme = "browse"
        self.grid_library = LibraryPage(
            "TV Shows", aspect=1.5, nouns=("show", "shows"),
            empty_title="No TV shows yet", empty_hint="Add your TV folder in Settings.",
        )
        self.browse = BrowsePage(
            "TV Shows", nouns=("show", "shows"),
            empty_title="No TV shows yet", empty_hint="Add your TV folder in Settings.",
            hero_meta=lambda s: (
                f"{s.get('seasons', 0)} season{'s' if s.get('seasons', 0) != 1 else ''}"
                f"  \u00b7  {s.get('count', 0)} episodes" if "seasons" in s else ""
            ),
        )
        self.show_page = ShowPage()
        self.season_page = ItemPage()
        for page in (self.browse, self.grid_library, self.show_page, self.season_page):
            self.addWidget(page)
        self.browse.itemActivated.connect(self.open_show)
        self.grid_library.grid.itemActivated.connect(self.open_show)
        self.browse.playItem.connect(self._play_show)
        self.browse.continueActivated.connect(self._continue)
        self.show_page.backRequested.connect(self.reload)
        self.show_page.seasonActivated.connect(self.open_season)
        self.season_page.backRequested.connect(self._back_to_show)
        self.season_page.playRequested.connect(self.playRequested.emit)

    @property
    def library(self):
        return self.grid_library if self._theme == "grid" else self.browse

    def set_theme(self, name):
        self._theme = "grid" if name == "grid" else "browse"
        if self.currentWidget() in (self.browse, self.grid_library):
            self.show_library()

    def set_items(self, items):
        self.browse.set_items(items)
        self.grid_library.set_items(items)

    def set_inches(self, inches):
        self.browse.set_inches(inches)
        self.grid_library.set_inches(inches)
        self.show_page.seasons.set_inches(inches)

    def set_ignore_articles(self, ignore):
        self.browse.set_ignore_articles(ignore)
        self.grid_library.set_ignore_articles(ignore)

    def show_library(self):
        self.setCurrentWidget(self.library)

    def reload(self):
        conn = db.connect()
        try:
            self.set_items(db.show_browse(conn))
            self.browse.set_continue(db.continue_watching(conn, "tv"))
        finally:
            conn.close()
        self.show_library()

    @staticmethod
    def _episode_queue(rows, show_title):
        return [
            {"path": r["path"], "title": r["title"], "artist": show_title, "album": "",
             "duration": r["duration"] or 0, "video": True, "lyrics_path": None}
            for r in rows
        ]

    def _play_show(self, item):
        """The big Play button: every episode in order, starting at the first one you have not finished."""
        if "id" not in item:
            return
        conn = db.connect()
        try:
            rows = []
            for season in db.seasons_for_show(conn, item["id"]):
                rows.extend(db.episodes_for_season(conn, item["id"], season["season"]))
            start = next((i for i, r in enumerate(rows) if db.get_resume(conn, r["path"]) > 0), 0)
        finally:
            conn.close()
        if rows:
            self.playRequested.emit(self._episode_queue(rows, item["title"]), start)

    def _continue(self, entry):
        conn = db.connect()
        try:
            rows = db.episodes_for_season(conn, entry["item_id"], entry["season"])
        finally:
            conn.close()
        index = next((i for i, r in enumerate(rows) if r["path"] == entry["path"]), 0)
        if rows:
            self.playRequested.emit(self._episode_queue(rows, entry["item_title"]), index)

    # ---- navigation -------------------------------------------------------

    def open_show(self, item):
        if "id" not in item:
            return
        conn = db.connect()
        try:
            show = db.get_show(conn, item["id"])
            if show is None:
                return
            seasons = db.seasons_for_show(conn, show["id"])
            mine = db.custom_covers(conn, "season")
            note = db.get_note(conn, "show", show["key"])
            season_items = []
            for s in seasons:
                key = f"{show['key']}||{s['season']}"
                season_items.append({
                    "show_id": show["id"], "season": s["season"], "title": s["title"],
                    "cover_path": mine.get(key, show["cover"]),
                    "blurb": db.get_note(conn, "season", key),
                })
        finally:
            conn.close()
        self._show_id = show["id"]
        n = len(seasons)
        info = {
            "title": show["title"], "scanned_title": show["scanned_title"],
            "title_kind": "show", "title_key": show["key"], "can_rename": True,
            "cover": show["cover"], "scanned_cover": show["scanned_cover"],
            "custom_cover": show["custom_cover"], "cover_kind": "show", "cover_key": show["key"],
            "note": note, "note_kind": "show", "note_key": show["key"],
            "back_text": "←  TV Shows",
            "count_text": f"{n} season{'s' if n != 1 else ''}",
            "queue_artist": show["title"], "queue_album": "",
        }
        self.show_page.show_show(info, season_items)
        self.setCurrentWidget(self.show_page)

    def _back_to_show(self):
        if self._show_id is not None:
            self.open_show({"id": self._show_id})

    def open_season(self, item):
        if "show_id" not in item:
            return
        conn = db.connect()
        try:
            show = db.get_show(conn, item["show_id"])
            if show is None:
                return
            episodes = db.episodes_for_season(conn, show["id"], item["season"])
            key = f"{show['key']}||{item['season']}"
            note = db.get_note(conn, "season", key)
            mine = db.custom_covers(conn, "season").get(key)
        finally:
            conn.close()
        rows = [
            {"label": f"{e['number']:02d}" if e["number"] else "–",
             "title": e["title"], "duration": e["duration"], "path": e["path"]}
            for e in episodes
        ]
        total = sum(e["duration"] or 0 for e in episodes)
        info = {
            "title": item["title"], "scanned_title": item["title"],
            "title_kind": None, "title_key": None, "can_rename": False,
            "cover": mine or show["cover"], "scanned_cover": show["cover"],
            "custom_cover": bool(mine), "cover_kind": "season", "cover_key": key,
            "note": note, "note_kind": "season", "note_key": key,
            "back_text": f"←  {show['title']}",
            "count_text": _count_text(len(rows), "episode", "episodes", total),
            "queue_artist": show["title"], "queue_album": item["title"],
        }
        self.season_page.show_item(info, rows)
        self.setCurrentWidget(self.season_page)

    def track_changed(self, track):
        self.season_page.mark_playing(track["path"])

    def state_changed(self, state):
        self.season_page.set_play_state(state)
