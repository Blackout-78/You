"""The local library database. One file on your PC: %APPDATA%\\You\\library.db"""
import os
import sqlite3
import time

from .settings import data_dir

SCHEMA = """
CREATE TABLE IF NOT EXISTS artists(
    id INTEGER PRIMARY KEY, name TEXT NOT NULL, cover TEXT, mbid TEXT, folder TEXT);
CREATE TABLE IF NOT EXISTS albums(
    id INTEGER PRIMARY KEY, artist_id INTEGER NOT NULL, title TEXT NOT NULL,
    year TEXT, cover TEXT, mbid TEXT, folder TEXT);
CREATE TABLE IF NOT EXISTS tracks(
    id INTEGER PRIMARY KEY, album_id INTEGER NOT NULL, path TEXT UNIQUE NOT NULL,
    mtime REAL, size INTEGER, title TEXT, artist TEXT, album TEXT,
    disc INTEGER, number INTEGER, duration REAL, year TEXT,
    lyrics_path TEXT, mbid_artist TEXT, mbid_album TEXT);
CREATE TABLE IF NOT EXISTS notes(
    kind TEXT NOT NULL, key TEXT NOT NULL, text TEXT NOT NULL, PRIMARY KEY(kind, key));
CREATE TABLE IF NOT EXISTS movies(
    id INTEGER PRIMARY KEY, key TEXT UNIQUE NOT NULL, title TEXT NOT NULL, folder TEXT, cover TEXT);
CREATE TABLE IF NOT EXISTS movie_files(
    id INTEGER PRIMARY KEY, movie_id INTEGER NOT NULL, path TEXT UNIQUE NOT NULL,
    mtime REAL, size INTEGER, title TEXT, duration REAL, ord INTEGER);
CREATE TABLE IF NOT EXISTS shows(
    id INTEGER PRIMARY KEY, key TEXT UNIQUE NOT NULL, title TEXT NOT NULL, folder TEXT, cover TEXT);
CREATE TABLE IF NOT EXISTS episodes(
    id INTEGER PRIMARY KEY, show_id INTEGER NOT NULL, season INTEGER, season_title TEXT,
    number INTEGER, title TEXT, path TEXT UNIQUE NOT NULL, mtime REAL, size INTEGER, duration REAL);
CREATE TABLE IF NOT EXISTS titles(
    kind TEXT NOT NULL, key TEXT NOT NULL, text TEXT NOT NULL, PRIMARY KEY(kind, key));
CREATE TABLE IF NOT EXISTS favorites(
    path TEXT PRIMARY KEY, title TEXT, artist TEXT, album TEXT, video INTEGER);
CREATE TABLE IF NOT EXISTS resume(
    path TEXT PRIMARY KEY, position_ms INTEGER NOT NULL, duration_ms INTEGER);
CREATE TABLE IF NOT EXISTS playlists(
    id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL);
CREATE TABLE IF NOT EXISTS playlist_items(
    id INTEGER PRIMARY KEY, playlist_id INTEGER NOT NULL, path TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS custom_covers(
    kind TEXT NOT NULL, key TEXT NOT NULL, path TEXT NOT NULL, PRIMARY KEY(kind, key));
"""

TRACK_FIELDS = (
    "path", "mtime", "size", "title", "artist", "album", "disc", "number",
    "duration", "year", "mbid_artist", "mbid_album",
)


def connect():
    conn = sqlite3.connect(str(data_dir() / "library.db"), timeout=30)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)
    try:        # older libraries: remember when a video was last watched
        conn.execute("ALTER TABLE resume ADD COLUMN updated REAL")
    except sqlite3.OperationalError:
        pass
    return conn


def load_tracks(conn):
    """Previously scanned tracks by path, so unchanged files aren't read again."""
    rows = conn.execute(f"SELECT {', '.join(TRACK_FIELDS)} FROM tracks").fetchall()
    return {row["path"]: dict(row) for row in rows}


def load_album_covers(conn):
    rows = conn.execute(
        "SELECT artists.name AS artist, albums.title AS album, albums.cover AS cover "
        "FROM albums JOIN artists ON artists.id = albums.artist_id"
    ).fetchall()
    return {
        (row["artist"].casefold(), row["album"].casefold()): row["cover"]
        for row in rows if row["cover"]
    }


