from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QButtonGroup, QCheckBox, QFileDialog, QFrame, QHBoxLayout, QLabel, QProgressBar, QPushButton,
    QScrollArea, QVBoxLayout, QWidget,
)

from .grid import MediaGrid
from .info import APP_NAME, VERSION
from .personal import ColorsPanel, ProfilePanel

MIN_INCHES = 0.25
MAX_INCHES = 1.5


class LibraryPage(QWidget):
    """Movies, TV Shows and Music all use this: a title bar and the lined grid."""

    def __init__(self, title, aspect, nouns, empty_title, empty_hint):
        super().__init__()
        self.nouns = nouns  # (singular, plural)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

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
        layout.addWidget(header)

        line = QFrame()
        line.setObjectName("hairline")
        line.setFixedHeight(1)
        layout.addWidget(line)

        self.grid = MediaGrid(aspect=aspect, empty_title=empty_title, empty_hint=empty_hint)
        layout.addWidget(self.grid, 1)
        self._update_count()

    def add_header_button(self, button):
        """Put an extra button in the title bar, just left of the count."""
        self.header_row.insertWidget(self.header_row.count() - 1, button)
        self.header_row.insertSpacing(self.header_row.count() - 1, 24)

    def set_items(self, items):
        self.grid.set_items(items)
        self._update_count()

    def set_inches(self, inches):
        self.grid.set_inches(inches)

    def set_ignore_articles(self, ignore):
        self.grid.set_ignore_articles(ignore)

    def _update_count(self):
        n = self.grid.item_count()
        self.count.setText(f"{n} {self.nouns[0] if n == 1 else self.nouns[1]}")


class FolderSection(QWidget):
    """One library: its folders, Add / Rescan / Stop buttons, a progress bar and a status line."""

    folderChosen = Signal(str)
    folderRemoved = Signal(str)
    rescanRequested = Signal()
    stopRequested = Signal()

    def __init__(self, title, note, add_text, rescan_text):
        super().__init__()
        self._busy = False
        self._remove_buttons = []
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(0)

        heading = QLabel(title)
        heading.setObjectName("sectionTitle")
        heading.setContentsMargins(0, 26, 0, 6)
        lay.addWidget(heading)
        explain = QLabel(note)
        explain.setObjectName("muted")
        explain.setWordWrap(True)
        lay.addWidget(explain)
        lay.addSpacing(8)

        self.folder_box = QVBoxLayout()
        self.folder_box.setSpacing(4)
        lay.addLayout(self.folder_box)
        lay.addSpacing(8)

        buttons = QHBoxLayout()
        buttons.setSpacing(10)
        self.add_button = QPushButton(add_text)
        self.rescan_button = QPushButton(rescan_text)
        self.stop_button = QPushButton("Stop")
        for button in (self.add_button, self.rescan_button, self.stop_button):
            button.setObjectName("action")
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
            buttons.addWidget(button)
        buttons.addStretch(1)
        self.stop_button.hide()
        self.add_button.clicked.connect(self._choose)
        self.rescan_button.clicked.connect(self.rescanRequested.emit)
        self.stop_button.clicked.connect(self.stopRequested.emit)
        lay.addLayout(buttons)

        lay.addSpacing(10)
        self.progress = QProgressBar()
        self.progress.setTextVisible(False)
        self.progress.hide()
        lay.addWidget(self.progress)
        self.status = QLabel()
        self.status.setObjectName("muted")
        self.status.setWordWrap(True)
        lay.addWidget(self.status)

    def _choose(self):
        path = QFileDialog.getExistingDirectory(self, "Choose a folder")
        if path:
            self.folderChosen.emit(path)

    def set_folders(self, folders):
        while self.folder_box.count():
            item = self.folder_box.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        self._remove_buttons = []
        for folder in folders:
            row = QWidget()
            row_lay = QHBoxLayout(row)
            row_lay.setContentsMargins(0, 0, 0, 0)
            label = QLabel(folder)
            label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
            remove = QPushButton("Remove")
            remove.setObjectName("link")
            remove.setCursor(Qt.CursorShape.PointingHandCursor)
            remove.setFocusPolicy(Qt.FocusPolicy.NoFocus)
            remove.setEnabled(not self._busy)
            remove.clicked.connect(lambda _=False, f=folder: self.folderRemoved.emit(f))
            row_lay.addWidget(label)
            row_lay.addWidget(remove)
            row_lay.addStretch(1)
            self.folder_box.addWidget(row)
            self._remove_buttons.append(remove)

    def set_busy(self, busy, active):
        """busy: some library is scanning, so changes are off. active: it is this one."""
        self._busy = busy
        self.add_button.setEnabled(not busy)
        self.rescan_button.setEnabled(not busy)
        for button in self._remove_buttons:
            button.setEnabled(not busy)
        self.stop_button.setVisible(active)
        self.progress.setVisible(active)
        if active:
            self.progress.setRange(0, 0)   # moving bar until we know how many files there are
            self.status.setText("Looking for files\u2026")

    def set_progress(self, done, total):
        self.progress.setRange(0, max(1, total))
        self.progress.setValue(done)
        if done >= total:
            self.status.setText("Reading covers and saving your library\u2026")
        else:
            self.status.setText(f"Scanning {done} of {total} files")

    def set_status(self, text):
        self.status.setText(text)


