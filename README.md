<div align="center">
  <img src="assets/generated/taskflow-128.png" alt="TaskFlow logo" height="96" />
</div>

# TaskFlow

A lightweight, cross-platform desktop task manager with Pomodoro-style timers, built with Java 21 and JavaFX. Tasks are persisted locally in SQLite, and the application ships as a native package with a bundled Java runtime — no Java installation required for end users.

[![CI](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/marodriguezd/TaskFlow)](https://github.com/marodriguezd/TaskFlow/releases/latest)
[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](LICENSE)

English · [Español](README_es.md) · [Deutsch](README_de.md) · [Français](README_fr.md) · [Italiano](README_it.md) · [中文](README_zh.md)

## Screenshots

| Dark theme | Light theme |
| --- | --- |
| ![TaskFlow dark theme](docs/TaskFlow-dark.png) | ![TaskFlow light theme](docs/TaskFlow-light.png) |

## Features

- **Task management** — create, edit, complete, and delete tasks
- **Priorities** — assign a priority level to each task
- **Pomodoro timers** — per-task countdown timers
- **Single active timer invariant** — starting a timer automatically pauses any other running timer (autopause)
- **Progress tracking** — visual progress bar for the active session
- **History & restoration** — completed and deleted tasks are logged to a history with the ability to restore them
- **Internationalization** — interface in 6 languages (English, Español, Deutsch, Français, Italiano, 中文简体) with live switching, first-run OS language detection, and deterministic English fallback
- **SQLite persistence** — all tasks, history, and preferences are stored locally in a SQLite database
- **Theme switching** — light and dark themes with Apple Liquid Glass styling and native window translucency
- **Always-on-top mode** — toggleable window pinning
- **Window geometry persistence** — window position and size are remembered between sessions
- **Legacy data migration** — automatic one-time import of JSON data from previous versions
- **Audio notification** — an optional bell chime plays when a timer completes (user-replaceable; a bundled WAV copy keeps the chime working when the host media backend cannot decode MP3)

## Technology

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| UI toolkit | JavaFX 21 |
| Build | Gradle (Kotlin DSL, wrapper included) |
| Persistence | SQLite via JDBC (`sqlite-jdbc`) |
| Testing | JUnit 6, AssertJ, JaCoCo (45% minimum line coverage) |
| Code style | Spotless with Google Java Format |
| Packaging | `jpackage` with a bundled Java 21 runtime |
| Logging | SLF4J + Logback |
| Legacy migration | Jackson (JSON) |
| Internationalization | Java `ResourceBundle` (UTF-8, 6 locales) |

The CI pipeline builds, tests, formats-checks, and packages the application on Ubuntu, Windows, and macOS.

## Architecture

The codebase follows a layered structure with a clear separation between presentation, application services, domain model, persistence, and platform integration. Package root: `io.github.marodriguezd.taskflow`.

```
io.github.marodriguezd.taskflow
├── TaskFlowApp           # JavaFX Application entry point
├── TaskFlowLauncher      # Static main() launcher
├── domain/               # Pure model classes, no framework dependencies
│   ├── Task, Priority
│   ├── HistoryItem, HistoryEventType
│   ├── ThemeMode, UserPreferences, WindowGeometry
├── persistence/          # SQLite data access
│   ├── DatabaseManager               # Connection & schema management
│   ├── TaskRepository / SqliteTaskRepository
│   ├── HistoryRepository / SqliteHistoryRepository
│   ├── PreferenceRepository / SqlitePreferenceRepository
│   ├── LegacyDataMigrator            # Legacy JSON → SQLite import
│   └── PersistenceException
├── service/              # Application/business services
│   ├── TaskService       # Task use cases & validation
│   ├── TimerService      # Countdown timers, single-active-timer enforcement
│   ├── PlatformService   # OS detection, data directories, platform defaults
│   ├── SoundService      # Timer completion audio
│   └── ValidationException
├── ui/                   # JavaFX presentation layer
│   ├── MainWindow
│   ├── component/        # HeaderView, TaskCardView, ProgressBarView,
│   │                     # EmptyStateView, Icons
│   ├── dialog/           # AddTaskDialog, EditTaskDialog, HistoryDialog,
│   │                     # AboutDialog
│   ├── i18n/             # Messages, LocaleManager, Languages, PriorityLabels
│   └── theme/            # ThemeManager, UIConstants
└── util/                 # DateTimeUtil, TimeFormatter
```

- **`ui`** knows nothing about JDBC; it talks to **`service`**, which orchestrates **`domain`** and **`persistence`**.
- **`domain`** is dependency-free and fully unit-testable.
- **`persistence`** is the only layer that touches SQLite, exposed through repository interfaces.
- **`service.PlatformService`** isolates all operating-system-specific behavior (data directories, windowing conventions, default always-on-top state).

Resources live under `src/main/resources`: the CSS themes (`css/base.css`, `css/light.css`, `css/dark.css`), the translation bundles (`i18n/messages*.properties`), application icons (`assets/`), the default notification sound (`assets/bell.mp3` plus the `assets/bell.wav` fallback for media backends without MP3 support), and the Logback configuration.

## Getting Started

### Prerequisites

- A JDK 21 (the Gradle toolchain will locate or provision one)

### Running in development

```bash
./gradlew run
```

### Common commands

```bash
./gradlew test             # Run the test suite
./gradlew spotlessCheck    # Verify code formatting
./gradlew spotlessApply    # Apply code formatting
./gradlew build            # Compile, test, and assemble
./gradlew jpackageImage    # Build a standalone application image with jpackage
./gradlew jpackagePackage  # Build a native package (msi/dmg/AppImage) for the current OS
```

The packaged application image is written to `build/dist/TaskFlow` and can be launched directly:

```bash
./build/dist/TaskFlow/bin/TaskFlow
```

## Distribution

`./gradlew jpackageImage` produces a self-contained application image via [`jpackage`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html). The image bundles a Java 21 runtime, so **end users do not need Java installed** to run the packaged application.

`./gradlew jpackagePackage` builds a native installer package for the current operating system:

| OS | Package type |
| --- | --- |
| Windows | `.msi` |
| macOS | `.dmg` |
| Linux | `.AppImage` (x86_64; requires a compatible Linux userspace and desktop/runtime libraries) |

TaskFlow runs on Windows, Linux, and macOS. GitHub Actions workflows (`.github/workflows/ci.yml`, `.github/workflows/release.yml`) build and package the application on all three platforms.

### Releases

> [!NOTE]
> **Latest: [TaskFlow v1.2.4](https://github.com/marodriguezd/TaskFlow/releases/tag/v1.2.4)** — hardens legacy-data import with transactional provenance, matches history independently of time-zone changes, and falls back to the bundled `bell.wav` when `bell.mp3` cannot be decoded. Native packages bundle a full Java 21 runtime for Windows (`.msi`), Linux (`.AppImage`), and macOS (`.dmg`, Apple Silicon), with SHA-256 checksums for verifying each download.

Official releases are published automatically on the [GitHub Releases page](https://github.com/marodriguezd/TaskFlow/releases) when a `vMAJOR.MINOR.PATCH` tag is pushed (e.g. `v1.0.0`). The pipeline:

1. validates the tag format, that it matches the Gradle project version, and that it points at the built commit;
2. builds and tests the application on Windows, Linux, and macOS runners;
3. produces the native installer for each platform with `jpackage`;
4. verifies a `RELEASE_VERSION` provenance stamp bundled with every artifact;
5. publishes a stable GitHub Release with the `.msi`, `.AppImage`, and `.dmg` packages plus SHA-256 `checksums.txt`.

Each installer bundles a Java 21 runtime, so end users do not need Java installed.

## Data

TaskFlow stores all user data in a per-user directory containing the SQLite database (`taskflow.db`) and the notification sound (`bell.mp3`). The location depends on the platform:

| Platform | Data directory |
| --- | --- |
| Windows | `%APPDATA%\TaskFlow` |
| macOS | `~/Library/Application Support/TaskFlow` |
| Linux | `$XDG_DATA_HOME/TaskFlow`, falling back to `~/.TaskFlow` |

If a legacy `~/.TaskFlow` directory already exists, it is kept as the data directory for continuity.

## Language

TaskFlow's interface ships in exactly six languages:

| Language | Tag |
| --- | --- |
| English | `en` |
| Spanish | `es` |
| German | `de` |
| French | `fr` |
| Italian | `it` |
| Simplified Chinese | `zh-Hans` |

- **Live switching** — pick a language from the globe button in the header; the whole UI (including open state such as the task badge and tooltips) re-renders immediately, without restarting.
- **Persistent per user** — the choice is stored in the `preferences` table (`language` key) as a locale-independent BCP 47 tag. Existing databases from 1.0.0 need no migration: the key is simply added on first save.
- **First-run detection** — on first launch the interface language is detected from the OS locale. Unsupported OS languages (including Portuguese and any language not shipped) fall back to English; Traditional Chinese locales also fall back to English, since only Simplified Chinese is shipped.
- **Deterministic English fallback** — translations live in `src/main/resources/i18n/messages*.properties`; any missing key resolves to the English base bundle, and a key missing everywhere is returned as-is (logged once) rather than crashing. The test suite enforces that exactly these six bundles exist and are complete (key and placeholder parity).
- **Not translated** — log output, database values, priority/history codes, and internal identifiers remain locale-independent English for data compatibility.

## Migration

If you used a previous version of TaskFlow that stored data as JSON, the application detects the legacy files on first launch and imports them into SQLite automatically:

- `taskflow_data.json` (tasks)
- `taskflow_history.json` (history)
- `taskflow_geometry.json` (window geometry)
- `taskflow_settings.json` (theme and window preferences)

Legacy files are looked up in the data directory and in the home directory root (`~/.taskflow_data.json`, `~/.taskflow_history.json`, `~/.taskflow_geometry.json`). Every discovered location is imported, even when another location already populated the database. Each processed file is renamed with a `.migrated` suffix (e.g. `taskflow_data.json.migrated`), never overwriting an existing backup; the original contents are preserved as a backup. Migration failures are logged and never prevent the application from starting.

### Import provenance and deduplication

Legacy JSON has no stable record identifiers. Since schema version 2 the database therefore keeps durable import provenance instead of guessing from the current table contents:

- **Source ledger** — `legacy_sources` records the source kind (`tasks`, `history`, `geometry`, `settings`) and the SHA-256 of the original bytes. The source record, the imported rows, and their provenance are written in one transaction; the file is renamed only after that transaction commits. A retry after a failed rename, or a restored source backup with identical bytes, is skipped rather than duplicated. A failed parse or transaction leaves neither partial rows nor a false completion record.
- **Row provenance** — imported `tasks` and `history` rows carry the source id and a canonical content signature. Only imported rows participate in matching: an application-created row is invisible to the matcher and can never suppress a legacy record. Provenance lives on the row itself, so deleting an imported row also removes its provenance.
- **Multiset matching** — signatures are counted and consumed one-for-one. Repeated identical entries inside one file keep their multiplicity, and overlapping source snapshots import each shared record once while keeping every distinct record. Rows are never overwritten during import.
- **Timestamps** — history identity uses the canonical **local wall-clock** value written by the legacy file, not a resolved instant. A source retried or restored under a different JVM time zone therefore keeps the same identity. The instant is resolved once, with the zone in effect at first import, and used for storage/display. `event_at` wins when it parses; otherwise a parsable `completed_at` is used; when neither parses, the entry is stored with a fallback time and is explicitly not treated as a reliable timestamp.
- **Singleton precedence** — geometry from the home root is applied before geometry from the data directory, so a conflicting data-directory value wins deterministically on first import. Settings are read only from the data directory. A source already recorded as processed is not re-applied, so a restored stale backup cannot overwrite newer user settings or geometry.

**Schema upgrade.** Schema version 2 is an additive upgrade: it creates `legacy_sources` and adds nullable provenance columns and indexes. Existing tasks, history, preferences, geometry, language, and sound settings are preserved unchanged, and the upgrade is idempotent. **Rows written before schema version 2 are not labelled as legacy-imported**, because their origin cannot be proven; they keep `NULL` provenance. Consequently, if a source was imported by a build that predates schema v2, is still on disk (or is restored later), and its rows still exist, the first provenance-aware upgrade can add one duplicate occurrence. This residual ambiguity is inherent to legacy data without identifiers and is not hidden by guessing. All imports performed by this version or later use the durable provenance ledger.

## Development

- **Formatting** is enforced with Spotless (Google Java Format, AOSP style). Run `./gradlew spotlessApply` before committing; CI fails on `spotlessCheck` violations.
- **Tests** use JUnit 6 with AssertJ and cover the domain model, services, repositories, legacy migration and provenance, schema upgrades, internationalization (bundle completeness, fallback, language detection), timer edge cases, transaction behavior, geometry recovery, and an end-to-end application flow. JaCoCo enforces a 45% minimum line-coverage threshold. Unit and integration tests run headless — no display or JavaFX toolkit required.
- **Packaged-app checks** run in CI on Ubuntu, Windows, and macOS: the jpackage image and its JAR resources (translations, themes, audio, JavaFX) are verified, then the packaged application is launched with an explicit smoke-test flag that requires the primary window to be showing and a normal shutdown. Linux uses Xvfb and also verifies the AppImage; macOS verifies a copy installed from the DMG; Windows verifies the MSI install/uninstall. `python3 tools/upgrade_check.py` boots the packaged app against a genuine v1 database built from `src/test/resources/schema-v1.sql` and verifies the v2 upgrade, data preservation and NULL provenance. `python3 tools/data_preservation_check.py` records the database, `bell.mp3` and a sentinel task in a disposable profile, removes the installed package (MSI uninstall, copied `.app`, AppImage), and verifies the data survived unchanged. `python3 tools/acceptance_check.py` (Linux) drives the packaged application on a private Xvfb display with a disposable profile and a private ALSA file PCM: CRUD, timers, history, theme/language/geometry persistence, and the enabled/disabled/corrupt/missing sound paths, asserting **non-silent captured samples** for playback and silence when sound is disabled, without using your desktop, mouse, or sound card. Physical audibility and interactive GUI quality still require the manual checks in [docs/MANUAL_ACCEPTANCE.md](docs/MANUAL_ACCEPTANCE.md).

## Repository administration

Recommended `main` branch protection, including required CI checks and signed-commit considerations, is documented in [docs/REPOSITORY_HARDENING.md](docs/REPOSITORY_HARDENING.md). Branch rules require GitHub repository administration and are not configured by source files.
