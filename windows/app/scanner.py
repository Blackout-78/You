"""Reads your music folders into the local library. Works fully offline."""
import os
import re
from pathlib import Path

from mutagen import File as MutagenFile
from PySide6.QtCore import QObject, Signal

from . import db
from .covers import save_cover as _save_cover

AUDIO_EXTS = {".mp3", ".m4a", ".mp4", ".flac", ".ogg", ".opus"}
IMAGE_EXTS = (".jpg", ".jpeg", ".png", ".webp")
ALBUM_IMAGE_NAMES = ("cover", "folder", "front", "albumart", "album")
ARTIST_IMAGE_NAMES = ("artist", "band", "folder", "photo")
LYRIC_EXTS = (".lrc", ".txt")

# "1-01 Title", "01 - Title", "05. Title"  ->  (disc, track, title)
_NUMBERED = re.compile(r"^\s*(?:(\d+)\s*[-.]\s*)?(\d{1,3})\s*[-.\s]\s*(.+)$")


# ---- small helpers ----------------------------------------------------------

def _first(tags, key):
    try:
        value = tags.get(key)
    except Exception:
        return ""
    if not value:
        return ""
    if isinstance(value, (list, tuple)):
        value = value[0] if value else ""
    return str(value).strip()


def _int_part(text):
    """'3/12' -> 3, '' -> 0"""
    match = re.match(r"\s*(\d+)", text or "")
    return int(match.group(1)) if match else 0


def parse_filename(stem):
    match = _NUMBERED.match(stem)
    if not match:
        return 0, 0, stem.strip()
    disc, number, title = match.groups()
    return int(disc or 0), int(number), title.strip()


def _place(path, root):
    """Folder-based fallbacks: (artist, album, artist_folder) from Artist/Album/file layout."""
    parts = Path(path).relative_to(root).parts
    if len(parts) >= 3:
        return parts[0], parts[1], str(Path(root) / parts[0])
    if len(parts) == 2:
        return parts[0], "Singles", str(Path(root) / parts[0])
    return "Unknown Artist", Path(root).name or "Unknown album", None


# ---- covers -----------------------------------------------------------------

def _embedded_cover(path):
    try:
        audio = MutagenFile(path)
    except Exception:
        return None
    if audio is None:
        return None
    pictures = getattr(audio, "pictures", None)          # FLAC
    if pictures:
        front = [p for p in pictures if getattr(p, "type", None) == 3]
        return bytes((front or pictures)[0].data)
    tags = getattr(audio, "tags", None)
    if tags is None:
        return None
    try:
        frames = tags.getall("APIC")                      # MP3
    except Exception:
        frames = []
    if frames:
        front = [f for f in frames if getattr(f, "type", None) == 3]
        return bytes((front or frames)[0].data)
    try:
        covr = tags.get("covr")                           # M4A / MP4
    except Exception:
        covr = None
    return bytes(covr[0]) if covr else None


def _folder_image(directory, names):
    try:
        entries = os.listdir(directory)
    except OSError:
        return None
    lowered = {entry.lower(): entry for entry in entries}
    for base in names:
        for ext in IMAGE_EXTS:
            if base + ext in lowered:
                try:
                    return (Path(directory) / lowered[base + ext]).read_bytes()
                except OSError:
                    return None
    return None


# ---- reading one track -------------------------------------------------------

def _read_track(path, root):
    fallback_artist, fallback_album, _ = _place(path, root)
    stem = Path(path).stem
    f_disc, f_number, f_title = parse_filename(stem)

    tags, duration = {}, 0.0
    try:
        audio = MutagenFile(path, easy=True)
        if audio is not None:
            tags = audio.tags or {}
            duration = float(getattr(audio.info, "length", 0.0) or 0.0)
    except Exception:
        pass

    return {
        "path": path,
        "title": _first(tags, "title") or f_title,
        "artist": _first(tags, "albumartist") or _first(tags, "artist") or fallback_artist,
        "album": _first(tags, "album") or fallback_album,
        "disc": _int_part(_first(tags, "discnumber")) or f_disc or 1,
        "number": _int_part(_first(tags, "tracknumber")) or f_number,
        "duration": duration,
        "year": _first(tags, "date")[:4],
        "mbid_artist": _first(tags, "musicbrainz_albumartistid"),
        "mbid_album": _first(tags, "musicbrainz_albumid"),
    }


