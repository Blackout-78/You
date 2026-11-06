"""Finds and reads lyrics: .lrc and .txt files next to a song, or lyrics embedded in the file."""
import os
import re
from dataclasses import dataclass

from mutagen import File as MutagenFile

_TIME = re.compile(r"\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]")
_META = re.compile(r"^\s*\[([A-Za-z#]+):([^\]]*)\]\s*$")        # [ar:Artist] [ti:Title] [offset:500]
_WORD = re.compile(r"<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>")        # word-by-word timing, ignored


@dataclass
class Lyrics:
    synced: bool
    lines: list      # [(milliseconds or None, text)]
    source: str      # "lrc", "txt" or "embedded"


def read_text(path):
    """Read a lyrics file whatever its encoding (UTF-8 with or without BOM, UTF-16, older Windows)."""
    with open(path, "rb") as handle:
        data = handle.read()
    if data[:2] in (b"\xff\xfe", b"\xfe\xff"):
        return data.decode("utf-16", errors="replace")
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError:
        return data.decode("cp1252", errors="replace")


def parse_lrc(text):
    """Timed lines sorted by time, or None if this isn't a timed lyrics file."""
    offset = 0
    found = []
    for raw in text.splitlines():
        line = raw.strip("\ufeff \t\r")
        meta = _META.match(line)
        if meta:
            if meta.group(1).lower() == "offset":
                try:
                    offset = int(meta.group(2).strip())
                except ValueError:
                    pass
            continue
        stamps = _TIME.findall(line)
        if not stamps:
            continue
        words = _WORD.sub("", _TIME.sub("", line)).strip()
        for minutes, seconds, fraction in stamps:
            ms = int(minutes) * 60000 + int(seconds) * 1000 + int((fraction or "0").ljust(3, "0")[:3])
            found.append((ms, words))
    if len(found) < 2:
        return None
    found = [(max(0, ms - offset), words) for ms, words in found]   # positive offset = sooner
    found.sort(key=lambda pair: pair[0])
    return found


def parse_plain(text):
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n")]
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return [(None, line) for line in lines]


def _embedded(path):
    """(plain or timed text, synced lines) stored inside the audio file's tags."""
    try:
        audio = MutagenFile(path)
    except Exception:
        return "", None
    tags = getattr(audio, "tags", None) if audio is not None else None
    if tags is None:
        return "", None

    text, synced = "", None
    try:                                                         # MP3
        for frame in tags.getall("SYLT"):
            if getattr(frame, "format", 2) == 2 and frame.text:
                synced = sorted(((int(ms), t.strip()) for t, ms in frame.text), key=lambda p: p[0])
                break
        plain = tags.getall("USLT")
        if plain:
            text = str(plain[0].text)
    except Exception:
        pass
    if not text:                                                 # M4A, FLAC, Ogg
        for key in ("\xa9lyr", "LYRICS", "lyrics", "UNSYNCEDLYRICS", "unsyncedlyrics"):
            try:
                value = tags.get(key)
            except Exception:
                value = None
            if value:
                text = str(value[0] if isinstance(value, (list, tuple)) else value)
                break
    return text, synced


def load_lyrics(track):
    """Priority: .lrc file, timed embedded lyrics, .txt file, plain embedded lyrics."""
    sidecar = track.get("lyrics_path")
    file_text = ""
    if sidecar and os.path.exists(sidecar):
        try:
            file_text = read_text(sidecar)
        except OSError:
            file_text = ""
        if sidecar.lower().endswith(".lrc"):
            lines = parse_lrc(file_text)
            if lines:
                return Lyrics(True, lines, "lrc")

    embedded_text, synced = _embedded(track["path"])
    if synced:
        return Lyrics(True, synced, "embedded")
    if embedded_text:
        lines = parse_lrc(embedded_text)
        if lines:
            return Lyrics(True, lines, "embedded")

    if file_text.strip():
        return Lyrics(False, parse_plain(file_text), "txt")
    if embedded_text.strip():
        return Lyrics(False, parse_plain(embedded_text), "embedded")
    return None
