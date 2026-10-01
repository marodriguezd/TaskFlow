# TaskFlow v1.1.0 — Internationalized Java 21 Desktop Task Manager

TaskFlow 1.1.0 adds full internationalization to the Java 21 + JavaFX desktop task manager. The entire interface now ships in five languages, switchable at runtime.

## Highlights

- **Five UI languages** — English, Spanish, German, Italian, and Simplified Chinese. Every button, label, tooltip, prompt, and history description is translated through resource bundles.
- **Live language switching** — change the language anytime from the globe button in the header; the UI re-renders immediately, no restart required.
- **Automatic first-run detection** — on first launch the interface language is detected from the OS locale, then persisted. Unsupported OS languages fall back to English; Traditional Chinese locales fall back to English (only Simplified Chinese is shipped).
- **Persistent language preference** — stored locally per user in the SQLite `preferences` table as a locale-independent BCP 47 tag. Existing 1.0.0 databases upgrade in place with no migration step.
- **Deterministic English fallback** — any missing translation resolves to the English base bundle; the build enforces that exactly five bundles exist and are complete.
- **Task management** — create, edit, complete, and delete tasks with High / Medium / Low priorities
- **Pomodoro timers** — per-task countdown timers with a single-active-timer invariant (starting a timer automatically pauses any other running timer)
- **Progress tracking** — per-task visual progress bar
- **History & restoration** — completed and deleted tasks are archived and can be restored at any time
- **SQLite persistence** — tasks, history, and preferences are stored locally in a SQLite database; no account or cloud required
- **Themes & preferences** — light/dark theme switching, always-on-top mode, and window geometry persistence across sessions
- **Audio notification** — a bell chime plays when a timer completes (user-replaceable `bell.mp3`)
- **Legacy data migration** — existing JSON data from the legacy Python version is imported automatically on first launch

## Native packages with bundled Java

Installers are produced with `jpackage` and **bundle a full Java 21 runtime**, so end users do **not** need to install Java.

| Platform | Package | Notes |
| --- | --- | --- |
| Windows (x64) | `TaskFlow-1.1.0.msi` | MSI installer with Start Menu shortcut |
| Linux (x64, any distribution) | `TaskFlow-1.1.0-x86_64.AppImage` | Portable AppImage — download, `chmod +x`, and run; no installation, no FUSE required (falls back to `--appimage-extract-and-run`) |
| macOS (Apple Silicon) | `TaskFlow-1.1.0.dmg` | DMG image built on an ARM64 runner (not a universal binary) |

Verify your download before installing:

```bash
sha256sum -c checksums.txt
```

## Supported platforms

- **Windows 10/11** (x64) — `.msi`
- **Linux** (x64, any distribution) — `.AppImage`
- **macOS (Apple Silicon)** — `.dmg`

Continuous integration builds, tests, and packages the application on all three platforms on every change.

## Upgrading from 1.0.0

Just install the new package over the previous one — your data is untouched. The database schema is unchanged; the only addition is the `language` preference key, written the first time a language is resolved (from the OS locale on first run, or when you pick one from the header).

## Upgrading from the legacy (Python) version

If you used TaskFlow before the Java rewrite, the application detects your legacy JSON files on first launch and imports them into SQLite automatically:

- `taskflow_data.json` (tasks)
- `taskflow_history.json` (history)
- `taskflow_geometry.json` (window geometry)
- `taskflow_settings.json` (theme and window preferences)

Legacy files are looked up in the application data directory and in the home directory root (for example `~/.taskflow_data.json`). Each processed file is renamed with a `.migrated` suffix so it is imported only once; the original contents are preserved as a backup. Migration failures are logged and never prevent the application from starting. Settings migration preserves any already-stored language and sound preferences.

## Data locations

| Platform | Data directory |
| --- | --- |
| Windows | `%APPDATA%\TaskFlow` |
| macOS | `~/Library/Application Support/TaskFlow` |
| Linux | `$XDG_DATA_HOME/TaskFlow`, falling back to `~/.TaskFlow` |

The directory contains the SQLite database (`taskflow.db`) and the notification sound (`bell.mp3`).

## Known limitations

- The interface ships in five languages only; French and Portuguese are not supported in this release.
- The Linux package ships as a portable `.AppImage`; there is no native `.deb`/`.rpm` repository integration (menu/shortcut registration depends on the desktop environment).
- The macOS package targets Apple Silicon; no Intel or universal build is provided.
- The Windows package is x64 only.

## License

TaskFlow v1.1.0 is released under the [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License (CC BY-NC-SA 4.0)](LICENSE).
