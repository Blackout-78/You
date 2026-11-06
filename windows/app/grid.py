import math

from PySide6.QtCore import QRectF, QSize, Qt, Signal
from PySide6.QtGui import QColor, QFont, QFontMetrics, QPainter, QPen, QPixmap
from PySide6.QtWidgets import QAbstractScrollArea, QFrame

from .look import C
from .util import sort_key

COLUMNS = 4
INCH = 96            # logical pixels per inch (Qt scales this with Windows display scaling)
MIN_COVER = 120      # covers never get narrower than this; the spacing shrinks first
LABEL_GAP = 10       # space between a cover and its label
LABEL_HEIGHT = 24
MIN_ROWS = 2         # an empty grid still shows this many rows of lines



def wrap_lines(text, metrics, width, max_lines):
    """Break text into at most max_lines lines that fit width, ending with an ellipsis if cut."""
    words = text.split()
    lines, current, i = [], "", 0
    truncated = False
    while i < len(words):
        trial = f"{current} {words[i]}".strip()
        if not current or metrics.horizontalAdvance(trial) <= width:
            current = trial
            i += 1
        else:
            lines.append(current)
            current = ""
            if len(lines) == max_lines:
                truncated = True
                break
    if current and len(lines) < max_lines:
        lines.append(current)
    if truncated and lines:
        lines[-1] = metrics.elidedText(lines[-1] + " \u2026", Qt.TextElideMode.ElideRight, width)
    return lines


