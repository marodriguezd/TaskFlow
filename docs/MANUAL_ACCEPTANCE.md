# Manual acceptance checklist

Automated CI verifies unit/integration behavior, the packaged resource inventory, and a real
launch/shutdown of the packaged application (including AppImage, DMG copy-install, and MSI
install/uninstall). CI runners have no physical audio device and no human at the display, so the
checks below still require a person. None of them are claimed as automated coverage.

Use a disposable copy of the data directory when a check asks you to modify user files, and restore
it afterwards.

## Physical audio

- [ ] With sound enabled, let a task expire (or finish one) and confirm the bell is audible exactly
      once.
- [ ] Disable sound in the UI, finish another task, and confirm silence.
- [ ] Replace `<data directory>/bell.mp3` with a different readable MP3, restart, and confirm the
      completion uses the replacement.
- [ ] Replace it with a corrupt file, finish a task, and confirm the application does not crash
      (a warning and/or the fallback beep is acceptable).
- [ ] Delete `bell.mp3`, restart, and confirm the bundled default is re-provisioned and plays.

## Interactive GUI

- [ ] Add, edit, complete, delete, and restore tasks; confirm history entries appear and restoring a
      history item moves it back to the active list without duplicating it.
- [ ] Start two timers in sequence and confirm the first pauses (single active timer).
- [ ] Switch through all six languages (English, Spanish, German, French, Italian, Simplified
      Chinese); confirm open dialogs close, the live UI updates, and the choice survives a restart.
- [ ] Toggle dark/light theme and confirm every open window updates without a restart.
- [ ] Toggle always-on-top, move and resize the window, adjust the glass opacity slider, close the
      app, and confirm all four survive a restart.
- [ ] Upgrade a copy of an existing v1 database: confirm every task, history entry, preference,
      geometry value, language, and sound setting is unchanged and `PRAGMA user_version` is `2`.

## Packaging per platform

- [ ] **Windows MSI** — install, launch, create a task, close, then uninstall. Confirm
      `%APPDATA%\TaskFlow` (database, `bell.mp3`) still exists and contains the task afterwards.
- [ ] **macOS DMG** — accept the license, drag `TaskFlow.app` to Applications, launch, create a
      task, quit, then remove the app. Confirm `~/Library/Application Support/TaskFlow` still
      contains the database and sound.
- [ ] **Linux AppImage** — run it with FUSE and with `--appimage-extract-and-run`, launch, create a
      task, and quit. Confirm `$XDG_DATA_HOME/TaskFlow` (or `~/.TaskFlow`) is preserved after the
      extracted image is deleted.
- [ ] Launch each package once with no prior configuration and confirm the window renders, the
      language defaults sensibly, and no fatal startup error is logged.
