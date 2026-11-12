"""Plays your music files. Uses Qt's built-in audio player, fully offline."""
import os
import random

from PySide6.QtCore import QObject, QUrl, Signal
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer


class Player(QObject):
    trackChanged = Signal(dict)
    stateChanged = Signal(str)       # "playing", "paused" or "stopped"
    positionChanged = Signal(int)    # milliseconds
    durationChanged = Signal(int)    # milliseconds
    errorOccurred = Signal(str)
    sleepReached = Signal()          # the sleep timer's "end of this song" just stopped playback
    queueChanged = Signal()          # the list of what plays next changed

    def __init__(self, parent=None):
        super().__init__(parent)
        self._audio = QAudioOutput(self)
        self._media = QMediaPlayer(self)
        self._media.setAudioOutput(self._audio)
        self._queue = []
        self._index = -1
        self._order = []            # the order tracks are played in (shuffled or straight)
        self._pos = -1              # where we are in that order
        self.resume_provider = None   # optional: function(track) -> milliseconds to start a video at
        self._pending_seek = 0
        self._shuffle = False
        self._repeat = "off"        # "off", "all" or "one"
        self._stop_after_current = False   # sleep timer: "end of this song"

        self._media.positionChanged.connect(lambda ms: self.positionChanged.emit(int(ms)))
        self._media.durationChanged.connect(lambda ms: self.durationChanged.emit(int(ms)))
        self._media.playbackStateChanged.connect(self._on_state)
        self._media.mediaStatusChanged.connect(self._on_status)
        self._media.errorOccurred.connect(self._on_error)

    def set_video_output(self, widget):
        self._media.setVideoOutput(widget)

    # ---- controls ---------------------------------------------------------

    def play_queue(self, tracks, index):
        self._queue = list(tracks)
        self._index = index
        self._rebuild_order()
        self._load_current()

    # ---- the queue: what plays next ---------------------------------------

    def current_track(self):
        return self._queue[self._index] if 0 <= self._index < len(self._queue) else None

    def upcoming(self):
        """The tracks that will play after this one, in order."""
        if self._pos < 0:
            return []
        return [self._queue[i] for i in self._order[self._pos + 1:]]

    def play_next(self, track):
        """Put a song right after the one playing."""
        if self.current_track() is None:
            self.play_queue([track], 0)
            return
        self._queue.append(dict(track))
        self._order.insert(self._pos + 1, len(self._queue) - 1)
        self.queueChanged.emit()

    def add_to_queue(self, track):
        """Put a song at the end of the queue."""
        if self.current_track() is None:
            self.play_queue([track], 0)
            return
        self._queue.append(dict(track))
        self._order.append(len(self._queue) - 1)
        self.queueChanged.emit()

    def jump_to_upcoming(self, k):
        pos = self._pos + 1 + k
        if 0 <= pos < len(self._order):
            self._pos = pos
            self._index = self._order[pos]
            self._load_current()

    def remove_upcoming(self, k):
        pos = self._pos + 1 + k
        if not 0 <= pos < len(self._order):
            return
        gone = self._order.pop(pos)
        del self._queue[gone]
        self._order = [i - 1 if i > gone else i for i in self._order]
        if gone < self._index:
            self._index -= 1
        self.queueChanged.emit()

    def clear_upcoming(self):
        if self._pos < 0:
            return
        keep = self._order[:self._pos + 1]
        self._queue = [self._queue[i] for i in keep]
        self._index = keep.index(self._index)
        self._order = list(range(len(self._queue)))
        self._pos = self._order[self._index]
        self.queueChanged.emit()

    def set_stop_after_current(self, on):
        self._stop_after_current = bool(on)

    def position(self):
        return int(self._media.position())

    def duration(self):
        return int(self._media.duration())

    def set_shuffle(self, on):
        self._shuffle = bool(on)
        self._rebuild_order()

    def set_repeat(self, mode):
        self._repeat = mode if mode in ("off", "all", "one") else "off"

    def _rebuild_order(self):
        order = list(range(len(self._queue)))
        if self._shuffle:
            random.shuffle(order)
            if self._index in order:                 # the track playing now stays first
                order.remove(self._index)
                order.insert(0, self._index)
        self._order = order
        self._pos = order.index(self._index) if self._index in order else -1

    def play(self):
        if not self._queue or self._index < 0:
            return
        if self._media.source().isEmpty():
            self._load_current()
        else:
            self._media.play()

    def pause(self):
        if self._media.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            self._media.pause()

    def stop(self):
        self._media.stop()

    def next(self):
        if self._pos + 1 < len(self._order):
            self._pos += 1
        elif self._repeat == "all" and self._order:
            if self._shuffle:
                self._rebuild_order()                 # a fresh shuffle for the next round
            self._pos = 0
        else:
            return False
        self._index = self._order[self._pos]
        self._load_current()
        return True

    def previous(self):
        if self._media.position() > 3000 or self._pos <= 0:
            self._media.setPosition(0)
        else:
            self._pos -= 1
            self._index = self._order[self._pos]
            self._load_current()

    def seek(self, ms):
        self._media.setPosition(int(ms))

    def set_volume(self, percent):
        self._audio.setVolume(max(0, min(100, int(percent))) / 100.0)

    def set_muted(self, muted):
        self._audio.setMuted(bool(muted))

    def skip(self, delta_ms):
        """Jump forward or back, staying inside the current track."""
        end = self._media.duration()
        target = self._media.position() + int(delta_ms)
        if end > 0:
            target = min(target, max(0, end - 500))
        self._media.setPosition(max(0, target))

    # ---- internals --------------------------------------------------------

    def _load_current(self):
        if not 0 <= self._index < len(self._queue):
            return
        track = self._queue[self._index]
        if not os.path.exists(track["path"]):
            self.errorOccurred.emit("Can't find this file. Is the drive connected?")
            return
        self.trackChanged.emit(track)
        self.queueChanged.emit()
        self._pending_seek = 0
        if track.get("video") and self.resume_provider is not None:
            try:
                self._pending_seek = int(self.resume_provider(track) or 0)
            except Exception:
                self._pending_seek = 0
        self._media.setSource(QUrl.fromLocalFile(track["path"]))
        self._media.play()

    def _on_state(self, state):
        states = QMediaPlayer.PlaybackState
        if state == states.PlayingState:
            self.stateChanged.emit("playing")
        elif state == states.PausedState:
            self.stateChanged.emit("paused")
        else:
            self.stateChanged.emit("stopped")

    def _on_status(self, status):
        loaded = (QMediaPlayer.MediaStatus.LoadedMedia, QMediaPlayer.MediaStatus.BufferedMedia)
        if self._pending_seek and status in loaded:
            self._media.setPosition(self._pending_seek)      # pick up where you left off
            self._pending_seek = 0
        if status == QMediaPlayer.MediaStatus.EndOfMedia:
            if self._stop_after_current:             # sleep timer: end of this song
                self._stop_after_current = False
                self._media.stop()
                self.sleepReached.emit()
            elif self._repeat == "one":
                self._media.setPosition(0)
                self._media.play()
            else:
                self.next()

    def _on_error(self, error, message):
        self.errorOccurred.emit(message or "Can't play this file.")
