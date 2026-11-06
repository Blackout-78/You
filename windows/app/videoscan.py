"""Reads your movie and TV folders into the local library. Works fully offline."""
import os
import re
from pathlib import Path

from mutagen import File as MutagenFile

from . import db
from .covers import save_cover
from .scanner import _folder_image

VIDEO_EXTS = {".mp4", ".m4v", ".mkv", ".avi", ".mov", ".wmv", ".webm", ".mpg", ".mpeg", ".ts"}
POSTER_NAMES = ("poster", "folder", "cover", "front", "thumb")

_SEASON_DIR = re.compile(r"^\s*(?:season|series|s)\s*[-_.]?\s*(\d{1,2})\b", re.I)
_SPECIALS_DIR = re.compile(r"^\s*specials?\b", re.I)
_SXXEYY = re.compile(r"[Ss](\d{1,2})\s*[Ee](\d{1,3})")
_NXM = re.compile(r"(?<!\d)(\d{1,2})x(\d{2,3})(?!\d)")
_EXTRA_EP = re.compile(r"^\s*(?:[-–_ ]*[Ee]\d{1,3})+")
_CUT_YEAR = re.compile(r"\s*[\(\[\{]\s*(?:19|20)\d{2}")
_CUT_TAGS = re.compile(
    r"\b(?:2160p|1080p|720p|480p|WEB[- .]?DL|WEB[- .]?Rip|BluRay|BDRip|HDTV|x26[45]|H\.?26[45])\b",
    re.I,
)
_CUT_DASH = re.compile(r"\s+-\s+")
_SMALL_WORDS = {"of", "and", "the", "a", "an", "in", "on", "at", "to", "for", "vs", "or"}


# ---- names -------------------------------------------------------------------

def natural_key(text):
    return [int(part) if part.isdigit() else part.casefold() for part in re.split(r"(\d+)", text)]


def _title_case(text):
    words = text.split()
    return " ".join(
        word.capitalize() if i == 0 or word not in _SMALL_WORDS else word
        for i, word in enumerate(words)
    )


def clean_title(name):
    """'CODENAME Kids Next Door (2002-2008) - Complete ... 1080p' -> 'CODENAME Kids Next Door'"""
    text = name.strip()
    if " " not in text:
        text = re.sub(r"[._]+", " ", text).replace("-", " ")
    for pattern in (_CUT_YEAR, _CUT_TAGS, _CUT_DASH):
        match = pattern.search(text)
        if match and match.start() > 0:
            text = text[:match.start()]
    text = re.sub(r"\s+", " ", text).strip(" -._")
    if text and text == text.lower():
        text = _title_case(text)
    return text or name.strip()


def file_title(path):
    stem = Path(path).stem.strip()
    if " " not in stem:
        stem = re.sub(r"[._]+", " ", stem).strip()
    return stem


def _season_from_dirs(path, show_dir):
    """Look at the folders between the show folder and the file for 'Season N' or 'Specials'."""
    folder = os.path.dirname(path)
    show_dir = os.path.normcase(os.path.normpath(show_dir))
    while os.path.normcase(os.path.normpath(folder)) != show_dir and len(folder) > len(show_dir):
        name = os.path.basename(folder)
        match = _SEASON_DIR.match(name)
        if match:
            return int(match.group(1)), name
        if _SPECIALS_DIR.match(name):
            return 0, name
        folder = os.path.dirname(folder)
    return None, ""


def parse_episode(path, show_dir):
    """(season, episode number, episode title, season folder name) from the file and its folders."""
    stem = Path(path).stem
    season = number = None
    rest = ""
    match = _SXXEYY.search(stem) or _NXM.search(stem)
    if match:
        season, number = int(match.group(1)), int(match.group(2))
        rest = stem[match.end():]
    folder_season, season_name = _season_from_dirs(path, show_dir)
    if season is None:
        season = folder_season if folder_season is not None else 1
    if number is None:
        lead = re.match(r"\s*(\d{1,3})\b", stem)
        number = int(lead.group(1)) if lead else 0
        rest = stem[lead.end():] if lead else stem
    rest = _EXTRA_EP.sub("", rest)
    if " " not in rest.strip():
        rest = re.sub(r"[._]+", " ", rest)
    title = rest.strip(" -–_.")
    if not title:
        title = f"Episode {number}" if number else file_title(path)
    return season, number, title, season_name


