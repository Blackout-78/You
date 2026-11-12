"""The program's look, written as a style sheet from one "look" (see look.py)."""
from . import look as looks


def make_stylesheet(c):
    """c is a look.Colors. With a background picture, plain areas become see-through so the picture shows."""
    DS = c.dark.name()                       # the solid dark tone
    pic = bool(c.picture)
    D = "transparent" if pic else DS         # plain backgrounds
    L = c.light.name()
    M = c.middle.name()
    S = c.soft.name()
    LN = c.line.name()
    LN2 = c.faint.name()
    DIS = c.disabled.name()
    HV = c.hover.name()
    a = 0.65 if pic else 1.0
    P2 = looks.rgba(c.panel, a)
    P1 = looks.rgba(c.panel_low, a)
    P0 = looks.rgba(c.panel_lower, a)
    G0 = looks.rgba(c.dark, 0)
    G120 = looks.rgba(c.dark, 0.47)
    G235 = looks.rgba(c.dark, 0.92)
    G255 = looks.rgba(c.dark, 1)
    return f"""
QWidget {{
    background: {D};
    color: {L};
    font-family: "Segoe UI";
    font-size: 10pt;
}}
QToolTip {{
    background: {DS};
    color: {L};
    border: 1px solid {LN};
    padding: 4px 8px;
}}

#sidebar {{ background: {D}; }}
#appName {{ font-size: 13pt; }}

QPushButton#nav {{
    text-align: left;
    padding: 12px 22px;
    border: none;
    border-left: 2px solid transparent;
    background: transparent;
    color: {M};
    font-size: 11pt;
}}
QPushButton#nav:hover {{ color: {L}; }}
QPushButton#nav:checked {{ color: {L}; border-left: 2px solid {L}; }}

QFrame#hairline, QFrame#vline {{ background: {LN}; border: none; }}

#pageTitle {{ font-size: 20pt; }}
#trackTitle {{ font-size: 12pt; }}
#sectionTitle {{ font-size: 13pt; }}
#muted {{ color: {M}; }}

QFrame#card {{ border: 1px solid {LN}; background: {D}; }}
QFrame#card QLabel {{ border: none; }}

QScrollArea {{ border: none; }}
QScrollBar:vertical {{ background: {D}; width: 10px; margin: 0; border: none; }}
QScrollBar::handle:vertical {{ background: {LN}; min-height: 40px; border: none; }}
QScrollBar::handle:vertical:hover {{ background: {HV}; }}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{ height: 0; border: none; }}
QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {{ background: none; }}

QPushButton#step {{
    border: 1px solid {LN};
    background: {D};
    padding: 4px 14px;
    font-size: 12pt;
}}
QPushButton#step:hover {{ border-color: {L}; }}

QPushButton#action {{
    border: 1px solid {LN};
    background: {D};
    padding: 8px 18px;
}}
QPushButton#action:hover {{ border-color: {L}; }}
QPushButton#action:disabled {{ color: {DIS}; border-color: {LN2}; }}
QPushButton#link {{
    border: none;
    background: transparent;
    color: {M};
    padding: 4px 8px;
}}
QPushButton#link:hover {{ color: {L}; }}
QPushButton#link:disabled {{ color: {DIS}; }}

QProgressBar {{ background: {P2}; border: none; min-height: 4px; max-height: 4px; }}
QProgressBar::chunk {{ background: {L}; }}

QSlider::groove:horizontal {{ height: 3px; background: {LN}; }}
QSlider::sub-page:horizontal {{ background: {L}; }}
QSlider::add-page:horizontal {{ background: {LN}; }}
QSlider::handle:horizontal {{
    width: 10px; height: 10px; margin: -4px 0; background: {L}; border-radius: 5px;
}}
QSlider::sub-page:horizontal:disabled {{ background: {DIS}; }}
QSlider::handle:horizontal:disabled {{ background: {DIS}; }}

QTreeWidget {{ background: {D}; border: none; outline: 0; }}
QTreeWidget::item {{ padding: 11px 8px; border-bottom: 1px solid {P2}; color: {S}; }}
QTreeWidget::item:hover {{ background: {P1}; }}
QTreeWidget::item:selected {{ background: {P2}; color: {L}; }}

QFrame#lyricsBox {{ background: {P0}; border: 1px solid {LN2}; }}
QListWidget#queueList {{ background: {P0}; border: none; outline: 0; font-size: 10.5pt; }}
QListWidget#queueList::item {{ padding: 4px 8px; border: none; color: {S}; }}
QListWidget#queueList::item:hover {{ background: {P2}; color: {L}; }}
QLabel#npTitle {{ background: transparent; font-size: 20pt; font-weight: 700; }}
QLabel#npKicker {{ background: transparent; color: {M}; font-size: 8pt; letter-spacing: 3px; }}
QListWidget#lyricsList {{ background: {D}; border: none; outline: 0; font-size: 15pt; }}
QListWidget#lyricsList::item {{ padding: 10px 24px; border: none; }}
QListWidget#lyricsList::item:hover {{ background: transparent; }}
QPlainTextEdit#lyricsText {{
    border: none; background: {D}; padding: 24px 48px; font-size: 13pt; color: {S};
}}

QLineEdit {{
    background: {P0}; border: 1px solid {LN}; padding: 8px;
    selection-background-color: {L}; selection-color: {DS};
}}

QPlainTextEdit {{
    background: {P0}; border: 1px solid {LN}; padding: 8px;
    selection-background-color: {L}; selection-color: {DS};
}}

QFrame#hero {{ background: {P0}; }}
QLabel#heroArt {{ background: {P0}; border: none; }}
QWidget#heroShade {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
        stop:0 {G120}, stop:0.4 {G0}, stop:1 {G255});
}}
QWidget#heroContent {{ background: transparent; }}
QLabel#heroTitle {{ background: transparent; font-size: 30pt; font-weight: bold; }}
QLabel#heroMeta {{ background: transparent; color: {S}; font-size: 11pt; }}
QLabel#heroText {{ background: transparent; color: {S}; font-size: 11pt; }}
QPushButton#heroPlay {{
    background: {L}; color: {DS}; border: none; padding: 11px 34px; font-weight: bold;
}}
QPushButton#heroPlay:hover {{ background: {c.light.darker(115).name()}; }}
QPushButton#heroInfo {{
    background: {P2}; color: {L}; border: none; padding: 11px 26px; font-weight: bold;
}}
QPushButton#heroInfo:hover {{ background: {HV}; }}
QPushButton#chip {{
    background: {P2}; color: {S}; border: none; padding: 8px 16px;
}}
QPushButton#chip:hover {{ background: {P2}; }}
QPushButton#chip:checked {{ background: {L}; color: {DS}; font-weight: bold; }}
QFrame#tile {{ background: {P0}; border: none; }}
QWidget#tileShade {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
        stop:0 {G0}, stop:0.35 {G0}, stop:1 {G235});
}}
QLabel#tileTitle {{ background: transparent; font-weight: bold; font-size: 11pt; }}
QLabel#tileSub {{ background: transparent; color: {S}; font-size: 9pt; }}
QFrame#plain {{ background: {D}; border: none; }}
QLabel#tileName {{ background: transparent; font-weight: bold; }}
QScrollBar:horizontal {{ background: {D}; height: 10px; margin: 0; border: none; }}
QScrollBar::handle:horizontal {{ background: {LN}; min-width: 40px; border: none; }}
QScrollBar::handle:horizontal:hover {{ background: {HV}; }}
QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal {{ width: 0; border: none; }}
QScrollBar::add-page:horizontal, QScrollBar::sub-page:horizontal {{ background: none; }}
QMenu {{ background: {DS}; border: 1px solid {LN}; padding: 4px; }}
QMenu::item {{ padding: 8px 24px; color: {S}; }}
QMenu::item:selected {{ background: {P2}; color: {L}; }}
QMenu::separator {{ height: 1px; background: {LN}; margin: 4px 0; }}

QCheckBox {{ spacing: 10px; }}
QCheckBox::indicator {{ width: 16px; height: 16px; border: 1px solid {M}; background: {D}; }}
QCheckBox::indicator:checked {{ background: {L}; border-color: {L}; }}
"""


STYLESHEET = make_stylesheet(looks.C)