class SettingsPage(QScrollArea):
    gridInchesChanged = Signal(float)
    ignoreArticlesChanged = Signal(bool)
    folderAdded = Signal(str, str)        # kind, folder
    folderRemoved = Signal(str, str)
    rescanRequested = Signal(str)
    stopRequested = Signal()
    themeChanged = Signal(str, str)       # section (music, movies, tv), theme (browse, grid)
    lookChanged = Signal()                # the colors changed
    profileChanged = Signal()             # the picture or name changed

    def __init__(self, settings):
        super().__init__()
        self._inches = float(settings.get("grid_inches"))
        self._active = None
        self.setWidgetResizable(True)
        self.setFrameShape(QFrame.Shape.NoFrame)

        body = QWidget()
        self.setWidget(body)
        lay = QVBoxLayout(body)
        lay.setContentsMargins(40, 28, 40, 40)
        lay.setSpacing(0)

        title = QLabel("Settings")
        title.setObjectName("pageTitle")
        lay.addWidget(title)

        self.profile_panel = ProfilePanel(settings)
        self.profile_panel.profileChanged.connect(self.profileChanged.emit)
        lay.addWidget(self.profile_panel)
        lay.addSpacing(24)
        self.colors_panel = ColorsPanel(settings)
        self.colors_panel.lookChanged.connect(self.lookChanged.emit)
        lay.addWidget(self.colors_panel)
        lay.addSpacing(24)

        lay.addWidget(self._heading("Layout"))
        lay.addWidget(self._note(
            "Space around covers is the distance from each cover and label to the nearest "
            "grid line. On small windows it shrinks so covers stay readable."
        ))
        lay.addSpacing(10)
        stepper = QHBoxLayout()
        stepper.setSpacing(12)
        minus = QPushButton("\u2212")
        plus = QPushButton("+")
        for button in (minus, plus):
            button.setObjectName("step")
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.inch_label = QLabel()
        self.inch_label.setMinimumWidth(70)
        self.inch_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        minus.clicked.connect(lambda: self._bump(-0.25))
        plus.clicked.connect(lambda: self._bump(0.25))
        stepper.addWidget(minus)
        stepper.addWidget(self.inch_label)
        stepper.addWidget(plus)
        stepper.addStretch(1)
        lay.addLayout(stepper)
        self._refresh_inch_label()

        lay.addSpacing(18)
        ignore = QCheckBox("Ignore \u201cThe\u201d, \u201cA\u201d and \u201cAn\u201d when sorting A to Z")
        ignore.setChecked(bool(settings.get("ignore_articles")))
        ignore.toggled.connect(self.ignoreArticlesChanged.emit)
        lay.addWidget(ignore)

        lay.addWidget(self._heading("Theme"))
        lay.addWidget(self._note(
            "Browse has a big picture and rows. Grid is a plain grid of covers. Pick one for each section."
        ))
        lay.addSpacing(10)
        for kind, label in (("music", "Music"), ("movies", "Movies"), ("tv", "TV Shows")):
            row = QHBoxLayout()
            row.setSpacing(8)
            name = QLabel(label)
            name.setMinimumWidth(110)
            row.addWidget(name)
            group = QButtonGroup(self)
            group.setExclusive(True)
            current = settings.get(f"theme_{kind}")
            for theme, text in (("browse", "Browse"), ("grid", "Grid")):
                button = QPushButton(text)
                button.setObjectName("chip")
                button.setCheckable(True)
                button.setCursor(Qt.CursorShape.PointingHandCursor)
                button.setFocusPolicy(Qt.FocusPolicy.NoFocus)
                button.setChecked(current == theme)
                group.addButton(button)
                button.clicked.connect(lambda _=False, k=kind, t=theme: self.themeChanged.emit(k, t))
                row.addWidget(button)
            row.addStretch(1)
            lay.addLayout(row)
            lay.addSpacing(6)

        lay.addWidget(self._heading("Library folders"))
        lay.addWidget(self._note(
            "Choose the folder that holds your items. The app only reads your files and "
            "never changes them."
        ))
        self.sections = {
            "music": FolderSection(
                "Music",
                "The folder that holds your artist folders, for example C:\\Users\\you\\Music",
                "Add music folder\u2026", "Rescan music"),
            "movies": FolderSection(
                "Movies",
                "The folder that holds your movie folders, for example C:\\Users\\you\\Videos\\Movies",
                "Add movies folder\u2026", "Rescan movies"),
            "tv": FolderSection(
                "TV Shows",
                "The folder that holds your show folders, for example C:\\Users\\you\\Videos\\TV",
                "Add TV folder\u2026", "Rescan TV"),
        }
        keys = {"music": "music_folders", "movies": "movie_folders", "tv": "tv_folders"}
        for kind, section in self.sections.items():
            section.set_folders(settings.get(keys[kind]))
            section.folderChosen.connect(lambda path, k=kind: self.folderAdded.emit(k, path))
            section.folderRemoved.connect(lambda path, k=kind: self.folderRemoved.emit(k, path))
            section.rescanRequested.connect(lambda k=kind: self.rescanRequested.emit(k))
            section.stopRequested.connect(self.stopRequested.emit)
            lay.addWidget(section)

        lay.addWidget(self._heading("Privacy"))
        lay.addWidget(self._note(
            "This app doesn't collect any data about you. It has no networking code and never "
            "connects to the internet. Your library, settings and lyrics stay on this PC."
        ))

        lay.addWidget(self._heading("About"))
        lay.addWidget(self._note(f"{APP_NAME} {VERSION}"))
        lay.addStretch(1)

    def _heading(self, text):
        label = QLabel(text)
        label.setObjectName("sectionTitle")
        label.setContentsMargins(0, 34, 0, 8)
        return label

    def _note(self, text):
        label = QLabel(text)
        label.setObjectName("muted")
        label.setWordWrap(True)
        return label

    # ---- libraries --------------------------------------------------------

    def set_folders(self, kind, folders):
        self.sections[kind].set_folders(folders)

    def set_scanning(self, kind):
        """kind is the library being scanned, or None when nothing is."""
        self._active = kind
        for name, section in self.sections.items():
            section.set_busy(kind is not None, name == kind)

    def set_progress(self, done, total, _name):
        if self._active:
            self.sections[self._active].set_progress(done, total)

    def set_status(self, kind, text):
        self.sections[kind].set_status(text)

    # ---- appearance -------------------------------------------------------

    def _bump(self, delta):
        value = round(min(MAX_INCHES, max(MIN_INCHES, self._inches + delta)), 2)
        if value == self._inches:
            return
        self._inches = value
        self._refresh_inch_label()
        self.gridInchesChanged.emit(value)

    def _refresh_inch_label(self):
        self.inch_label.setText(f"{self._inches:.2f} in")