def replace_music(conn, artists):
    """Swap the whole music library for the freshly scanned one, all or nothing."""
    with conn:
        conn.execute("DELETE FROM tracks")
        conn.execute("DELETE FROM albums")
        conn.execute("DELETE FROM artists")
        for artist in artists.values():
            cur = conn.execute(
                "INSERT INTO artists(name, cover, mbid, folder) VALUES(?,?,?,?)",
                (artist["name"], artist.get("cover"), artist.get("mbid"), artist.get("dir")),
            )
            artist_id = cur.lastrowid
            for album in artist["albums"].values():
                cur = conn.execute(
                    "INSERT INTO albums(artist_id, title, year, cover, mbid, folder) "
                    "VALUES(?,?,?,?,?,?)",
                    (artist_id, album["title"], album.get("year"), album.get("cover"),
                     album.get("mbid"), album.get("dir")),
                )
                album_id = cur.lastrowid
                for t in album["tracks"]:
                    conn.execute(
                        "INSERT INTO tracks(album_id, path, mtime, size, title, artist, album, "
                        "disc, number, duration, year, lyrics_path, mbid_artist, mbid_album) "
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        (album_id, t["path"], t["mtime"], t["size"], t["title"], t["artist"],
                         t["album"], t["disc"], t["number"], t["duration"], t["year"],
                         t.get("lyrics_path"), t.get("mbid_artist"), t.get("mbid_album")),
                    )


def clear_music(conn):
    replace_music(conn, {})


def custom_covers(conn, kind):
    """Pictures you chose yourself, by name. Missing files are ignored."""
    rows = conn.execute("SELECT key, path FROM custom_covers WHERE kind = ?", (kind,)).fetchall()
    return {r["key"]: r["path"] for r in rows if os.path.exists(r["path"])}


def set_custom_cover(conn, kind, key, path):
    with conn:
        if path:
            conn.execute(
                "INSERT OR REPLACE INTO custom_covers(kind, key, path) VALUES(?,?,?)",
                (kind, key, path),
            )
        else:
            conn.execute("DELETE FROM custom_covers WHERE kind = ? AND key = ?", (kind, key))


def music_artist_items(conn):
    mine = custom_covers(conn, "artist")
    rows = conn.execute("SELECT id, name, cover FROM artists").fetchall()
    return [
        {"id": r["id"], "title": r["name"], "cover_path": mine.get(r["name"].casefold(), r["cover"])}
        for r in rows
    ]


# ---- pages: artist, album, tracks, descriptions --------------------------------

def get_artist(conn, artist_id):
    row = conn.execute(
        "SELECT id, name, cover, mbid FROM artists WHERE id = ?", (artist_id,)
    ).fetchone()
    if not row:
        return None
    artist = dict(row)
    mine = custom_covers(conn, "artist").get(artist["name"].casefold())
    artist["custom_cover"] = bool(mine)
    if mine:
        artist["cover"] = mine
    return artist


def albums_for_artist(conn, artist_id):
    rows = conn.execute(
        "SELECT id, title, year, cover, mbid, "
        "(SELECT COUNT(*) FROM tracks WHERE tracks.album_id = albums.id) AS n "
        "FROM albums WHERE artist_id = ?", (artist_id,)
    ).fetchall()
    return [dict(r) for r in rows]


def get_album(conn, album_id):
    row = conn.execute(
        "SELECT albums.id, albums.title, albums.year, albums.cover, albums.mbid, "
        "artists.name AS artist FROM albums JOIN artists ON artists.id = albums.artist_id "
        "WHERE albums.id = ?", (album_id,)
    ).fetchone()
    return dict(row) if row else None


def tracks_for_album(conn, album_id):
    rows = conn.execute(
        "SELECT path, title, artist, album, disc, number, duration, lyrics_path "
        "FROM tracks WHERE album_id = ? ORDER BY disc, number, title COLLATE NOCASE",
        (album_id,),
    ).fetchall()
    return [dict(r) for r in rows]


# Descriptions you write yourself are kept by name, so a rescan never loses them.

def artist_note_key(artist):
    return artist.casefold()


def album_note_key(artist, album):
    return f"{artist.casefold()}||{album.casefold()}"


def get_note(conn, kind, key):
    row = conn.execute("SELECT text FROM notes WHERE kind = ? AND key = ?", (kind, key)).fetchone()
    return row["text"] if row else ""


def set_note(conn, kind, key, text):
    with conn:
        if text.strip():
            conn.execute(
                "INSERT OR REPLACE INTO notes(kind, key, text) VALUES(?,?,?)",
                (kind, key, text.strip()),
            )
        else:
            conn.execute("DELETE FROM notes WHERE kind = ? AND key = ?", (kind, key))


# ---- movies and TV -------------------------------------------------------------

