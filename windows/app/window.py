import os
import sys

from PySide6.QtCore import Qt, QThread
from PySide6.QtGui import QColor, QIcon, QKeySequence, QPainter, QPixmap, QShortcut
from PySide6.QtWidgets import (
    QApplication, QButtonGroup, QFrame, QHBoxLayout, QLabel, QMainWindow, QPushButton,
    QStackedWidget, QVBoxLayout, QWidget,
)

from . import db
from .demo import demo_items
from .info import APP_NAME
from . import look as looks
from .lyricspanel import LyricsPanel
from .music_pages import MusicSection
from .pages import SettingsPage
from .personal import round_pixmap
from .player import Player
from .playerbar import PlayerBar
from .scanner import ScanWorker
from .theme import make_stylesheet
from .util import resource_path
from .video_pages import MovieSection, TvSection
from .videoview import VideoView

FOLDER_KEYS = {"music": "music_folders", "movies": "movie_folders", "tv": "tv_folders"}


def summary(kind, result):
    a, b = result
    if kind == "music":
        return f"Done. {b} tracks by {a} artists."
    if kind == "movies":
        return f"Done. {a} movies, {b} videos."
    return f"Done. {a} shows, {b} episodes."


class Backdrop(QWidget):
    """Paints the background: the dark color, or your picture (dimmed) behind everything."""

    def __init__(self):
        super().__init__()
        self._colors = looks.C
        self._source = QPixmap()
        self._source_path = ""
        self._scaled = QPixmap()
        self._scaled_for = None

    def set_colors(self, colors):
        self._colors = colors
        if colors.picture != self._source_path:
            self._source_path = colors.picture
            self._source = QPixmap(colors.picture) if colors.picture else QPixmap()
            self._scaled_for = None
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.fillRect(self.rect(), self._colors.dark)
        if not self._source.isNull():
            size = self.size()
            if self._scaled_for != (size.width(), size.height()):
                self._scaled = self._source.scaled(
                    size, Qt.AspectRatioMode.KeepAspectRatioByExpanding, Qt.TransformationMode.SmoothTransformation)
                self._scaled_for = (size.width(), size.height())
            x = (self._scaled.width() - size.width()) // 2
            y = (self._scaled.height() - size.height()) // 2
            painter.drawPixmap(0, 0, self._scaled, x, y, size.width(), size.height())
            shade = QColor(self._colors.dark)
            shade.setAlphaF(self._colors.dim_amount)
            painter.fillRect(self.rect(), shade)
        painter.end()


