"""Placeholder items so you can preview the layout: python main.py --demo"""
import random

from PySide6.QtGui import QColor

MOVIES = [
    "The Long Harbor", "Ashes of Amber", "Blue Meridian", "A Quiet Storm", "Copper Skies",
    "Dust and Neon", "Echo Valley", "The Fifth Lantern", "Glass Orchard", "Hollow Crown",
    "Iron Tide", "Juniper Road", "Kingfisher", "Last Train North",
]
SHOWS = [
    "The Night Ledger", "Harbor Lights", "Ninth Floor", "A Small Kingdom", "Paper Moons",
    "Quarry Hill", "Red Lantern Row", "Salt and Smoke", "The Tin Season", "Under Glass",
    "Violet Hour", "Winter Tide", "Yellow Door", "Zero Street",
]
ARTISTS = [
    "Aurora Ridge", "The Blue Hours", "Cinder Choir", "Dead Letter Office", "Echo Harbor",
    "Fallow Fields", "Glass Animals Club", "The Hollow Pines", "Ivory Static", "Jasper Row",
    "Kilo Wolves", "A Lost Cartographer", "Midnight Orchard", "North of Nowhere",
]


def _colored(names, seed):
    rng = random.Random(seed)
    return [
        {"title": name, "color": QColor.fromHsv(rng.randrange(360), rng.randrange(90, 200), rng.randrange(80, 170))}
        for name in names
    ]


def demo_items():
    return {
        "movies": _colored(MOVIES, 1),
        "tv": _colored(SHOWS, 2),
        "music": _colored(ARTISTS, 3),
    }
