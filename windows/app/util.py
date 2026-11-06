import sys
import unicodedata
from pathlib import Path

_ARTICLES = ("the ", "a ", "an ")
_LEADING_JUNK = " \"'([{<.!-_"


def resource_path(relative: str) -> str:
    """Find a bundled file, both when running from source and from the packaged .exe."""
    bundle = getattr(sys, "_MEIPASS", None)
    root = Path(bundle) if bundle else Path(__file__).resolve().parent.parent
    return str(root / relative)


def sort_key(title: str, ignore_articles: bool = True) -> str:
    """A-to-Z sort key. Ignores case, accents, leading punctuation and (optionally) The/A/An."""
    text = title.strip().casefold()
    if ignore_articles:
        for article in _ARTICLES:
            if text.startswith(article) and len(text) > len(article):
                text = text[len(article):]
                break
    text = text.lstrip(_LEADING_JUNK)
    return unicodedata.normalize("NFKD", text)
