# TaskFlow v1.1.2 — Dependency & Build Maintenance Release

TaskFlow 1.1.2 is a maintenance release with no application behavior changes: no new features, no database schema changes, and no data migration. It refreshes the dependency and build toolchain to current stable releases and stays on the Java 21 / JavaFX 21 LTS lines.

## Dependency and toolchain updates

- **Gradle** 8.10.2 → 9.8.0 (via wrapper)
- **JavaFX** 21.0.4 → 21.0.12 (latest in the JavaFX 21 LTS line matching JDK 21)
- **SQLite JDBC** 3.47.2.0 → 3.53.4.0
- **SLF4J** 2.0.16 → 2.0.20, **Logback** 1.5.16 → 1.6.5
- **Jackson** (`jackson-databind`, `jackson-datatype-jsr310`) 2.18.2 → 2.22.3
- **JUnit** 5.11.4 → 5.14.4 (with an explicit `junit-platform-launcher` 1.14.4), **AssertJ** 3.27.0 → 3.27.7
- **Spotless** 7.0.2 → 8.10.3 with **Google Java Format** 1.24.0 → 1.36.1 (AOSP style), **JaCoCo** 0.8.12 → 0.8.15
- **CI/release actions** refreshed to current major versions, all pinned to immutable SHAs
- **Build maintenance** — the `releaseStamp` provenance task now captures the project version at configuration time (Gradle 9 future-proofing; the build reports zero deprecation warnings), and `gradle.properties` carries the `--add-exports` JVM flags Google Java Format requires on JDK 16+

## Native packages with bundled Java

Installers are produced with `jpackage` and **bundle a full Java 21 runtime**, so end users do **not** need to install Java.

| Platform | Package | Notes |
| --- | --- | --- |
| Windows (x64) | `TaskFlow-1.1.2.msi` | MSI installer with Start Menu shortcut |
| Linux (x86_64) | `TaskFlow-1.1.2-x86_64.AppImage` | AppImage with bundled Java runtime; requires a compatible Linux userspace and desktop/runtime libraries; no FUSE required (falls back to `--appimage-extract-and-run`) |
| macOS (Apple Silicon) | `TaskFlow-1.1.2.dmg` | DMG image built on an ARM64 runner (not a universal binary) |

Verify your download before installing:

```bash
sha256sum -c checksums.txt
```

## Upgrading from 1.1.1

Just install the new package over the previous one — your data is untouched. This is a drop-in maintenance release: the database schema is unchanged, all tasks, history, preferences, theme, language, and window geometry carry over, and no migration step is required.

---

# TaskFlow v1.1.1 — Stability Patch Release

TaskFlow 1.1.1 was a patch release that fixed issues found during post-release testing of 1.1.0. It contained no new features: every change was a low-severity fix or documentation improvement, and existing data, preferences, and the database schema were untouched.

## Fixes

- **Clean application shutdown** — closing the window now goes through the standard JavaFX shutdown path, so `Application.stop()` always runs and the SQLite database is explicitly closed (with a WAL checkpoint) on every normal exit. Previously an immediate hard process exit could race past that cleanup.
- **Corrupt timestamps are logged** — malformed or unparseable `created_at`/`updated_at`/`event_at` values in the local database now produce a warning with the column, the raw value, and the parser error before falling back to the current time. Valid data is unaffected; previously the fallback was silent.
- **Locale-safe OS detection** — operating-system name checks now lowercase with `Locale.ROOT`, matching the rest of the codebase, so results cannot vary with the user's locale.
- **Documented audio limitation** — the code now documents a known JavaFX media behavior: on machines without an audio device, JavaFX may print a `MediaException` to stderr when the completion chime is played. It is cosmetic only; the application keeps running and timer completion, archiving, and persistence are unaffected.

## Native packages with bundled Java

Installers are produced with `jpackage` and **bundle a full Java 21 runtime**, so end users do **not** need to install Java.

| Platform | Package | Notes |
| --- | --- | --- |
| Windows (x64) | `TaskFlow-1.1.1.msi` | MSI installer with Start Menu shortcut |
| Linux (x86_64) | `TaskFlow-1.1.1-x86_64.AppImage` | AppImage with bundled Java runtime; requires a compatible Linux userspace and desktop/runtime libraries; no FUSE required (falls back to `--appimage-extract-and-run`) |
| macOS (Apple Silicon) | `TaskFlow-1.1.1.dmg` | DMG image built on an ARM64 runner (not a universal binary) |

Verify your download before installing:

```bash
sha256sum -c checksums.txt
```

## Supported platforms

- **Windows 10/11** (x64) — `.msi`
- **Linux** (x86_64; compatible userspace and desktop/runtime libraries required) — `.AppImage`
- **macOS (Apple Silicon)** — `.dmg`

Continuous integration builds, tests, and packages the application on all three platforms on every change.

## Upgrading from 1.1.0

Just install the new package over the previous one — your data is untouched. This is a drop-in patch: the database schema is unchanged, all tasks, history, preferences, theme, language, and window geometry carry over, and no migration step is required.

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

- The interface ships in five languages only (English, Spanish, German, Italian, Simplified Chinese); French and Portuguese are not supported.
- On systems without an audio device the completion chime cannot play and JavaFX may print a media error to stderr; this does not affect application behavior.
- The Linux package ships as a portable `.AppImage`; there is no native `.deb`/`.rpm` repository integration (menu/shortcut registration depends on the desktop environment).
- The macOS package targets Apple Silicon; no Intel or universal build is provided.
- The Windows package is x64 only.

## License

TaskFlow v1.1.1 is released under the [GNU General Public License v3.0 only](LICENSE) (GPL-3.0-only).
