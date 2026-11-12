"""Builds a small fake library, opens every page and takes pictures. Run by the automatic build, not by you."""
import os
import sys
import tempfile
import traceback
from pathlib import Path

root = Path(tempfile.mkdtemp(prefix="you_selftest_"))
os.environ["APPDATA"] = str(root / "appdata")
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
out = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "shots"
out.mkdir(parents=True, exist_ok=True)

from PySide6.QtGui import QColor, QImage
from PySide6.QtWidgets import QApplication

from app import db
from app.settings import Settings
from app.theme import STYLESHEET
from app.window import MainWindow

app = QApplication(sys.argv)
app.setStyleSheet(STYLESHEET)
problems = []


def step(name, fn):
    try:
        fn()
        app.processEvents()
        print("ok  ", name)
    except Exception:
        problems.append(name)
        print("FAIL", name)
        traceback.print_exc()


def picture(name, color, size=(600, 600)):
    path = root / f"{name}.png"
    image = QImage(size[0], size[1], QImage.Format.Format_RGB32)
    image.fill(QColor(color))
    image.save(str(path))
    return str(path)


def video(name):
    path = root / f"{name}.mp4"
    path.write_bytes(b"x" * 10)
    return str(path)


def seed():
    conn = db.connect()
    artists = {}
    for n, (artist, color) in enumerate((("Aurora Ridge", "#7a3b8f"), ("Echo Harbor", "#2b6f8f"))):
        albums = {}
        for a, (title, count) in enumerate((("First Light", 10), ("Single Day", 1), ("Small EP", 3))):
            tracks = [
                {"path": str(root / f"{artist}-{title}-{t}.mp3"), "mtime": 1.0, "size": 1, "title": f"{title} Song {t + 1}",
                 "artist": artist, "album": title, "disc": 1, "number": t + 1, "duration": 200.0 + t,
                 "year": str(2020 + a), "lyrics_path": None, "mbid_artist": None, "mbid_album": None}
                for t in range(count)
            ]
            albums[title] = {"title": title, "year": str(2020 + a), "cover": picture(f"{artist}{title}", color),
                             "mbid": None, "dir": str(root), "tracks": tracks}
        artists[artist] = {"name": artist, "cover": None, "mbid": None, "dir": str(root), "albums": albums}
    db.replace_music(conn, artists)

    movies = []
    for n in range(6):
        files = [{"path": video(f"movie{n}-{k}"), "mtime": 100.0 + n, "size": 10, "title": f"Movie {n} part {k + 1}",
                  "duration": 5400.0} for k in range(2 if n < 2 else 1)]
        movies.append({"key": f"movie{n}", "title": f"Movie Number {n}", "folder": str(root), "cover": picture(f"m{n}", "#8f3b3b", (400, 600)),
                       "files": files})
    db.replace_movies(conn, movies)

    shows = []
    for n in range(4):
        episodes = [{"season": s, "season_title": "", "number": e, "title": f"Episode {e}", "path": video(f"show{n}-{s}-{e}"),
                     "mtime": 200.0 + n, "size": 10, "duration": 1500.0} for s in (1, 2) for e in (1, 2, 3)]
        shows.append({"key": f"show{n}", "title": f"Show Number {n}", "folder": str(root), "cover": picture(f"s{n}", "#3b8f5a", (400, 600)),
                      "episodes": episodes})
    db.replace_tv(conn, shows)
    db.save_resume(conn, video("movie3-0"), 600000, 5400000)
    db.save_resume(conn, video("show1-1-2"), 300000, 1500000)
    pid = db.create_playlist(conn, "Road trip")
    db.add_to_playlist(conn, pid, str(root / "Aurora Ridge-First Light-0.mp3"))
    db.add_to_playlist(conn, pid, str(root / "Aurora Ridge-First Light-1.mp3"))
    conn.close()


seed()
window = MainWindow(Settings())
window.resize(1280, 800)
window.show()
app.processEvents()


def shot(name):
    window.grab().save(str(out / f"{name}.png"))


for index, name in enumerate(("movies", "tv", "music", "settings")):
    step(f"open {name}", lambda i=index: window._on_nav(i))
    step(f"picture {name}", lambda n=name: shot(n))