def _sidecar_lyrics(path, listings):
    folder = os.path.dirname(path)
    if folder not in listings:
        try:
            listings[folder] = {name.lower(): name for name in os.listdir(folder)}
        except OSError:
            listings[folder] = {}
    stem = Path(path).stem.lower()
    for ext in LYRIC_EXTS:
        actual = listings[folder].get(stem + ext)
        if actual:
            return os.path.join(folder, actual)
    return None


# ---- the scan ----------------------------------------------------------------

def scan_music(folders, progress=None, cancelled=lambda: False):
    """Returns (artist_count, track_count), or None if stopped. Never touches your files."""
    progress = progress or (lambda *args: None)

    found = {}
    for root in folders:
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if not d.startswith(".")]
            for name in filenames:
                if os.path.splitext(name)[1].lower() in AUDIO_EXTS:
                    found.setdefault(os.path.join(dirpath, name), root)
            if cancelled():
                return None
    files = sorted(found.items())
    total = len(files)

    conn = db.connect()
    try:
        old_tracks = db.load_tracks(conn)
        old_covers = db.load_album_covers(conn)
        listings = {}
        records = []

        for i, (path, root) in enumerate(files, 1):
            if cancelled():
                return None
            try:
                stat = os.stat(path)
            except OSError:
                continue
            old = old_tracks.get(path)
            if old and old["mtime"] == stat.st_mtime and old["size"] == stat.st_size:
                rec = dict(old)
                rec["_changed"] = False
            else:
                rec = _read_track(path, root)
                rec["mtime"], rec["size"] = stat.st_mtime, stat.st_size
                rec["_changed"] = True
            rec["lyrics_path"] = _sidecar_lyrics(path, listings)
            rec["_artist_dir"] = _place(path, root)[2]
            rec["_album_dir"] = os.path.dirname(path)
            records.append(rec)
            if i % 5 == 0 or i == total:
                progress(i, total, os.path.basename(path))

        artists = {}
        for rec in records:
            artist = artists.setdefault(rec["artist"].casefold(), {
                "name": rec["artist"], "mbid": rec["mbid_artist"],
                "dir": rec["_artist_dir"], "albums": {},
            })
            if not artist["mbid"]:
                artist["mbid"] = rec["mbid_artist"]
            album = artist["albums"].setdefault(rec["album"].casefold(), {
                "title": rec["album"], "year": rec["year"], "mbid": rec["mbid_album"],
                "dir": rec["_album_dir"], "tracks": [], "changed": False,
            })
            album["tracks"].append(rec)
            album["changed"] = album["changed"] or rec["_changed"]

        for artist in artists.values():
            if cancelled():
                return None
            for album in artist["albums"].values():
                album["tracks"].sort(key=lambda t: (t["disc"], t["number"], t["title"].casefold()))
                cover = None
                if not album["changed"]:
                    cover = old_covers.get((artist["name"].casefold(), album["title"].casefold()))
                    if cover and not os.path.exists(cover):
                        cover = None
                if not cover:
                    for rec in album["tracks"][:3]:
                        cover = _save_cover(_embedded_cover(rec["path"]))
                        if cover:
                            break
                if not cover:
                    cover = _save_cover(_folder_image(album["dir"], ALBUM_IMAGE_NAMES))
                album["cover"] = cover

            cover = None
            if artist["dir"]:
                cover = _save_cover(_folder_image(artist["dir"], ARTIST_IMAGE_NAMES))
            if not cover:
                ordered = sorted(
                    artist["albums"].values(),
                    key=lambda a: (a["year"] or "9999", a["title"].casefold()),
                )
                cover = next((a["cover"] for a in ordered if a["cover"]), None)
            artist["cover"] = cover

        db.replace_music(conn, artists)
        return len(artists), total
    finally:
        conn.close()


class ScanWorker(QObject):
    """Runs the scan on a background thread so the window never freezes."""

    progress = Signal(int, int, str)
    done = Signal(object)      # (artists, tracks), or None if stopped
    failed = Signal(str)

    def __init__(self, folders, kind="music"):
        super().__init__()
        self.folders = list(folders)
        self.kind = kind                  # "music", "movies" or "tv"
        self._cancel = False

    def cancel(self):
        self._cancel = True

    def run(self):
        try:
            if self.kind == "movies":
                from .videoscan import scan_movies as scan
            elif self.kind == "tv":
                from .videoscan import scan_tv as scan
            else:
                scan = scan_music
            result = scan(self.folders, self.progress.emit, lambda: self._cancel)
        except Exception as exc:
            self.failed.emit(f"{type(exc).__name__}: {exc}")
            return
        self.done.emit(result)