def load_video_cache(conn):
    """path -> (mtime, size, duration) for videos already scanned, so they aren't re-read."""
    cache = {}
    for table in ("movie_files", "episodes"):
        for r in conn.execute(f"SELECT path, mtime, size, duration FROM {table}"):
            cache[r["path"]] = (r["mtime"], r["size"], r["duration"])
    return cache


def replace_movies(conn, entries):
    with conn:
        conn.execute("DELETE FROM movie_files")
        conn.execute("DELETE FROM movies")
        for entry in entries:
            cur = conn.execute(
                "INSERT INTO movies(key, title, folder, cover) VALUES(?,?,?,?)",
                (entry["key"], entry["title"], entry["folder"], entry.get("cover")),
            )
            for order, f in enumerate(entry["files"]):
                conn.execute(
                    "INSERT INTO movie_files(movie_id, path, mtime, size, title, duration, ord) "
                    "VALUES(?,?,?,?,?,?,?)",
                    (cur.lastrowid, f["path"], f["mtime"], f["size"], f["title"], f["duration"], order),
                )


def replace_tv(conn, shows):
    with conn:
        conn.execute("DELETE FROM episodes")
        conn.execute("DELETE FROM shows")
        for show in shows:
            cur = conn.execute(
                "INSERT INTO shows(key, title, folder, cover) VALUES(?,?,?,?)",
                (show["key"], show["title"], show["folder"], show.get("cover")),
            )
            for ep in show["episodes"]:
                conn.execute(
                    "INSERT INTO episodes(show_id, season, season_title, number, title, path, "
                    "mtime, size, duration) VALUES(?,?,?,?,?,?,?,?,?)",
                    (cur.lastrowid, ep["season"], ep["season_title"], ep["number"], ep["title"],
                     ep["path"], ep["mtime"], ep["size"], ep["duration"]),
                )


def clear_movies(conn):
    replace_movies(conn, [])


def clear_tv(conn):
    replace_tv(conn, [])


def get_titles(conn, kind):
    rows = conn.execute("SELECT key, text FROM titles WHERE kind = ?", (kind,)).fetchall()
    return {r["key"]: r["text"] for r in rows}


def set_title(conn, kind, key, text):
    with conn:
        if text and text.strip():
            conn.execute(
                "INSERT OR REPLACE INTO titles(kind, key, text) VALUES(?,?,?)",
                (kind, key, text.strip()),
            )
        else:
            conn.execute("DELETE FROM titles WHERE kind = ? AND key = ?", (kind, key))


def _items(conn, table, kind):
    mine = custom_covers(conn, kind)
    names = get_titles(conn, kind)
    rows = conn.execute(f"SELECT id, key, title, cover FROM {table}").fetchall()
    return [
        {"id": r["id"], "title": names.get(r["key"], r["title"]),
         "cover_path": mine.get(r["key"], r["cover"])}
        for r in rows
    ]


def _one(conn, table, kind, item_id):
    row = conn.execute(
        f"SELECT id, key, title, cover FROM {table} WHERE id = ?", (item_id,)
    ).fetchone()
    if not row:
        return None
    item = dict(row)
    item["scanned_title"], item["scanned_cover"] = item["title"], item["cover"]
    name = get_titles(conn, kind).get(item["key"])
    cover = custom_covers(conn, kind).get(item["key"])
    item["custom_title"], item["custom_cover"] = bool(name), bool(cover)
    if name:
        item["title"] = name
    if cover:
        item["cover"] = cover
    return item


def movie_items(conn):
    return _items(conn, "movies", "movie")


def get_movie(conn, movie_id):
    return _one(conn, "movies", "movie", movie_id)


def movie_files(conn, movie_id):
    rows = conn.execute(
        "SELECT path, title, duration FROM movie_files WHERE movie_id = ? ORDER BY ord",
        (movie_id,),
    ).fetchall()
    return [dict(r) for r in rows]


def show_items(conn):
    return _items(conn, "shows", "show")


def get_show(conn, show_id):
    return _one(conn, "shows", "show", show_id)


def seasons_for_show(conn, show_id):
    rows = conn.execute(
        "SELECT season, MIN(season_title) AS season_title, COUNT(*) AS count "
        "FROM episodes WHERE show_id = ? GROUP BY season ORDER BY season",
        (show_id,),
    ).fetchall()
    out = []
    for r in rows:
        title = r["season_title"] or ("Specials" if r["season"] == 0 else f"Season {r['season']}")
        out.append({"season": r["season"], "title": title, "count": r["count"]})
    return out


def episodes_for_season(conn, show_id, season):
    rows = conn.execute(
        "SELECT number, title, path, duration FROM episodes "
        "WHERE show_id = ? AND season = ? ORDER BY number, path",
        (show_id, season),
    ).fetchall()
    return [dict(r) for r in rows]