window._on_nav(0)
step("movies: shuffle chip", lambda: window.movies.library.chips._group.buttons()[3].click())
step("movies: sort title", lambda: window.movies.library.chips._group.buttons()[1].click())
step("movies: hero play", lambda: window.movies.library.hero.primary.click())
step("movies: open a movie", lambda: window.movies.open_movie({"id": 1}))
step("picture movie page", lambda: shot("movie_page"))
step("movies: back", lambda: window.movies.reload())

window._on_nav(1)
step("tv: hero play", lambda: window.tv.library.hero.primary.click())
step("tv: open show", lambda: window.tv.open_show({"id": 1}))
step("tv: open season", lambda: window.tv.open_season({"show_id": 1, "season": 1, "title": "Season 1"}))
step("tv: back", lambda: window.tv.reload())

window._on_nav(2)
step("music: artist page", lambda: window.music.open_artist({"id": 1}))
step("picture artist", lambda: shot("artist"))
step("music: show all songs", lambda: window.music.artist_page.more_button.click())
step("music: album page", lambda: window.music.open_album({"id": 1}))
step("picture album", lambda: shot("album"))
step("music: playlists", lambda: window.music.open_playlists())
step("picture playlists", lambda: shot("playlists"))
step("music: open playlist", lambda: window.music.open_playlist({"id": 1, "name": "Road trip", "count": 2}))
step("picture playlist", lambda: shot("playlist"))
step("music: favorites", lambda: window.music.open_favorites())

for kind, nav_index in (("movies", 0), ("tv", 1), ("music", 2)):
    window._on_nav(nav_index)
    step(f"theme grid: {kind}", lambda k=kind: window._on_theme(k, "grid"))
    step(f"picture grid {kind}", lambda k=kind: shot(f"grid_{k}"))
step("theme grid: artist page", lambda: window.music.open_artist({"id": 1}))
step("picture grid artist", lambda: shot("grid_artist"))
step("theme grid: album from artist", lambda: window.music.open_album({"id": 1}))
for kind, nav_index in (("movies", 0), ("tv", 1), ("music", 2)):
    window._on_nav(nav_index)
    step(f"theme browse: {kind}", lambda k=kind: window._on_theme(k, "browse"))
step("browse artist page again", lambda: window.music.open_artist({"id": 1}))
step("stop button", lambda: window._stop())

def queue_checks():
    def song(title):
        path = root / f"queue-{title}.mp3"
        path.write_bytes(b"x" * 10)
        return {"path": str(path), "title": title, "artist": "Aurora Ridge", "album": "First Light", "duration": 100}
    player = window.player
    player.play_queue([song("T1"), song("T2"), song("T3")], 0)
    player.play_next(song("A"))
    player.add_to_queue(song("B"))
    titles = [t["title"] for t in player.upcoming()]
    assert titles == ["A", "T2", "T3", "B"], titles
    player.remove_upcoming(1)
    assert [t["title"] for t in player.upcoming()] == ["A", "T3", "B"]
    player.jump_to_upcoming(1)                      # T3 now playing, B after it
    assert player.current_track()["title"] == "T3"
    assert [t["title"] for t in player.upcoming()] == ["B"]
    player.clear_upcoming()
    assert player.upcoming() == [] and player.current_track()["title"] == "T3"
    window._queue_track(song("C"), True)
    assert [t["title"] for t in player.upcoming()] == ["C"]


step("queue: play next, add, remove, jump, clear", queue_checks)
step("now playing page opens", lambda: window._toggle_lyrics())
step("picture now playing", lambda: shot("now_playing"))
step("now playing page closes", lambda: window._toggle_lyrics())


def sleep_checks():
    window._set_sleep_timer(15)
    assert window._sleep_timer.isActive()
    window._set_sleep_timer(-1)
    assert not window._sleep_timer.isActive()
    window._set_sleep_timer(0)


step("sleep timer: set and cancel", sleep_checks)

# ---- your colors, picture and profile ----------------------------------------
from PySide6.QtGui import QImage as _QImage
from app import look as looks
from app.personal import WelcomeDialog, icon_pixmap, make_shortcut, round_pixmap


def red_picture():
    path = root / "red.png"
    image = _QImage(800, 600, _QImage.Format.Format_RGB32)
    image.fill(QColor("#ff0000"))
    image.save(str(path))
    return str(path)


def pixel(x, y):
    image = window.grab().toImage()
    c = QColor(image.pixel(x, y))
    return c.red(), c.green(), c.blue()


