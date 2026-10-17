#!/usr/bin/env python3
"""Automated, isolated acceptance checks for a packaged TaskFlow build.

The checks run on a private Xvfb display with a throw-away home/data directory and
a private ALSA file PCM (see ``tools/isolated_audio.py``) that records playback to
a raw sample file. That means:

* the real desktop, keyboard, mouse, sound card and default sink are never used;
* the process can only open the generated file PCM, and PulseAudio/PipeWire are
  pointed at non-existent private paths, so audio cannot reach the speakers;
* the completed-sound checks inspect the captured samples, not log lines:
  enabled sound must produce non-silent samples, disabled sound must stay silent;
* physical audibility and visual quality still need a human. The script prints
  exactly which checks are automated and which are not.

It exercises the packaged application end to end: first launch, task CRUD,
timer autopause, history/restore, theme, language and geometry persistence,
always-on-top, and the enabled/disabled/corrupt/missing sound scenarios.

Usage:
    python3 tools/acceptance_check.py [--image build/dist/TaskFlow]
                                      [--keep] [--timeout 40]

Requires Linux with Xvfb, xdotool, ImageMagick's ``import``, ``xprop``
(``x11-utils``), ``ffmpeg`` and the ALSA ``file``/``null`` PCM plugins (part of
alsa-lib). The script exits non-zero if any automated check fails.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time
import zipfile
from pathlib import Path

from isolated_audio import AudioIsolationError, IsolatedAudio, wav_peak

REPO_ROOT = Path(__file__).resolve().parent.parent
LAUNCHER_REL = Path("bin") / "TaskFlow"
LANGUAGES = ["en", "es", "de", "fr", "it", "zh-Hans"]

# Scene layout constants (UIConstants.DEFAULT_WIDTH/HEIGHT and the fixed header,
# footer and card metrics in MainWindow/TaskCardView).
CARD_TOP = 62
CARD_PITCH = 107
CARD_ROW1 = 21
CARD_ROW2 = 56


class Failure(RuntimeError):
    pass


class Acceptance:
    def __init__(self, image: Path, timeout: int, keep: bool):
        self.image = image.resolve()
        self.launcher = self.image / LAUNCHER_REL
        self.timeout = timeout
        self.keep = keep
        self.root = Path(tempfile.mkdtemp(prefix="taskflow-accept-"))
        self.home = self.root / "home"
        self.xdg = self.root / "xdg"
        self.home.mkdir()
        self.xdg.mkdir()
        self.display = None
        self.xvfb = None
        self.app = None
        self.window = None
        self.audio: IsolatedAudio | None = None
        self.checks: list[tuple[str, bool, str]] = []
        self.log_path = self.root / "app.log"
        self._packaged_bell: Path | None = None

    # ------------------------------------------------------------------ infra
    def log(self, message: str) -> None:
        print(message, flush=True)

    def check(self, name: str, passed: bool, detail: str = "") -> None:
        self.checks.append((name, passed, detail))
        self.log(f"{'PASS' if passed else 'FAIL'}  {name}" + (f" — {detail}" if detail else ""))

    def run(self, args: list[str], env: dict[str, str] | None = None, timeout: int = 20) -> str:
        try:
            proc = subprocess.run(
                args,
                env=env or self.env(),
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                errors="replace",
                timeout=timeout,
            )
        except FileNotFoundError as error:
            raise Failure(f"required tool is missing: {args[0]}") from error
        if proc.returncode != 0:
            raise Failure(f"{' '.join(args)} failed ({proc.returncode}):\n{proc.stdout}")
        return proc.stdout

    def env(self) -> dict[str, str]:
        env = dict(self.audio.env()) if self.audio is not None else os.environ.copy()
        env.update(
            {
                "DISPLAY": self.display or ":0",
                "HOME": str(self.home),
                "XDG_DATA_HOME": str(self.xdg),
                "JAVA_TOOL_OPTIONS": f"-Duser.home={self.home}",
            }
        )
        return env

    def start_display(self) -> None:
        for number in range(90, 130):
            disp = f":{number}"
            lock = Path(f"/tmp/.X{number}-lock")
            if lock.exists():
                continue
            xvfb = subprocess.Popen(
                ["Xvfb", disp, "-screen", "0", "1280x1024x24", "-nolisten", "tcp"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            env = os.environ.copy()
            env["DISPLAY"] = disp
            for _ in range(50):
                if xvfb.poll() is not None:
                    break
                probe = subprocess.run(
                    ["xdotool", "getdisplaygeometry"],
                    env=env,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.DEVNULL,
                    text=True,
                )
                if probe.returncode == 0:
                    self.display = disp
                    self.xvfb = xvfb
                    return
                time.sleep(0.1)
            xvfb.kill()
        raise Failure("could not start a private Xvfb display")

    def packaged_bell(self) -> Path:
        """Extract the ``bell.mp3`` that ships inside the packaged application JAR.

        The audio checks must inspect the bytes that are actually distributed, not
        the repository source asset: a truncated or corrupt packaged resource has to
        fail here even though the working-tree copy is intact.
        """
        if self._packaged_bell is not None:
            return self._packaged_bell
        app_dir = self.image / "lib" / "app"
        jars = sorted(app_dir.glob("taskflow-*.jar"))
        if len(jars) != 1:
            raise Failure(f"expected exactly one taskflow JAR in {app_dir}, found {len(jars)}")
        try:
            with zipfile.ZipFile(jars[0]) as archive:
                data = archive.read("assets/bell.mp3")
        except (KeyError, zipfile.BadZipFile) as error:
            raise Failure(f"the packaged JAR has no readable assets/bell.mp3: {error}") from error
        target = self.root / "packaged-bell.mp3"
        target.write_bytes(data)
        self._packaged_bell = target
        return target

    def db(self) -> sqlite3.Connection:
        return sqlite3.connect(self.xdg / "TaskFlow" / "taskflow.db", timeout=5)

    def query(self, sql: str, params: tuple = ()) -> list[tuple]:
        with self.db() as conn:
            return list(conn.execute(sql, params))

    def execute(self, sql: str, params: tuple = ()) -> None:
        with self.db() as conn:
            conn.execute(sql, params)

    def stop_app(self) -> bool:
        if self.app is None:
            return True
        clean = False
        for attempt in range(3):
            try:
                ids = self.windows()
            except Failure:
                ids = [self.window] if self.window else []
            if not ids:
                break
            # The main window is the largest one; a leftover dialog would be smaller.
            def area(window: int) -> int:
                try:
                    _, _, w, h = self.geometry(window)
                    return w * h
                except Failure:
                    return 0

            self.window = max(ids, key=area)
            try:
                geo = self.geometry(self.window)
                if attempt:
                    self.log(f"  retrying close for window {self.window} at {geo[0]},{geo[1]}")
                # Dismiss any popup grab (menu/tooltip) before clicking the header.
                self.key("Escape")
                time.sleep(0.3)
                if attempt == 0:
                    self.click_window_relative(self.window, geo[2] - 26, 26)
                else:
                    self.click_raw(self.window, geo[2] - 26, 26)
            except Failure as error:
                self.log(f"  close click failed: {error}")
            try:
                self.app.wait(timeout=5)
                clean = True
                break
            except subprocess.TimeoutExpired:
                continue
        if not clean:
            self.app.kill()
            self.app.wait(timeout=10)
        self.app = None
        self.window = None
        return clean

    def launch(self) -> None:
        assert self.audio is not None
        # Cheap pre-flight: never start the application unless the private graph
        # still contains only the null sink.
        self.audio.assert_isolated()
        with self.log_path.open("a", encoding="utf-8") as log:
            log.write(f"\n===== launch =====\n")
            log.flush()
            self.app = subprocess.Popen(
                [str(self.launcher)],
                env=self.env(),
                stdout=log,
                stderr=subprocess.STDOUT,
            )
        deadline = time.time() + self.timeout
        while time.time() < deadline:
            if self.app.poll() is not None:
                raise Failure(f"application exited early; log:\n{self.tail_log()}")
            try:
                output = self.run(["xdotool", "search", "--class", "TaskFlowApp"])
            except Failure:
                output = ""
            ids = [int(line) for line in output.split() if line.strip().isdigit()]
            if ids:
                self.window = max(ids)
                time.sleep(1.0)
                return
            time.sleep(0.25)
        raise Failure(f"application window did not appear; log:\n{self.tail_log()}")

    def restart(self) -> None:
        self.stop_app()
        self.launch()

    def tail_log(self) -> str:
        try:
            return self.log_path.read_text(encoding="utf-8", errors="replace")[-4000:]
        except OSError:
            return "<no log>"

    # --------------------------------------------------------------- xdotool
    def geometry(self, window: int) -> tuple[int, int, int, int]:
        output = self.run(["xdotool", "getwindowgeometry", "--shell", str(window)])
        values = dict(re.findall(r"^(X|Y|WIDTH|HEIGHT)=(\d+)$", output, re.MULTILINE))
        if not values:
            raise Failure(f"could not read geometry of {window}")
        return (int(values["X"]), int(values["Y"]), int(values["WIDTH"]), int(values["HEIGHT"]))

    def windows(self) -> list[int]:
        output = self.run(["xdotool", "search", "--class", "TaskFlowApp"])
        return sorted(int(line) for line in output.split() if line.strip().isdigit())

    def click_raw(self, window: int, x: int, y: int) -> None:
        # A bare Xvfb server has no window manager, so focus and stacking must be
        # requested explicitly; X_SetInputFocus can also be rejected for a
        # mapped-but-unfocused window. Both failures are harmless for clicking.
        for command in ("windowraise", "windowfocus"):
            try:
                self.run(["xdotool", command, str(window)])
            except Failure:
                pass
        self.run(["xdotool", "mousemove", str(x), str(y)])
        time.sleep(0.15)
        self.run(["xdotool", "click", "1"])

    def click(self, x: int, y: int) -> None:
        assert self.window
        gx, gy, _, _ = self.geometry(self.window)
        self.click_raw(self.window, gx + x, gy + y)

    def click_window_relative(self, window: int, x: int, y: int) -> None:
        try:
            self.run(["xdotool", "windowraise", str(window)])
        except Failure:
            pass
        self.run(["xdotool", "mousemove", "--window", str(window), str(x), str(y)])
        time.sleep(0.15)
        self.run(["xdotool", "click", "1"])

    def key(self, *keys: str) -> None:
        self.run(["xdotool", "key", *keys])

    def type_text(self, text: str) -> None:
        self.run(["xdotool", "type", "--delay", "15", text])

    def dialog(self) -> tuple[int, int, int, int, int]:
        deadline = time.time() + 5
        while time.time() < deadline:
            ids = self.windows()
            if len(ids) >= 2:
                dialog = max(ids)
                x, y, w, h = self.geometry(dialog)
                return dialog, x, y, w, h
            time.sleep(0.15)
        raise Failure("modal dialog did not appear")

    def fill_dialog(self, text: str | None = None, minutes: int | None = None) -> None:
        dialog, dx, dy, dw, dh = self.dialog()
        if text is not None:
            self.click_raw(dialog, dx + dw // 2, dy + 74)
            # Let JavaFX focus the field before typing; without this the first
            # characters can be delivered before the TextField owns the keys.
            time.sleep(0.4)
            self.type_text(text)
        if minutes is not None:
            self.click_raw(dialog, dx + dw // 2, dy + 74)
            self.key("Tab", "ctrl+a")
            self.type_text(str(minutes))
        self.key("Return")
        deadline = time.time() + 5
        while time.time() < deadline and len(self.windows()) >= 2:
            time.sleep(0.1)

    def add_task(self, name: str) -> None:
        assert self.window
        for _ in range(3):
            _, _, w, h = self.geometry(self.window)
            self.click(w // 2, h - 29)
            try:
                self.dialog()
                break
            except Failure:
                time.sleep(0.5)
        else:
            raise Failure("could not open the add-task dialog")
        self.fill_dialog(text=name)

    def row(self, index: int) -> tuple[int, int]:
        return CARD_TOP + CARD_PITCH * index + CARD_ROW2

    def click_play(self, index: int) -> None:
        _, _, w, _ = self.geometry(self.window)
        self.click(w - 40, self.row(index))

    def click_done(self, index: int) -> None:
        _, _, w, _ = self.geometry(self.window)
        self.click(w - 78, self.row(index))

    def click_edit(self, index: int) -> None:
        _, _, w, _ = self.geometry(self.window)
        self.click(w - 70, CARD_TOP + CARD_PITCH * index + CARD_ROW1)

    def click_delete(self, index: int) -> None:
        _, _, w, _ = self.geometry(self.window)
        self.click(w - 38, CARD_TOP + CARD_PITCH * index + CARD_ROW1)

    def open_history(self) -> None:
        _, _, w, h = self.geometry(self.window)
        self.click(w - 138, 26)

    def click_restore(self) -> None:
        dialog, dx, dy, dw, dh = self.dialog()
        self.click_raw(dialog, dx + dw - 85, dy + 110)

    # ----------------------------------------------------------------- checks
    def check_first_launch(self) -> None:
        assert self.window
        gx, gy, w, h = self.geometry(self.window)
        self.check("first launch opens a window", w >= 300 and h >= 420, f"{w}x{h} at {gx},{gy}")
        version = self.query("PRAGMA user_version")[0][0]
        self.check("schema user_version is 2", version == 2, str(version))
        bell = self.xdg / "TaskFlow" / "bell.mp3"
        self.check("default bell is provisioned", bell.is_file() and bell.stat().st_size > 1000)
        packaged = self.packaged_bell()
        if bell.is_file():
            same = hashlib.sha256(bell.read_bytes()).digest() == hashlib.sha256(
                packaged.read_bytes()
            ).digest()
            self.check("provisioned bell matches the packaged asset", same)

    def check_add_and_timers(self) -> None:
        self.add_task("Acceptance task")
        self.add_task("Second task")
        rows = self.query("SELECT name, remaining_seconds FROM tasks ORDER BY id")
        self.check("adding tasks through the UI persists both", len(rows) == 2, str(rows))

        self.click_play(0)
        time.sleep(2)
        self.click_play(1)
        time.sleep(1)
        first = self.query("SELECT remaining_seconds FROM tasks ORDER BY id")[0][0]
        time.sleep(3)
        after = self.query("SELECT remaining_seconds FROM tasks ORDER BY id")
        self.check(
            "single active timer pauses the first task",
            first == after[0][0] and after[1][0] < 1500,
            f"first {first}->{after[0][0]}, second {after[1][0]}",
        )

        self.click_edit(0)
        self.fill_dialog(minutes=3)
        # The dialog closes before the persistence write lands; poll instead of
        # assuming the row is updated by the time the click returns.
        deadline = time.time() + 10
        edited = (0, 0)
        while time.time() < deadline:
            edited = self.query("SELECT total_seconds, remaining_seconds FROM tasks ORDER BY id")[0]
            if edited[0] == 180:
                break
            time.sleep(0.2)
        self.check("editing a task updates its duration", edited[0] == 180, str(edited))

    def check_history(self) -> None:
        self.click_done(0)
        time.sleep(0.5)
        history = self.query("SELECT event_type, completed_manually FROM history")
        self.check(
            "manual completion archives completed + completed_manually",
            history == [("completed", 1)],
            str(history),
        )
        self.check("manual completion removes the active task", len(self.query("SELECT id FROM tasks")) == 1)

        self.open_history()
        self.click_restore()
        time.sleep(1)
        tasks = self.query("SELECT name FROM tasks ORDER BY id")
        self.check("restoring history creates a task", len(tasks) == 2, str(tasks))
        self.check("restoring history removes the history row", not self.query("SELECT id FROM history"))

        self.click_delete(0)
        time.sleep(0.5)
        deleted = self.query("SELECT event_type FROM history")
        self.check("delete archives deleted", deleted == [("deleted",)], str(deleted))

    def check_theme(self) -> None:
        before = self.query("SELECT pref_value FROM preferences WHERE pref_key='theme'")[0][0]
        image_before = self.root / "theme-before.png"
        image_after = self.root / "theme-after.png"
        assert self.window
        self.run(["import", "-window", str(self.window), str(image_before)])
        _, _, w, _ = self.geometry(self.window)
        self.click(w - 110, 26)
        time.sleep(1)
        after = self.query("SELECT pref_value FROM preferences WHERE pref_key='theme'")[0][0]
        self.run(["import", "-window", str(self.window), str(image_after)])
        self.check("theme toggle persists", before != after, f"{before} -> {after}")
        same = image_before.read_bytes() == image_after.read_bytes()
        self.check("theme toggle repaints the window", not same)

    def check_pin(self) -> None:
        before = self.query("SELECT pref_value FROM preferences WHERE pref_key='always_on_top'")[0][0]
        _, _, w, _ = self.geometry(self.window)
        self.click(w - 166, 26)
        time.sleep(0.5)
        states = self.run(["xprop", "-id", str(self.window), "_NET_WM_STATE"])
        pinned = self.query("SELECT pref_value FROM preferences WHERE pref_key='always_on_top'")[0][0]
        above = "_NET_WM_STATE_ABOVE" in states
        self.check(
            "always-on-top toggle updates the window and preference",
            before != pinned and above == (pinned == "true"),
            f"{before} -> {pinned}, above={above}",
        )

    def check_languages(self) -> None:
        for index, tag in enumerate(LANGUAGES):
            _, _, w, _ = self.geometry(self.window)
            self.click(w - 82, 26)
            time.sleep(0.4)
            self.key("Home")
            for _ in range(index):
                self.key("Down")
            self.key("Return")
            time.sleep(0.8)
            stored = self.query("SELECT pref_value FROM preferences WHERE pref_key='language'")[0][0]
            self.check(f"language switch to {tag} persists", stored == tag, stored)
        self.restart()
        stored = self.query("SELECT pref_value FROM preferences WHERE pref_key='language'")[0][0]
        self.check("language choice survives a restart", stored == LANGUAGES[-1], stored)

    def check_geometry(self) -> None:
        assert self.window
        self.run(["xdotool", "windowmove", str(self.window), "120", "90"])
        self.run(["xdotool", "windowsize", str(self.window), "380", "600"])
        time.sleep(1.2)
        self.restart()
        gx, gy, w, h = self.geometry(self.window)
        saved = dict(self.query("SELECT pref_key, pref_value FROM preferences"))
        ok = (
            abs(w - 380) <= 2
            and abs(h - 600) <= 2
            and abs(gx - float(saved["geo_x"])) <= 2
            and abs(gy - float(saved["geo_y"])) <= 2
        )
        self.check("geometry is saved and restored", ok, f"window {w}x{h} at {gx},{gy}")

    def prepare_audio_task(self, remaining: int = 2, enabled: bool = True) -> None:
        self.stop_app()
        task = self.query("SELECT id FROM tasks ORDER BY id LIMIT 1")
        if task:
            self.execute("UPDATE tasks SET remaining_seconds=? WHERE id=?", (remaining, task[0][0]))
        else:
            raise Failure("audio check requires an active task")
        self.execute(
            "INSERT INTO preferences(pref_key,pref_value) VALUES('sound_enabled',?) "
            "ON CONFLICT(pref_key) DO UPDATE SET pref_value=excluded.pref_value",
            ("true" if enabled else "false",),
        )

    def played_sound_count(self) -> int:
        try:
            text = self.log_path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return 0
        return text.count("Played completion sound")

    def check_bell_asset(self) -> None:
        """Decode the packaged bell (no playback) and require non-silent PCM."""
        decoded = self.root / "bell-decoded.wav"
        try:
            source = self.packaged_bell()
            self.run(
                ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                 "-i", str(source), str(decoded)],
                env=os.environ.copy(),
                timeout=30,
            )
            peak = wav_peak(decoded)
            self.check(
                "packaged bell.mp3 decodes to non-silent audio",
                peak > 300,
                f"peak {peak}/32767 from {len(source.read_bytes())} packaged bytes",
            )
        except (Failure, OSError) as error:
            self.check("packaged bell.mp3 decodes to non-silent audio", False, str(error))

    def play_completion(self, hold: float = 2.5) -> int:
        """Complete the first task and return the peak captured by the ALSA device."""
        assert self.audio is not None
        before = self.query("SELECT COUNT(*) FROM history")[0][0]
        self.click_play(0)
        deadline = time.time() + 20
        while time.time() < deadline:
            if self.query("SELECT COUNT(*) FROM history")[0][0] > before:
                break
            time.sleep(0.2)
        else:
            raise Failure("timer did not complete during the audio check")
        # Let the short notification finish writing to the isolated PCM file.
        time.sleep(hold)
        return self.audio.capture.peak()

    def check_audio(self) -> None:
        # Every assertion below inspects the samples written by the isolated ALSA
        # file PCM. A log line or a history row alone is never proof of output.
        assert self.audio is not None
        self.check_bell_asset()

        # Enabled: the completion must reach the isolated device with sound.
        self.prepare_audio_task(enabled=True)
        self.audio.begin_capture()
        before = self.played_sound_count()
        self.launch()
        peak = self.play_completion()
        self.check(
            "enabled completion sound is dispatched",
            self.played_sound_count() > before,
            f"{before} -> {self.played_sound_count()}",
        )
        self.check(
            "enabled completion produces non-silent captured samples",
            peak > 100,
            f"peak {peak}/32767 from {self.audio.capture.path.name}",
        )

        # Disabled: no playback at all, therefore no captured samples.
        self.prepare_audio_task(enabled=False)
        self.audio.begin_capture()
        before = self.played_sound_count()
        self.launch()
        peak = self.play_completion()
        self.check(
            "disabled sound dispatches no playback",
            self.played_sound_count() == before,
            f"{before} -> {self.played_sound_count()}",
        )
        self.check(
            "disabled sound captures no samples",
            peak == 0 and self.audio.capture.frames() == 0,
            f"peak {peak}/32767, {self.audio.capture.frames()} frames",
        )

        # Corrupt user sound: the application must survive, archive the task and
        # still ring through the bundled fallback (never crash or go silent).
        self.prepare_audio_task(enabled=True)
        (self.xdg / "TaskFlow" / "bell.mp3").write_bytes(b"not an mp3")
        self.audio.begin_capture()
        self.launch()
        peak = self.play_completion()
        alive = self.app is not None and self.app.poll() is None
        archived = len(self.query("SELECT id FROM history")) >= 1
        self.check("corrupt bell.mp3 does not crash the application", alive and archived)
        self.check(
            "corrupt bell.mp3 falls back to non-silent bundled audio",
            peak > 100,
            f"peak {peak}/32767",
        )

        # Missing user sound: the default is provisioned again on startup and
        # its completion must produce captured samples.
        self.stop_app()
        (self.xdg / "TaskFlow" / "bell.mp3").unlink()
        task = self.query("SELECT id FROM tasks ORDER BY id LIMIT 1")
        self.execute("UPDATE tasks SET remaining_seconds=2 WHERE id=?", (task[0][0],))
        self.audio.begin_capture()
        self.launch()
        bell = self.xdg / "TaskFlow" / "bell.mp3"
        reprovisioned = bell.is_file() and bell.stat().st_size > 1000
        self.check("deleted bell.mp3 is provisioned again on restart", reprovisioned)
        before = self.played_sound_count()
        peak = self.play_completion()
        self.check(
            "re-provisioned bell is dispatched on completion",
            reprovisioned and self.played_sound_count() > before,
        )
        self.check(
            "re-provisioned bell produces non-silent captured samples",
            peak > 100,
            f"peak {peak}/32767",
        )

    def run_all(self) -> None:
        self.log(f"image: {self.image}")
        self.log(f"isolated root: {self.root}")
        self.start_display()
        self.log(f"private display: {self.display}")
        try:
            self.audio = IsolatedAudio(self.root).start()
        except AudioIsolationError as error:
            raise Failure(f"could not prepare the isolated audio device: {error}") from error
        self.log(f"private audio device: {self.audio.capture.config}")
        self.launch()
        self.check_first_launch()
        self.check_add_and_timers()
        self.check_history()
        self.check_theme()
        self.check_pin()
        self.check_languages()
        self.check_geometry()
        self.check_audio()
        self.check("the close button shuts the application down normally", self.stop_app())

    def close(self) -> None:
        self.stop_app()
        if self.audio is not None:
            self.audio.close()
            self.audio = None
        if self.xvfb is not None:
            self.xvfb.terminate()
            try:
                self.xvfb.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.xvfb.kill()
        if not self.keep:
            shutil.rmtree(self.root, ignore_errors=True)

    def report(self) -> int:
        passed = sum(1 for _, ok, _ in self.checks if ok)
        failed = [name for name, ok, _ in self.checks if not ok]
        self.log("")
        self.log(f"{passed}/{len(self.checks)} automated checks passed")
        if failed:
            self.log("failed: " + ", ".join(failed))
            self.log("application log: " + str(self.log_path))
            return 1
        self.log(
            "Samples were captured from the isolated ALSA device. Physical audibility "
            "(speakers/headphones) and interactive visual review are NOT automated."
        )
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, default=REPO_ROOT / "build" / "dist" / "TaskFlow")
    parser.add_argument("--timeout", type=int, default=40)
    parser.add_argument("--keep", action="store_true", help="keep the temporary profile")
    args = parser.parse_args()

    if not (args.image / LAUNCHER_REL).is_file():
        print(f"not a packaged application image: {args.image}", file=sys.stderr)
        return 2
    for tool in ("Xvfb", "xdotool", "import", "xprop", "ffmpeg"):
        if shutil.which(tool) is None:
            print(f"required tool is missing: {tool}", file=sys.stderr)
            return 2

    acceptance = Acceptance(args.image, args.timeout, args.keep)
    try:
        acceptance.run_all()
    except Failure as error:
        print(f"ACCEPTANCE CHECK FAILED: {error}", file=sys.stderr)
        acceptance.close()
        return 1
    try:
        return acceptance.report()
    finally:
        acceptance.close()


if __name__ == "__main__":
    sys.exit(main())
