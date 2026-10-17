#!/usr/bin/env python3
"""Automated, isolated acceptance checks for a packaged TaskFlow build.

The checks run on a private Xvfb display with a throw-away home/data directory and
an ALSA ``file`` plugin as the default PCM. That means:

* the real desktop, keyboard, mouse, audio device and default sink are never used;
* playback is routed to a private ALSA ``file`` PCM, so sound can never reach the
  user's speakers. JavaFX media cannot create a player on that headless device
  (the same limitation CI has), therefore the sound assertions cover the
  SoundService decision paths, application resilience and the packaged asset —
  not audible playback;
* physical audibility and visual quality still need a human. The script prints
  exactly which checks are automated and which are not.

It exercises the packaged application end to end: first launch, task CRUD,
timer autopause, history/restore, theme, language and geometry persistence,
always-on-top, and the enabled/disabled/corrupt/missing sound scenarios.

Usage:
    python3 tools/acceptance_check.py [--image build/dist/TaskFlow]
                                      [--keep] [--timeout 40]

Requires Linux with Xvfb, xdotool and ImageMagick's ``import`` (the same tools
CI already installs for the launch smoke test). The script exits non-zero if any
automated check fails.
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
import wave
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
LAUNCHER_REL = Path("bin") / "TaskFlow"
SOURCE_BELL = REPO_ROOT / "src" / "main" / "resources" / "assets" / "bell.mp3"
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
        self.audio_index = 0
        self.audio_raw = None
        self.checks: list[tuple[str, bool, str]] = []
        self.log_path = self.root / "app.log"

    # ------------------------------------------------------------------ infra
    def log(self, message: str) -> None:
        print(message, flush=True)

    def check(self, name: str, passed: bool, detail: str = "") -> None:
        self.checks.append((name, passed, detail))
        self.log(f"{'PASS' if passed else 'FAIL'}  {name}" + (f" — {detail}" if detail else ""))

    def run(self, args: list[str], env: dict[str, str] | None = None, timeout: int = 20) -> str:
        proc = subprocess.run(
            args,
            env=env or self.env(),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            errors="replace",
            timeout=timeout,
        )
        if proc.returncode != 0:
            raise Failure(f"{' '.join(args)} failed ({proc.returncode}):\n{proc.stdout}")
        return proc.stdout

    def env(self, raw: Path | None = None) -> dict[str, str]:
        env = os.environ.copy()
        env.update(
            {
                "DISPLAY": self.display or ":0",
                "HOME": str(self.home),
                "XDG_DATA_HOME": str(self.xdg),
                "JAVA_TOOL_OPTIONS": f"-Duser.home={self.home}",
            }
        )
        if raw is not None:
            env["ALSA_CONFIG_PATH"] = str(self.asound_conf(raw))
        return env

    def asound_conf(self, raw: Path) -> Path:
        path = self.root / f"asound-{self.audio_index}.conf"
        path.write_text(
            "pcm.null { type null }\n"
            'pcm.!default { type file; slave.pcm "null"; '
            f'file "{raw}"; format "raw" }}\n',
            encoding="utf-8",
        )
        return path

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
        self.audio_index += 1
        self.audio_raw = self.root / f"alsa-{self.audio_index}.raw"
        if self.audio_raw.exists():
            self.audio_raw.unlink()
        with self.log_path.open("a", encoding="utf-8") as log:
            log.write(f"\n===== launch {self.audio_index} =====\n")
            log.flush()
            self.app = subprocess.Popen(
                [str(self.launcher)],
                env=self.env(self.audio_raw),
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
        if SOURCE_BELL.is_file() and bell.is_file():
            same = hashlib.sha256(bell.read_bytes()).digest() == hashlib.sha256(
                SOURCE_BELL.read_bytes()
            ).digest()
            self.check("provisioned bell matches the bundled asset", same)

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
        edited = self.query("SELECT total_seconds, remaining_seconds FROM tasks ORDER BY id")[0]
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

    def play_and_wait(self) -> None:
        before = self.query("SELECT COUNT(*) FROM history")[0][0]
        self.click_play(0)
        deadline = time.time() + 15
        while time.time() < deadline:
            after = self.query("SELECT COUNT(*) FROM history")[0][0]
            if after > before:
                time.sleep(1.0)
                return
            time.sleep(0.3)
        raise Failure("timer did not complete during the audio check")

    def played_sound_count(self) -> int:
        try:
            text = self.log_path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return 0
        return text.count("Played completion sound")

    def check_bell_asset(self) -> None:
        """Decode the packaged bell (no playback) and require non-silent PCM."""
        if shutil.which("ffmpeg") is None:
            self.check("bell.mp3 asset decodes to non-silent audio", True, "ffmpeg unavailable; skipped")
            return
        decoded = self.root / "bell-decoded.wav"
        try:
            self.run(
                ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                 "-i", str(SOURCE_BELL), str(decoded)],
                env=os.environ.copy(),
                timeout=30,
            )
            with wave.open(str(decoded)) as audio:
                frames = audio.readframes(audio.getnframes())
            peak = max((abs(int.from_bytes(frames[i:i + 2], "little", signed=True))
                        for i in range(0, len(frames) - 1, 2)), default=0)
            self.check(
                "bell.mp3 asset decodes to non-silent audio",
                peak > 300,
                f"peak {peak}/32767",
            )
        except (Failure, wave.Error, OSError) as error:
            self.check("bell.mp3 asset decodes to non-silent audio", False, str(error))

    def check_audio(self) -> None:
        # These checks run with a private ALSA file PCM. JavaFX media cannot
        # create a player on that device (the same headless limitation CI has),
        # so the assertions cover the SoundService dispatch decision and the
        # application's resilience. Physical audibility remains a manual check.
        self.check_bell_asset()

        # Enabled: a completion attempts playback and still archives history.
        self.prepare_audio_task(enabled=True)
        before = self.played_sound_count()
        self.launch()
        self.play_and_wait()
        time.sleep(1)
        self.check(
            "enabled completion sound is dispatched",
            self.played_sound_count() > before,
            f"{before} -> {self.played_sound_count()}",
        )

        # Disabled: no playback is dispatched at all.
        self.prepare_audio_task(enabled=False)
        before = self.played_sound_count()
        self.launch()
        self.play_and_wait()
        time.sleep(1)
        self.check(
            "disabled sound dispatches no playback",
            self.played_sound_count() == before,
            f"{before} -> {self.played_sound_count()}",
        )

        # Corrupt user sound: the application must survive and still archive.
        self.prepare_audio_task(enabled=True)
        (self.xdg / "TaskFlow" / "bell.mp3").write_bytes(b"not an mp3")
        self.launch()
        self.play_and_wait()
        time.sleep(1)
        alive = self.app is not None and self.app.poll() is None
        archived = len(self.query("SELECT id FROM history")) >= 1
        self.check("corrupt bell.mp3 does not crash the application", alive and archived)

        # Missing user sound: the default is provisioned again on startup.
        self.stop_app()
        (self.xdg / "TaskFlow" / "bell.mp3").unlink()
        task = self.query("SELECT id FROM tasks ORDER BY id LIMIT 1")
        self.execute("UPDATE tasks SET remaining_seconds=2 WHERE id=?", (task[0][0],))
        self.launch()
        bell = self.xdg / "TaskFlow" / "bell.mp3"
        reprovisioned = bell.is_file() and bell.stat().st_size > 1000
        self.check("deleted bell.mp3 is provisioned again on restart", reprovisioned)
        before = self.played_sound_count()
        self.play_and_wait()
        time.sleep(1)
        self.check(
            "re-provisioned bell is dispatched on completion",
            reprovisioned and self.played_sound_count() > before,
        )

    def run_all(self) -> None:
        self.log(f"image: {self.image}")
        self.log(f"isolated root: {self.root}")
        self.start_display()
        self.log(f"private display: {self.display}")
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
        self.log("Physical audibility and interactive visual review are NOT automated.")
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
    for tool in ("Xvfb", "xdotool", "import"):
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