# ---- favorites (the heart on the player bar) -----------------------------------

def is_favorite(conn, path):
    return conn.execute("SELECT 1 FROM favorites WHERE path = ?", (path,)).fetchone() is not None


def toggle_favorite(conn, track):
    """Add or remove a track. Returns True if it is now a favorite."""
    with conn:
        if is_favorite(conn, track["path"]):
            conn.execute("DELETE FROM favorites WHERE path = ?", (track["path"],))
            return False
        conn.execute(
            "INSERT INTO favorites(path, title, artist, album, video) VALUES(?,?,?,?,?)",
            (track["path"], track.get("title"), track.get("artist"), track.get("album"),
             1 if track.get("video") else 0),
        )
        return True


def favorite_tracks(conn):
    """Everything with a heart, oldest first, with the lyrics file and length when it is a scanned song."""
    rows = conn.execute(
        "SELECT f.path, f.title, f.artist, f.album, f.video, t.duration, t.lyrics_path "
        "FROM favorites f LEFT JOIN tracks t ON t.path = f.path ORDER BY f.rowid"
    ).fetchall()
    return [dict(r) for r in rows]


def remove_favorite(conn, path):
    with conn:
        conn.execute("DELETE FROM favorites WHERE path = ?", (path,))


# ---- where you stopped watching a video --------------------------------------------

RESUME_MIN_MS = 10_000       # shorter than this is not worth remembering
RESUME_END_MS = 10_000       # this close to the end counts as finished


def get_resume(conn, path):
    row = conn.execute("SELECT position_ms FROM resume WHERE path = ?", (path,)).fetchone()
    return int(row["position_ms"]) if row else 0


def save_resume(conn, path, position_ms, duration_ms):
    """Remember a spot. Near the end the video counts as finished and is forgotten. Returns nothing."""
    position_ms = int(position_ms)
    duration_ms = int(duration_ms or 0)
    if position_ms < RESUME_MIN_MS:
        return
    with conn:
        if duration_ms > 0 and position_ms > duration_ms - RESUME_END_MS:
            conn.execute("DELETE FROM resume WHERE path = ?", (path,))
        else:
            conn.execute(
                "INSERT INTO resume(path, position_ms, duration_ms, updated) VALUES(?,?,?,?) "
                "ON CONFLICT(path) DO UPDATE SET position_ms = excluded.position_ms, "
                "duration_ms = excluded.duration_ms, updated = excluded.updated",
                (path, position_ms, duration_ms, time.time()),
            )


# ---- browse pages: Movies and TV Shows (big picture, sorting, rows) ------------------

def movie_browse(conn):
    """Every movie with what the browse page needs: length, when it was added, your description."""
    mine = custom_covers(conn, "movie")
    names = get_titles(conn, "movie")
    rows = conn.execute(
        "SELECT m.id, m.key, m.title, m.cover, COUNT(f.id) AS n, "
        "COALESCE(SUM(f.duration), 0) AS dur, COALESCE(MAX(f.mtime), 0) AS added "
        "FROM movies m LEFT JOIN movie_files f ON f.movie_id = m.id GROUP BY m.id"
    ).fetchall()
    return [
        {"id": r["id"], "key": r["key"], "title": names.get(r["key"], r["title"]),
         "cover_path": mine.get(r["key"], r["cover"]), "count": r["n"],
         "duration": r["dur"], "added": r["added"], "note": get_note(conn, "movie", r["key"])}
        for r in rows
    ]


def show_browse(conn):
    mine = custom_covers(conn, "show")
    names = get_titles(conn, "show")
    rows = conn.execute(
        "SELECT s.id, s.key, s.title, s.cover, COUNT(e.id) AS n, "
        "COUNT(DISTINCT e.season) AS seasons, COALESCE(SUM(e.duration), 0) AS dur, "
        "COALESCE(MAX(e.mtime), 0) AS added "
        "FROM shows s LEFT JOIN episodes e ON e.show_id = s.id GROUP BY s.id"
    ).fetchall()
    return [
        {"id": r["id"], "key": r["key"], "title": names.get(r["key"], r["title"]),
         "cover_path": mine.get(r["key"], r["cover"]), "count": r["n"], "seasons": r["seasons"],
         "duration": r["dur"], "added": r["added"], "note": get_note(conn, "show", r["key"])}
        for r in rows
    ]