def expect(condition, message):
    if not condition:
        raise AssertionError(message)


def look_all(value):
    window.settings.set("look_all", value)
    window._apply_looks()


step("colors: ocean preset", lambda: look_all(looks.normalize(dict(looks.PRESETS[1][1]))))
for name, nav_index in (("movies", 0), ("tv", 1), ("music", 2), ("settings", 3)):
    window._on_nav(nav_index)
    step(f"picture ocean {name}", lambda n=name: shot(f"ocean_{n}"))


def check_picture_shows():
    value = looks.normalize(dict(looks.PRESETS[1][1]))
    value["picture"] = red_picture()
    value["dim"] = 0
    window.settings.set("theme_music", "grid")
    window.music.set_theme("grid")
    look_all(value)
    window._on_nav(2)
    window.music.reload()
    app.processEvents()
    window.grab().save(str(out / "background_picture.png"))
    r, g, b = pixel(100, 450)
    expect(r > 120 and r > b + 40, f"background picture does not show in the sidebar (pixel {r},{g},{b})")
    r, g, b = pixel(900, 300)
    expect(r > 120 and r > b + 40, f"background picture does not show behind the pages (pixel {r},{g},{b})")


step("colors: background picture shows through", check_picture_shows)
window._on_nav(0)
step("picture background movies", lambda: shot("background_movies"))
window._on_nav(3)
step("picture background settings", lambda: shot("background_settings"))
step("colors: back to classic", lambda: look_all({}))


def check_own_section():
    own = looks.normalize(dict(looks.PRESETS[3][1]))
    window.settings.set("look_tv", own)
    window._apply_looks()
    window._on_nav(1)
    expect(looks.C.dark.name() == looks.tone_color(own["dark"]).name(), "the TV Shows page did not take its own colors")
    window._on_nav(2)
    expect(looks.C.dark.name() == "#000000", "Music did not go back to the whole-app colors")
    window._on_nav(1)
    window.grab().save(str(out / "own_tv.png"))
    window.settings.set("look_tv", {})
    window._apply_looks()


step("colors: a section with its own colors", check_own_section)


def check_panels():
    panel = window.settings_page.colors_panel
    for scope in ("music", "movies", "tv", "all"):
        panel._set_scope(scope)
        if scope != "all":
            panel.own.setChecked(True)
        for tone in ("light", "middle", "dark"):
            panel._set_tone(tone)
            panel.wheel._pick(__import__("PySide6.QtCore", fromlist=["QPointF"]).QPointF(180, 100))
            panel.sat.setValue(40)
            panel.bright.setValue(60)
        panel._apply_preset(looks.PRESETS[2][1])
        if scope != "all":
            panel.own.setChecked(False)
    panel._reset()
    window.settings_page.grab()


step("colors: settings panel (wheel, sliders, presets, reset)", check_panels)


def check_profile():
    picture = looks.keep_picture(red_picture(), root / "pictures", "profile", 512)
    expect(picture and os.path.isfile(picture), "profile picture was not kept")
    window.settings.set("profile_picture", picture)
    window.settings.set("profile_name", "Sam")
    window._apply_profile()
    window.settings_page.profile_panel.refresh()
    expect(window.name_label.text() == "Sam", "the name did not show")
    expect(not round_pixmap(picture, 64).isNull(), "round picture failed")
    expect(not round_pixmap("", 64).isNull(), "default person picture failed")
    ico = root / "t.ico"
    expect(looks.write_ico(picture, ico) and ico.stat().st_size > 100, "icon file was not made")
    for color in looks.ICON_COLORS:
        window.settings_page.profile_panel._pick_icon_color(color[0])
        expect(not icon_pixmap(64, color[0]).isNull(), "icon color picture failed")
    window.settings_page.profile_panel._pick_icon_color("black")
    print(make_shortcut(picture))
    window.settings.set("profile_picture", "")
    window.settings.set("profile_name", "")
    window._apply_profile()


step("profile: picture, name, icon file", check_profile)


def check_welcome():
    dialog = WelcomeDialog(window.settings)
    dialog.show()
    app.processEvents()
    dialog.grab().save(str(out / "welcome.png"))
    dialog.close()


step("welcome window", check_welcome)

window.close()
if problems:
    print("PROBLEMS:", problems)
    sys.exit(1)
print("ALL GOOD. Pictures in", out)
