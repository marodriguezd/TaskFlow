# Manual acceptance checklist

This file separates what the repository verifies automatically from what still needs a
person. Do not treat a green CI run as proof of audible sound, visual quality, or
installer data preservation.

## Automated evidence (no human needed)

- **CI (Ubuntu, Windows, macOS)** — unit/integration tests, schema v1 → v2 upgrade tests,
  Spotless, JaCoCo threshold, packaged resource inventory, and a real launch/shutdown of
  the packaged image. Linux also runs the AppImage; macOS copies the app out of the DMG;
  Windows installs, launches, and uninstalls the MSI.
- **`python3 tools/acceptance_check.py`** (Linux; requires `Xvfb`, `xdotool`, ImageMagick
  `import`) — drives the **packaged** application on a private Xvfb display with a
  throw-away home/data directory and an ALSA `file` PCM, so the real desktop, mouse, audio
  device and default sink are never touched. It automates:
  - first launch, schema `user_version = 2`, bell provisioning and asset hash;
  - add, edit, single-active-timer autopause, manual complete, delete and restore;
  - theme, always-on-top, all six languages, and geometry persistence across restarts;
  - the enabled/disabled/corrupt/missing `bell.mp3` decision paths (playback dispatch and
    resilience) plus decoding the packaged bell to non-silent PCM.

  It does **not** prove physical audibility or visual quality. JavaFX media cannot create
  a player on the headless ALSA file device, which is why the sound checks assert dispatch
  and application behavior rather than captured speaker output.

Use a disposable copy of the data directory for any check that modifies user files, and
restore it afterwards. Back up `taskflow.db` before editing it.

## Physical audio

A human at a real audio device is required. The application currently has **no in-app
sound on/off control**; the persisted `sound_enabled` preference is the only switch (the
automated harness sets it directly).

- [ ] With sound enabled (default), let a task expire (or finish it) and confirm the bell is
      audible exactly once.
- [ ] Disable sound in a disposable profile:
      `sqlite3 "<data directory>/taskflow.db" "INSERT INTO preferences(pref_key, pref_value) VALUES('sound_enabled','false') ON CONFLICT(pref_key) DO UPDATE SET pref_value='false';"`
      restart, finish another task, and confirm silence.
- [ ] Replace `<data directory>/bell.mp3` with a different readable MP3, restart, and confirm
      the completion uses the replacement.
- [ ] Replace it with a corrupt file, finish a task, and confirm the application does not
      crash (a warning and/or the fallback beep is acceptable).
- [ ] Delete `bell.mp3`, restart, and confirm the bundled default is re-provisioned (the
      automated harness checks this) and plays.

## Interactive GUI

- [ ] Add, edit, complete, delete, and restore tasks; confirm history entries appear and
      restoring a history item moves it back to the active list without duplicating it.
      (Add/edit/complete/delete/restore are automated on Linux; confirm the rendered
      dialogs and cards look correct.)
- [ ] Start two timers in sequence and confirm the first pauses (single active timer).
      (Automated via the persisted remaining time.)
- [ ] Switch through all six languages (English, Spanish, German, French, Italian, Simplified
      Chinese); confirm open dialogs close, the live UI updates, and the choice survives a
      restart. (Persistence and switching are automated; confirm the rendered text.)
- [ ] Toggle dark/light theme and confirm every open window updates without a restart.
      (Persistence and repaint are automated; visual quality is human.)
- [ ] Toggle always-on-top, move and resize the window, adjust the glass opacity slider, close
      the app, and confirm all four survive a restart. (Window state, geometry and opacity
      recording are automated; confirm the visual result.)
- [ ] Upgrade a copy of an existing v1 database: open it once with this build, then confirm
      every task, history entry, preference, geometry value, language, and sound setting is
      unchanged and `PRAGMA user_version` is `2`. CI covers v1 → v2 preservation; repeat on a
      real copy if your data matters.

## Packaging per platform

- [ ] **Windows MSI** — install, launch, create a task, close, then uninstall. Confirm
      `%APPDATA%\TaskFlow` (database, `bell.mp3`) still exists and contains the task afterwards.
      CI verifies install/launch/uninstall, but not post-uninstall data preservation.
- [ ] **macOS DMG** — accept the license, drag `TaskFlow.app` to Applications, launch, create a
      task, quit, then remove the app. Confirm `~/Library/Application Support/TaskFlow` still
      contains the database and sound. CI verifies a copied install, not removal.
- [ ] **Linux AppImage** — run it with FUSE and with `--appimage-extract-and-run`, launch, create
      a task, and quit. Confirm `$XDG_DATA_HOME/TaskFlow` (or `~/.TaskFlow`) is preserved after
      the extracted image is deleted. Point `XDG_DATA_HOME` at a temporary directory to avoid
      touching your real data.
- [ ] Launch each package once with no prior configuration and confirm the window renders, the
      language defaults sensibly, and no fatal startup error is logged.

## Signing and notarization

The repository has no code-signing/notarization credentials configured, so packages are not
signed. Verify signature/notarization only if you have the platform certificates and
tooling; never fabricate signing evidence or commit secrets.