class MainWindow(QMainWindow):
    def __init__(self, settings, demo=False):
        super().__init__()
        self.settings = settings
        self._titlebar_done = False
        self._scan_thread = None
        self._scan_worker = None
        self._scan_kind = None
        self._lyrics_open = False
        self._video_open = False
        self._play_state = "stopped"
        self._current_track = None
        self._last_resume_save = 0

        self.setWindowTitle(APP_NAME)
        self.setWindowIcon(QIcon(resource_path("assets/icon.ico")))
        self.resize(1280, 800)
        self.setMinimumSize(960, 600)

        root = Backdrop()
        self.backdrop = root
        self.setCentralWidget(root)
        row = QHBoxLayout(root)
        row.setContentsMargins(0, 0, 0, 0)
        row.setSpacing(0)

        self.stack = QStackedWidget()
        self.lyrics_panel = LyricsPanel()
        self.video_view = VideoView()
        self.player_bar = PlayerBar()
        self.lyrics_panel.hide()
        self.video_view.hide()

        right = QWidget()
        right_lay = QVBoxLayout(right)
        right_lay.setContentsMargins(0, 0, 0, 0)
        right_lay.setSpacing(0)
        right_lay.addWidget(self.stack, 1)
        right_lay.addWidget(self.lyrics_panel, 1)
        right_lay.addWidget(self.video_view, 1)
        bar_line = QFrame()
        bar_line.setObjectName("hairline")
        bar_line.setFixedHeight(1)
        right_lay.addWidget(bar_line)
        right_lay.addWidget(self.player_bar)

        row.addWidget(self._build_sidebar())
        divider = QFrame()
        divider.setObjectName("vline")
        divider.setFixedWidth(1)
        row.addWidget(divider)
        row.addWidget(right, 1)

        self.movies = MovieSection()
        self.tv = TvSection()
        self.music = MusicSection()
        self.settings_page = SettingsPage(settings)
        for page in (self.movies, self.tv, self.music, self.settings_page):
            self.stack.addWidget(page)

        self.libraries = [self.movies, self.tv, self.music]
        self.sections = {"music": self.music, "movies": self.movies, "tv": self.tv}
        for library in self.libraries:
            library.set_inches(settings.get("grid_inches"))
            library.set_ignore_articles(settings.get("ignore_articles"))

        self._connect_player()

        page = self.settings_page
        page.gridInchesChanged.connect(self._on_inches)
        page.ignoreArticlesChanged.connect(self._on_ignore_articles)
        page.folderAdded.connect(self._add_folder)
        page.folderRemoved.connect(self._remove_folder)
        page.rescanRequested.connect(self._start_scan)
        page.themeChanged.connect(self._on_theme)
        page.lookChanged.connect(self._apply_looks)
        page.profileChanged.connect(self._apply_profile)
        for section_name in self.sections:
            self.sections[section_name].set_theme(settings.get(f"theme_{section_name}"))
        page.stopRequested.connect(self._stop_scan)

        if demo:
            items = demo_items()
            self.movies.set_items(items["movies"])
            self.tv.set_items(items["tv"])
            self.music.set_items(items["music"])
        else:
            for kind in self.sections:
                self._reload(kind)

        self.nav.button(0).setChecked(True)
        self._apply_looks()
        self._apply_profile()

    # ---- your colors and profile -------------------------------------------

    NAV_SECTIONS = {0: "movies", 1: "tv", 2: "music"}

    def _apply_looks(self):
        """Paints the whole program with your colors; a section with its own colors keeps them."""
        global_look = looks.normalize(self.settings.get("look_all"))
        QApplication.instance().setStyleSheet(make_stylesheet(looks.Colors(global_look)))
        for name, section in self.sections.items():
            own = self.settings.get(f"look_{name}")
            section.setStyleSheet(make_stylesheet(looks.Colors(own)) if own else "")
        self._show_look_for(self.stack.currentIndex())

    def _show_look_for(self, index):
        """Painted parts (the grid, buttons, the background picture) follow the page you are on."""
        name = self.NAV_SECTIONS.get(index)
        own = self.settings.get(f"look_{name}") if name else None
        looks.apply(own if own else self.settings.get("look_all"))
        self.backdrop.set_colors(looks.C)
        if self._titlebar_done:
            self._black_titlebar()
        for widget in QApplication.instance().allWidgets():
            widget.update()

    def _apply_profile(self):
        """Your picture and name: the window icon, the logo and the name in the sidebar."""
        path = self.settings.get("profile_picture")
        if path and not os.path.isfile(path):
            path = ""
        icon = QIcon(path) if path else QIcon(resource_path("assets/icon.ico"))
        self.setWindowIcon(icon)
        QApplication.instance().setWindowIcon(icon)
        self.logo.setPixmap(round_pixmap(path, 36))
        self.name_label.setText(self.settings.get("profile_name") or APP_NAME)

    # ---- layout -----------------------------------------------------------

    def _build_sidebar(self):
        side = QWidget()
        side.setObjectName("sidebar")
        side.setFixedWidth(210)
        lay = QVBoxLayout(side)
        lay.setContentsMargins(0, 24, 0, 24)
        lay.setSpacing(0)

        head = QHBoxLayout()
        head.setContentsMargins(22, 0, 22, 28)
        head.setSpacing(12)
        self.logo = QLabel()
        self.logo.setFixedSize(36, 36)
        self.name_label = QLabel(APP_NAME)
        self.name_label.setObjectName("appName")
        head.addWidget(self.logo)
        head.addWidget(self.name_label)
        head.addStretch(1)
        lay.addLayout(head)

        self.nav = QButtonGroup(self)
        self.nav.setExclusive(True)
        for index, label in enumerate(("Movies", "TV Shows", "Music", "Settings")):
            button = QPushButton(label)
            button.setObjectName("nav")
            button.setCheckable(True)
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
            self.nav.addButton(button, index)
            if label == "Settings":
                lay.addStretch(1)
            lay.addWidget(button)
        self.nav.idClicked.connect(self._on_nav)
        return side

    # ---- player -----------------------------------------------------------

    def _connect_player(self):
        self.player = Player(self)
        self.player.set_video_output(self.video_view.video)
        bar = self.player_bar

        for section in self.libraries:
            section.playRequested.connect(self.player.play_queue)
            self.player.trackChanged.connect(section.track_changed)
            self.player.stateChanged.connect(section.state_changed)

        self.music.favoritesChanged.connect(self._refresh_heart)
        self.player.trackChanged.connect(bar.set_track)
        self.player.trackChanged.connect(self._on_track)
        self.player.stateChanged.connect(bar.set_state)
        self.player.stateChanged.connect(self._on_state)
        self.player.positionChanged.connect(self._on_position)
        self.player.resume_provider = self._resume_for
        self.player.positionChanged.connect(bar.set_position)
        self.player.positionChanged.connect(self.lyrics_panel.set_position)
        self.player.durationChanged.connect(bar.set_duration)
        self.player.errorOccurred.connect(bar.show_error)

        bar.toggleClicked.connect(self._toggle_play)
        bar.stopClicked.connect(self._stop)
        bar.nextClicked.connect(self.player.next)
        bar.previousClicked.connect(self.player.previous)
        bar.skipRequested.connect(self.player.skip)
        bar.heartClicked.connect(self._toggle_heart)

        # volume: remembered between runs
        self.player.set_volume(self.settings.get("volume"))
        self.player.set_muted(self.settings.get("muted"))
        bar.set_volume_controls(self.settings.get("volume"), self.settings.get("muted"))
        self.player.set_shuffle(self.settings.get("shuffle"))
        self.player.set_repeat(self.settings.get("repeat"))
        bar.set_shuffle(self.settings.get("shuffle"))
        bar.set_repeat(self.settings.get("repeat"))
        bar.shuffleClicked.connect(self._toggle_shuffle)
        bar.repeatClicked.connect(self._cycle_repeat)
        bar.volumeChanged.connect(self._set_volume)
        bar.muteClicked.connect(self._toggle_mute)

        # keyboard: Space plays or pauses, Left and Right jump 10 seconds
        self._shortcuts = []
        for keys, action in (
            ("Space", self._toggle_play),
            ("Left", lambda: self.player.skip(-10000)),
            ("Right", lambda: self.player.skip(10000)),
            ("M", self._toggle_mute),
        ):
            try:
                shortcut = QShortcut(QKeySequence(keys), self)
                shortcut.activated.connect(action)
                self._shortcuts.append(shortcut)
            except Exception:
                pass          # a missing shortcut must never stop the app from opening
        bar.seekRequested.connect(self.player.seek)
        bar.lyricsClicked.connect(self._toggle_lyrics)

        self.lyrics_panel.seekRequested.connect(self.player.seek)
        self.lyrics_panel.closeRequested.connect(self._close_lyrics)
        self.video_view.backRequested.connect(self._close_video)
        self.video_view.stopRequested.connect(self._stop)
        self.video_view.video.toggleRequested.connect(self._toggle_play)
        self.video_view.video.skipRequested.connect(self.player.skip)

    def _on_state(self, state):
        self._play_state = state
        if state == "paused":
            self._save_resume()

    # ---- where you stopped watching ---------------------------------------

    def _resume_for(self, track):
        conn = db.connect()
        try:
            return db.get_resume(conn, track["path"])
        finally:
            conn.close()

    def _save_resume(self):
        track = self._current_track
        if not track or not track.get("video"):
            return
        conn = db.connect()
        try:
            db.save_resume(conn, track["path"], self.player.position(), self.player.duration())
        finally:
            conn.close()
        self._last_resume_save = self.player.position()

    def _on_position(self, ms):
        track = self._current_track
        if track and track.get("video") and self._play_state == "playing" \
                and abs(ms - self._last_resume_save) >= 5000:
            self._save_resume()

    def _stop(self):
        """Stop: remember where a video was, end playback and leave the picture."""
        self._save_resume()
        self.player.stop()
        if self._video_open:
            self._close_video()

    def _toggle_play(self):
        if self._play_state == "playing":
            self.player.pause()
        else:
            self.player.play()

    def _toggle_shuffle(self):
        on = not self.settings.get("shuffle")
        self.settings.set("shuffle", on)
        self.player.set_shuffle(on)
        self.player_bar.set_shuffle(on)

    def _cycle_repeat(self):
        mode = {"off": "all", "all": "one", "one": "off"}.get(self.settings.get("repeat"), "off")
        self.settings.set("repeat", mode)
        self.player.set_repeat(mode)
        self.player_bar.set_repeat(mode)

    def _set_volume(self, value):
        self.settings.set("volume", int(value))
        self.player.set_volume(value)
        if self.settings.get("muted") and value > 0:
            self._toggle_mute()

    def _toggle_mute(self):
        muted = not self.settings.get("muted")
        self.settings.set("muted", muted)
        self.player.set_muted(muted)
        self.player_bar.set_volume_controls(self.settings.get("volume"), muted)

    def _toggle_heart(self):
        track = self._current_track
        if not track:
            return
        conn = db.connect()
        try:
            self.player_bar.set_favorite(db.toggle_favorite(conn, track))
        finally:
            conn.close()

    def _refresh_heart(self):
        if not self._current_track:
            return
        conn = db.connect()
        try:
            self.player_bar.set_favorite(db.is_favorite(conn, self._current_track["path"]))
        finally:
            conn.close()

    def _on_track(self, track):
        self._save_resume()                  # remember the spot in the video that is being left
        self._current_track = track
        self._last_resume_save = 0
        conn = db.connect()
        try:
            self.player_bar.set_favorite(db.is_favorite(conn, track["path"]))
        finally:
            conn.close()
        if track.get("video"):
            self._show_video(track)
            self.player_bar.set_lyrics_available(False)
        else:
            self.player_bar.set_lyrics_available(self.lyrics_panel.set_track(track))

    def _on_nav(self, index):
        self._close_lyrics()
        self._close_video()
        self.stack.setCurrentIndex(index)
        self._show_look_for(index)

    # ---- lyrics -----------------------------------------------------------

    def _toggle_lyrics(self):
        if self._lyrics_open:
            self._close_lyrics()
        elif not self._video_open:
            self._lyrics_open = True
            self.stack.hide()
            self.lyrics_panel.show()
            self.player_bar.set_lyrics_open(True)

    def _close_lyrics(self):
        if not self._lyrics_open:
            return
        self._lyrics_open = False
        self.lyrics_panel.hide()
        self.stack.show()
        self.player_bar.set_lyrics_open(False)

    # ---- video ------------------------------------------------------------

    def _show_video(self, track):
        self._close_lyrics()
        self.video_view.set_title(track["title"])
        if not self._video_open:
            self._video_open = True
            self.stack.hide()
            self.video_view.show()

    def _close_video(self):
        if not self._video_open:
            return
        self._save_resume()                  # Back also remembers where you stopped
        self._video_open = False
        if self.video_view.video.isFullScreen():
            self.video_view.video.setFullScreen(False)
        self.player.stop()
        self.video_view.hide()
        self.stack.show()

    # ---- libraries: folders and scanning -----------------------------------

    def _reload(self, kind):
        self.sections[kind].reload()

    @staticmethod
    def _same_folder(a, b):
        return os.path.normcase(os.path.normpath(a)) == os.path.normcase(os.path.normpath(b))

    def _add_folder(self, kind, path):
        path = os.path.normpath(path)
        key = FOLDER_KEYS[kind]
        folders = list(self.settings.get(key))
        if any(self._same_folder(path, f) for f in folders):
            self.settings_page.set_status(kind, "That folder is already in the list.")
            return
        folders.append(path)
        self.settings.set(key, folders)
        self.settings_page.set_folders(kind, folders)
        self._start_scan(kind)

    def _remove_folder(self, kind, path):
        key = FOLDER_KEYS[kind]
        folders = [f for f in self.settings.get(key) if not self._same_folder(f, path)]
        self.settings.set(key, folders)
        self.settings_page.set_folders(kind, folders)
        if folders:
            self._start_scan(kind)
            return
        conn = db.connect()
        try:
            {"music": db.clear_music, "movies": db.clear_movies, "tv": db.clear_tv}[kind](conn)
        finally:
            conn.close()
        self._reload(kind)
        self.settings_page.set_status(kind, "Folder removed. This library is now empty.")

    def _start_scan(self, kind):
        if self._scan_thread is not None:
            return
        folders = self.settings.get(FOLDER_KEYS[kind])
        if not folders:
            self.settings_page.set_status(kind, "Add a folder first.")
            return
        missing = [f for f in folders if not os.path.isdir(f)]
        if missing:
            self.settings_page.set_status(
                kind,
                f"Can't find {missing[0]}. Is the drive connected? "
                "Your library has not been changed.",
            )
            return

        self._scan_kind = kind
        self._scan_worker = ScanWorker(folders, kind)
        self._scan_thread = QThread(self)
        self._scan_worker.moveToThread(self._scan_thread)
        self._scan_thread.started.connect(self._scan_worker.run)
        self._scan_worker.progress.connect(self.settings_page.set_progress)
        self._scan_worker.done.connect(self._on_scan_done)
        self._scan_worker.failed.connect(self._on_scan_failed)
        self.settings_page.set_scanning(kind)
        self._scan_thread.start()

    def _stop_scan(self):
        if self._scan_worker is not None:
            self._scan_worker.cancel()
            self.settings_page.set_status(self._scan_kind, "Stopping…")

    def _end_scan(self):
        thread, worker, kind = self._scan_thread, self._scan_worker, self._scan_kind
        self._scan_thread = self._scan_worker = self._scan_kind = None
        if thread is not None:
            thread.quit()
            thread.wait()
            worker.deleteLater()
            thread.deleteLater()
        self.settings_page.set_scanning(None)
        return kind

    def _on_scan_done(self, result):
        kind = self._end_scan()
        if result is None:
            self.settings_page.set_status(kind, "Scan stopped. Your library has not been changed.")
            return
        self._reload(kind)
        self.settings_page.set_status(kind, summary(kind, result))

    def _on_scan_failed(self, message):
        kind = self._end_scan()
        self.settings_page.set_status(
            kind, f"The scan hit a problem and your library has not been changed: {message}"
        )

    def closeEvent(self, event):
        try:
            self._save_resume()
        except Exception:
            pass
        if self._scan_worker is not None:
            self._scan_worker.cancel()
        if self._scan_thread is not None:
            self._scan_thread.quit()
            self._scan_thread.wait(5000)
        super().closeEvent(event)

    # ---- settings wiring --------------------------------------------------

    def _on_inches(self, value):
        self.settings.set("grid_inches", value)
        for library in self.libraries:
            library.set_inches(value)

    def _on_theme(self, kind, theme):
        self.settings.set(f"theme_{kind}", theme)
        self.sections[kind].set_theme(theme)

    def _on_ignore_articles(self, value):
        self.settings.set("ignore_articles", value)
        for library in self.libraries:
            library.set_ignore_articles(value)

    # ---- black title bar on Windows ---------------------------------------

    def showEvent(self, event):
        super().showEvent(event)
        if not self._titlebar_done:
            self._titlebar_done = True
            self._black_titlebar()

    def _black_titlebar(self):
        if sys.platform != "win32":
            return
        try:
            import ctypes

            hwnd = int(self.winId())
            dwm = ctypes.windll.dwmapi

            def set_attr(attr, value):
                data = ctypes.c_int(value)
                return dwm.DwmSetWindowAttribute(hwnd, attr, ctypes.byref(data), ctypes.sizeof(data))

            if set_attr(20, 1) != 0:   # dark title bar (Windows 10 2004+ and 11)
                set_attr(19, 1)        # same switch on older Windows 10 builds

            def bgr(color):            # Windows wants blue-green-red order
                return color.red() | (color.green() << 8) | (color.blue() << 16)

            set_attr(35, bgr(looks.C.dark))    # caption color: your dark tone (Windows 11)
            set_attr(36, bgr(looks.C.light))   # caption text: your light tone (Windows 11)
        except Exception:
            pass  # purely cosmetic; ignore on any failure