class MediaGrid(QAbstractScrollArea):
    """Four covers per row, A to Z, dark gray lines, an inch around every cover and label.

    Items are dicts: {"title": str, "cover_path": str (optional), "pixmap": QPixmap (optional),
    "color": QColor (optional)}.
    Everything is painted by hand so thousands of items scroll smoothly.
    """

    itemActivated = Signal(object)

    def __init__(self, aspect=1.0, empty_title="Nothing here yet", empty_hint="", columns=COLUMNS,
                 blurb_lines=0, sort_items=True, embedded=False, parent=None):
        super().__init__(parent)
        self.viewport().setAutoFillBackground(False)
        self.embedded = embedded          # True: sits inside a scrolling page, so it grows to fit instead of scrolling
        self.sort_items = sort_items      # False keeps the order given (seasons: 1, 2, 10)
        self.columns = columns            # covers per row
        self.blurb_lines = blurb_lines    # lines of description shown under each title (0 = none)
        self.aspect = aspect              # cover height / width: 1.0 for albums, 1.5 for posters
        self.empty_title = empty_title
        self.empty_hint = empty_hint
        self._items = []
        self._inches = 1.0
        self._ignore_articles = True
        self._hover = -1
        self._source_cache = {}   # path -> full-size QPixmap
        self._scaled_cache = {}   # (key, w, h, dpr) -> cropped QPixmap

        self.setFrameShape(QFrame.Shape.NoFrame)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setVerticalScrollBarPolicy(
            Qt.ScrollBarPolicy.ScrollBarAlwaysOff if embedded else Qt.ScrollBarPolicy.ScrollBarAlwaysOn
        )
        self.verticalScrollBar().setSingleStep(40)
        self.viewport().setMouseTracking(True)

    # ---- public API -------------------------------------------------------

    def set_items(self, items):
        self._items = list(items)
        if self.sort_items:
            self._items.sort(key=lambda it: sort_key(it["title"], self._ignore_articles))
        self._hover = -1
        self._scaled_cache.clear()
        self._refresh()

    def set_inches(self, inches):
        self._inches = float(inches)
        self._refresh()

    def set_ignore_articles(self, ignore):
        self._ignore_articles = bool(ignore)
        if self.sort_items:
            self._items.sort(key=lambda it: sort_key(it["title"], self._ignore_articles))
        self._refresh()

    def item_count(self):
        return len(self._items)

    # ---- geometry ---------------------------------------------------------

    def _label_height(self):
        if not self.blurb_lines:
            return LABEL_HEIGHT
        return LABEL_HEIGHT + 4 + self.blurb_lines * self.viewport().fontMetrics().lineSpacing()

    def _metrics(self):
        width = self.viewport().width()
        cell_w = width / self.columns
        gap = min(self._inches * INCH, max(12.0, (cell_w - MIN_COVER) / 2))
        cover_w = max(20.0, cell_w - 2 * gap)
        cover_h = cover_w * self.aspect
        row_h = gap + cover_h + LABEL_GAP + self._label_height() + gap
        return cell_w, gap, cover_w, cover_h, row_h

    def _row_count(self):
        return max(MIN_ROWS, math.ceil(len(self._items) / self.columns))

    def _refresh(self):
        row_h = self._metrics()[4]
        content_h = self._row_count() * row_h + 1
        if self.embedded:
            wanted = int(content_h) + 1
            if self.height() != wanted:
                self.setFixedHeight(wanted)
            self.viewport().update()
            return
        view_h = self.viewport().height()
        bar = self.verticalScrollBar()
        bar.setRange(0, max(0, int(content_h - view_h)))
        bar.setPageStep(view_h)
        self.viewport().update()

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._refresh()

    def scrollContentsBy(self, dx, dy):
        self.viewport().update()

    # ---- painting ---------------------------------------------------------

    def paintEvent(self, event):
        painter = QPainter(self.viewport())
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
        view_w = self.viewport().width()
        view_h = self.viewport().height()
        if not C.picture:
            painter.fillRect(0, 0, view_w, view_h, C.dark)

        cell_w, gap, cover_w, cover_h, row_h = self._metrics()
        offset = self.verticalScrollBar().value()
        rows = self._row_count()
        first = max(0, int(offset // row_h))
        last = min(rows - 1, int((offset + view_h) // row_h))

        # grid lines
        painter.setPen(QPen(C.line, 1))
        bottom = min(view_h, round(rows * row_h - offset))
        for col in range(self.columns + 1):
            x = min(round(col * cell_w), view_w - 1)
            painter.drawLine(x, 0, x, bottom)
        for row in range(first, min(rows, last + 1) + 1):
            y = round(row * row_h - offset)
            painter.drawLine(0, y, view_w, y)

        if not self._items:
            self._paint_empty(painter, view_w, row_h, offset)
            return

        metrics = QFontMetrics(painter.font())
        start = first * self.columns
        stop = min(len(self._items), (last + 1) * self.columns)
        for i in range(start, stop):
            row, col = divmod(i, self.columns)
            item = self._items[i]
            hovered = i == self._hover
            x = col * cell_w + gap
            y = row * row_h - offset + gap
            cover = QRectF(x, y, cover_w, cover_h)
            self._paint_cover(painter, cover, item, hovered)

            label = QRectF(x, y + cover_h + LABEL_GAP, cover_w, LABEL_HEIGHT)
            text = metrics.elidedText(
                item["title"], Qt.TextElideMode.ElideRight, int(label.width())
            )
            painter.setPen(C.light if hovered else C.soft)
            painter.drawText(label, Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignTop, text)

            if self.blurb_lines and item.get("blurb"):
                lines = wrap_lines(item["blurb"], metrics, int(cover_w), self.blurb_lines)
                line_h = metrics.lineSpacing()
                painter.setPen(C.middle)
                top = label.top() + LABEL_HEIGHT + 4
                for n, line in enumerate(lines):
                    painter.drawText(
                        QRectF(x, top + n * line_h, cover_w, line_h),
                        Qt.AlignmentFlag.AlignHCenter, line,
                    )

    def _source_pixmap(self, item):
        pixmap = item.get("pixmap")
        if pixmap is not None and not pixmap.isNull():
            return pixmap, id(item)
        path = item.get("cover_path")
        if not path:
            return None, None
        pixmap = self._source_cache.get(path)
        if pixmap is None:
            if len(self._source_cache) > 300:
                self._source_cache.clear()
            pixmap = QPixmap(path)
            self._source_cache[path] = pixmap
        return (None, None) if pixmap.isNull() else (pixmap, path)

    def _fitted(self, pixmap, key, width, height):
        """The cover scaled to fill the box, centered and cropped, sharp on any display scaling."""
        dpr = self.devicePixelRatioF()
        cache_key = (key, width, height, dpr)
        fitted = self._scaled_cache.get(cache_key)
        if fitted is None:
            target_w, target_h = max(1, int(width * dpr)), max(1, int(height * dpr))
            scaled = pixmap.scaled(
                QSize(target_w, target_h),
                Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                Qt.TransformationMode.SmoothTransformation,
            )
            left = (scaled.width() - target_w) // 2
            top = (scaled.height() - target_h) // 2
            fitted = scaled.copy(left, top, target_w, target_h)
            fitted.setDevicePixelRatio(dpr)
            if len(self._scaled_cache) > 300:
                self._scaled_cache.clear()
            self._scaled_cache[cache_key] = fitted
        return fitted

    def _paint_cover(self, painter, rect, item, hovered):
        pixmap, key = self._source_pixmap(item)
        if pixmap is not None:
            fitted = self._fitted(pixmap, key, int(rect.width()), int(rect.height()))
            painter.drawPixmap(int(rect.x()), int(rect.y()), fitted)
        else:
            painter.fillRect(rect, item.get("color") or C.placeholder)
            if not item.get("color"):
                # no cover yet: show the title on the tile
                metrics = painter.fontMetrics()
                inner = max(20, int(rect.width()) - 24)
                lines = wrap_lines(item["title"], metrics, inner, 6)
                line_h = metrics.lineSpacing()
                top = rect.center().y() - len(lines) * line_h / 2
                painter.setPen(C.middle)
                for n, line in enumerate(lines):
                    painter.drawText(
                        QRectF(rect.x() + 12, top + n * line_h, inner, line_h),
                        Qt.AlignmentFlag.AlignHCenter, line,
                    )
        if hovered:
            painter.setPen(QPen(C.light, 1))
            painter.drawRect(rect.adjusted(0.5, 0.5, -0.5, -0.5))

    def _paint_empty(self, painter, view_w, row_h, offset):
        title_font = QFont(painter.font())
        title_font.setPointSize(15)
        title_metrics = QFontMetrics(title_font)
        hint_metrics = QFontMetrics(painter.font())

        width = max(
            title_metrics.horizontalAdvance(self.empty_title),
            hint_metrics.horizontalAdvance(self.empty_hint),
        ) + 64
        height = title_metrics.height() + hint_metrics.height() + 40
        box = QRectF((view_w - width) / 2, row_h / 2 - height / 2 - offset, width, height)
        painter.fillRect(box, C.dark)

        title_rect = QRectF(box.left(), box.top() + 16, box.width(), title_metrics.height())
        hint_rect = QRectF(
            box.left(), title_rect.bottom() + 8, box.width(), hint_metrics.height()
        )
        painter.setFont(title_font)
        painter.setPen(C.light)
        painter.drawText(title_rect, Qt.AlignmentFlag.AlignHCenter, self.empty_title)
        painter.setFont(QFont(painter.font().family(), 10))
        painter.setPen(C.middle)
        painter.drawText(hint_rect, Qt.AlignmentFlag.AlignHCenter, self.empty_hint)

    # ---- mouse ------------------------------------------------------------

    def _index_at(self, pos):
        cell_w, gap, cover_w, cover_h, row_h = self._metrics()
        py = pos.y() + self.verticalScrollBar().value()
        col = int(pos.x() // cell_w)
        row = int(py // row_h)
        if not 0 <= col < self.columns:
            return -1
        left = col * cell_w + gap
        top = row * row_h + gap
        inside = (
            left <= pos.x() <= left + cover_w
            and top <= py <= top + cover_h + LABEL_GAP + self._label_height()
        )
        index = row * self.columns + col
        return index if inside and 0 <= index < len(self._items) else -1

    def mouseMoveEvent(self, event):
        index = self._index_at(event.position().toPoint())
        if index != self._hover:
            self._hover = index
            cursor = Qt.CursorShape.PointingHandCursor if index >= 0 else Qt.CursorShape.ArrowCursor
            self.viewport().setCursor(cursor)
            self.viewport().setToolTip(self._items[index]["title"] if index >= 0 else "")
            self.viewport().update()

    def leaveEvent(self, event):
        super().leaveEvent(event)
        if self._hover != -1:
            self._hover = -1
            self.viewport().update()

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            index = self._index_at(event.position().toPoint())
            if index >= 0:
                self.itemActivated.emit(self._items[index])