# ---- scanning ----------------------------------------------------------------

def _is_video(name):
    return os.path.splitext(name)[1].lower() in VIDEO_EXTS and not name.startswith(".")


def _videos_under(folder):
    found = []
    for dirpath, dirnames, filenames in os.walk(folder):
        dirnames[:] = [d for d in dirnames if not d.startswith(".")]
        found.extend(os.path.join(dirpath, n) for n in filenames if _is_video(n))
    return sorted(found, key=lambda p: natural_key(os.path.relpath(p, folder)))


def _key(path):
    return os.path.normcase(os.path.normpath(path))


def _info(path, cache):
    """(mtime, size, duration in seconds). Durations are only read for new or changed files."""
    stat = os.stat(path)
    old = cache.get(path)
    if old and old[0] == stat.st_mtime and old[1] == stat.st_size:
        return stat.st_mtime, stat.st_size, old[2] or 0.0
    duration = 0.0
    try:
        audio = MutagenFile(path)
        if audio is not None:
            duration = float(audio.info.length or 0.0)
    except Exception:
        pass
    return stat.st_mtime, stat.st_size, duration


def _folder_entries(folders):
    """[(item folder, [video paths])] one per sub-folder of each library folder."""
    out = []
    for root in folders:
        try:
            names = sorted(os.listdir(root), key=natural_key)
        except OSError:
            continue
        for name in names:
            full = os.path.join(root, name)
            if name.startswith("."):
                continue
            if os.path.isdir(full):
                videos = _videos_under(full)
                if videos:
                    out.append((full, videos))
            elif _is_video(name):
                out.append((full, [full]))
    return out


def scan_movies(folders, progress=None, cancelled=lambda: False):
    """Returns (movie_count, video_count), or None if stopped. Never touches your files."""
    progress = progress or (lambda *args: None)
    entries = _folder_entries(folders)
    total = sum(len(videos) for _, videos in entries)

    conn = db.connect()
    try:
        cache = db.load_video_cache(conn)
        movies, done = [], 0
        for folder, videos in entries:
            if cancelled():
                return None
            files = []
            for path in videos:
                try:
                    mtime, size, duration = _info(path, cache)
                except OSError:
                    continue
                files.append({"path": path, "title": file_title(path),
                              "mtime": mtime, "size": size, "duration": duration})
                done += 1
                if done % 5 == 0 or done == total:
                    progress(done, total, os.path.basename(path))
            if not files:
                continue
            is_dir = os.path.isdir(folder)
            name = os.path.basename(folder) if is_dir else Path(folder).stem
            cover = save_cover(_folder_image(folder, POSTER_NAMES)) if is_dir else None
            movies.append({"key": _key(folder), "title": clean_title(name), "folder": folder,
                           "cover": cover, "files": files})
        db.replace_movies(conn, movies)
        return len(movies), sum(len(m["files"]) for m in movies)
    finally:
        conn.close()


def scan_tv(folders, progress=None, cancelled=lambda: False):
    """Returns (show_count, episode_count), or None if stopped."""
    progress = progress or (lambda *args: None)
    entries = [(f, v) for f, v in _folder_entries(folders) if os.path.isdir(f)]
    total = sum(len(videos) for _, videos in entries)

    conn = db.connect()
    try:
        cache = db.load_video_cache(conn)
        shows, done = [], 0
        for folder, videos in entries:
            if cancelled():
                return None
            episodes = []
            for path in videos:
                try:
                    mtime, size, duration = _info(path, cache)
                except OSError:
                    continue
                season, number, title, season_name = parse_episode(path, folder)
                episodes.append({"season": season, "season_title": season_name, "number": number,
                                 "title": title, "path": path, "mtime": mtime, "size": size,
                                 "duration": duration})
                done += 1
                if done % 5 == 0 or done == total:
                    progress(done, total, os.path.basename(path))
            if not episodes:
                continue
            episodes.sort(key=lambda e: (e["season"], e["number"], natural_key(e["path"])))
            shows.append({"key": _key(folder), "title": clean_title(os.path.basename(folder)),
                          "folder": folder, "cover": save_cover(_folder_image(folder, POSTER_NAMES)),
                          "episodes": episodes})
        db.replace_tv(conn, shows)
        return len(shows), sum(len(s["episodes"]) for s in shows)
    finally:
        conn.close()