def continue_watching(conn, kind):
    """Videos you started and did not finish, newest first. kind is 'movie' or 'tv'."""
    if kind == "movie":
        rows = conn.execute(
            "SELECT r.path, r.position_ms, m.id AS item_id, m.key, m.title AS item_title, m.cover, "
            "f.title AS video_title, f.duration, NULL AS season "
            "FROM resume r JOIN movie_files f ON f.path = r.path JOIN movies m ON m.id = f.movie_id "
            "ORDER BY COALESCE(r.updated, 0) DESC LIMIT 20"
        ).fetchall()
        mine, names = custom_covers(conn, "movie"), get_titles(conn, "movie")
    else:
        rows = conn.execute(
            "SELECT r.path, r.position_ms, s.id AS item_id, s.key, s.title AS item_title, s.cover, "
            "e.title AS video_title, e.duration, e.season "
            "FROM resume r JOIN episodes e ON e.path = r.path JOIN shows s ON s.id = e.show_id "
            "ORDER BY COALESCE(r.updated, 0) DESC LIMIT 20"
        ).fetchall()
        mine, names = custom_covers(conn, "show"), get_titles(conn, "show")
    out = []
    for r in rows:
        if not os.path.exists(r["path"]):
            continue
        title = names.get(r["key"], r["item_title"])
        label = title if kind == "movie" and r["video_title"] == title else f"{title} \u00b7 {r['video_title']}"
        out.append({
            "path": r["path"], "title": label, "item_title": title, "item_id": r["item_id"],
            "cover_path": mine.get(r["key"], r["cover"]), "duration": r["duration"] or 0,
            "position_ms": r["position_ms"], "season": r["season"],
        })
    return out


def tracks_for_artist(conn, artist_id):
    rows = conn.execute(
        "SELECT t.path, t.title, t.artist, t.album, t.duration, t.lyrics_path, "
        "a.cover AS album_cover, a.title AS album_title "
        "FROM tracks t JOIN albums a ON a.id = t.album_id WHERE a.artist_id = ? "
        "ORDER BY a.year, a.title COLLATE NOCASE, t.disc, t.number, t.title COLLATE NOCASE",
        (artist_id,),
    ).fetchall()
    return [dict(r) for r in rows]


# ---- playlists ----------------------------------------------------------------------

def playlists(conn):
    rows = conn.execute(
        "SELECT p.id, p.name, COUNT(i.id) AS n FROM playlists p "
        "LEFT JOIN playlist_items i ON i.playlist_id = p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE"
    ).fetchall()
    return [{"id": r["id"], "name": r["name"], "count": r["n"]} for r in rows]


def create_playlist(conn, name):
    """Returns the new playlist id, or None if the name is empty or already used."""
    name = (name or "").strip()
    if not name:
        return None
    try:
        with conn:
            return conn.execute("INSERT INTO playlists(name) VALUES(?)", (name,)).lastrowid
    except sqlite3.IntegrityError:
        return None


def delete_playlist(conn, playlist_id):
    with conn:
        conn.execute("DELETE FROM playlist_items WHERE playlist_id = ?", (playlist_id,))
        conn.execute("DELETE FROM playlists WHERE id = ?", (playlist_id,))


def add_to_playlist(conn, playlist_id, path):
    with conn:
        exists = conn.execute(
            "SELECT 1 FROM playlist_items WHERE playlist_id = ? AND path = ?", (playlist_id, path)
        ).fetchone()
        if not exists:
            conn.execute("INSERT INTO playlist_items(playlist_id, path) VALUES(?,?)", (playlist_id, path))


def remove_from_playlist(conn, playlist_id, path):
    with conn:
        conn.execute(
            "DELETE FROM playlist_items WHERE playlist_id = ? AND path = ?", (playlist_id, path)
        )


def playlist_tracks(conn, playlist_id):
    rows = conn.execute(
        "SELECT i.path, t.title, t.artist, t.album, t.duration, t.lyrics_path "
        "FROM playlist_items i JOIN tracks t ON t.path = i.path "
        "WHERE i.playlist_id = ? ORDER BY i.id",
        (playlist_id,),
    ).fetchall()
    return [dict(r) for r in rows]


def clear_my_data(conn):
    """Forget what You saved: descriptions, titles, covers you picked, hearts, resume spots, playlists."""
    with conn:
        for table in ("notes", "titles", "custom_covers", "favorites", "resume",
                      "playlist_items", "playlists"):
            conn.execute(f"DELETE FROM {table}")


def cover_for_track(conn, path):
    """The album cover of a song, for the Now Playing page and the queue."""
    row = conn.execute(
        "SELECT a.cover AS cover FROM tracks t JOIN albums a ON a.id = t.album_id WHERE t.path = ?", (path,)
    ).fetchone()
    return row["cover"] if row else None
