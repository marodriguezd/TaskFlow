# TaskFlow

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](LICENSE)

TaskFlow is a production-quality native desktop task manager built with **Java 21** and **JavaFX**. It combines Pomodoro-style countdown timers, visual priorities, completion history, local SQLite persistence, customizable sound notifications, and platform-specific window behaviors into a sleek, responsive desktop tool.

---

## Highlights & Features

- **Task Cards**:
  - Name with wrapping and high-contrast typography
  - Priority badge (`Alta` / High, `Media` / Medium, `Baja` / Low) with distinct visual accents
  - Monospace countdown timer display (MM:SS)
  - Custom gradient progress bar indicating remaining duration
  - Quick action controls: Edit (✎), Delete (✕), Mark Completed (✓), and Play/Pause (▶ / ❚❚)
- **Single-Timer Coordination**: Only one task timer runs at any moment; starting another timer automatically pauses the previous one.
- **Completion & Deletion History**:
  - Detailed audit log of completed and deleted tasks
  - Tracks event mode: timer expiration, manual completion, or deletion
  - One-click task restoration from history back to the active list
- **Theme Engine**:
  - High-contrast Dark theme (`#111114` base, `#7c6af7` accent)
  - Clean Light theme (`#f4f7fc` base, `#2f7ef7` accent)
  - Dynamic stylesheet switching without restarting the application
- **Platform-Aware Window Experience**:
  - **Linux / Wayland / X11**: Sleek frameless floating panel with smooth edge resizing, draggable header, and default always-on-top behavior.
  - **Windows**: Native window decorations supporting Snap Layouts, minimizing, maximizing, and pin toggle.
  - **macOS**: Native application styling with retina display support.
- **Audio Feedback**:
  - Automatic chime playback when a timer expires
  - Customizable completion audio via `bell.mp3` in the user's data directory with built-in fallbacks.

---

## Architecture Overview

TaskFlow follows clean architectural boundaries with strict separation of concerns:

```
src/main/java/io/github/marodriguezd/taskflow/
├── domain/              # Immutable domain entities & records (Task, Priority, HistoryItem, etc.)
├── persistence/         # SQLite JDBC repositories, schema management, and legacy data migration
├── service/             # Business logic (TaskService, TimerService, SoundService, PlatformService)
├── ui/                  # JavaFX controllers, views, custom components, and dialogs
│   ├── component/       # Custom controls (TaskCardView, ProgressBarView, HeaderView, EmptyStateView)
│   ├── dialog/          # Modal dialogs (AddTaskDialog, EditTaskDialog, HistoryDialog)
│   └── theme/           # ThemeManager, layout constants, and dynamic stylesheets
└── util/                # Formatters (TimeFormatter, DateTimeUtil)
```

### Modern Java 21 Features
- **Records**: Concise, immutable domain models (`Task`, `HistoryItem`, `WindowGeometry`, `UserPreferences`).
- **Pattern Matching & Switch Expressions**: Clean, exhaustively checked branching for platform detection and priority handling.
- **Standard Math APIs**: `Math.clamp` for bounds enforcement.
- **Decoupled Concurrency**: `ScheduledExecutorService` with `Platform.runLater` for timer scheduling, ensuring 100% testability in both GUI and headless environments.
- **Standard JDBC SQLite**: Fast local persistence using write-ahead logging (WAL) and foreign keys without heavy ORM overhead.

---

## Technology Stack

- **Runtime**: Java 21 LTS (Eclipse Temurin / OpenJDK)
- **GUI Toolkit**: JavaFX 21 (Controls, Media, Graphics, FXML)
- **Build System**: Gradle 8.10+ (Kotlin DSL)
- **Database**: SQLite 3 via JDBC (`org.xerial:sqlite-jdbc`)
- **Logging**: SLF4J 2.0 + Logback Classic
- **Serialization / Migration**: Jackson Databind 2.18
- **Testing**: JUnit 5 (Jupiter), AssertJ Core
- **Code Quality**: Spotless (Google Java Format AOSP)
- **Native Packaging**: JDK `jpackage`

---

## Development Setup

### Prerequisites
- **JDK 21+** installed (or let the Gradle toolchain provision it).
- Verify your environment:
  ```bash
  java --version
  ```

### Build, Test, and Format

1. **Compile and run all unit & integration tests:**
   ```bash
   ./gradlew test
   ```
2. **Assemble the application JAR and distributions:**
   ```bash
   ./gradlew build
   ```
3. **Verify and apply code formatting:**
   ```bash
   ./gradlew spotlessCheck
   ./gradlew spotlessApply
   ```
4. **Run TaskFlow locally:**
   ```bash
   ./gradlew run
   ```

---

## Native Packaging (`jpackage`)

TaskFlow leverages Java's native packaging tool (`jpackage`) to build standalone application bundles that include an optimized Java runtime environment. End users do not need to install Java.

### Standalone Application Image
Builds a standalone image under `build/dist/TaskFlow/` containing the native executable and bundled runtime:
```bash
./gradlew jpackageImage
```
To run the generated binary:
- **Linux**: `./build/dist/TaskFlow/bin/TaskFlow`
- **Windows**: `.\build\dist\TaskFlow\TaskFlow.exe`
- **macOS**: `open build/dist/TaskFlow.app`

### Distributable Installers
Builds native installers appropriate for the host operating system under `build/dist/`:
```bash
./gradlew jpackagePackage
```
- **Linux**: Generates `.deb` (or `.rpm`) package with desktop entry and menu category.
- **Windows**: Generates `.msi` (or `.exe`) installer with desktop shortcut and start menu integration.
- **macOS**: Generates `.dmg` disk image with application bundle.

---

## Data Storage & Migration

TaskFlow stores its SQLite database and user preferences in dedicated per-user application directories:

| Platform | Primary Data Directory | Database File |
| :--- | :--- | :--- |
| **Linux** | `~/.TaskFlow` (or `$XDG_DATA_HOME/TaskFlow`) | `taskflow.db` |
| **Windows** | `%APPDATA%\TaskFlow` (or `%USERPROFILE%\.TaskFlow`) | `taskflow.db` |
| **macOS** | `~/Library/Application Support/TaskFlow` | `taskflow.db` |

### Legacy Migration
If upgrading from the legacy Python/PyQt6 implementation of TaskFlow, the application detects existing JSON files on first launch:
- `taskflow_data.json` (Active tasks)
- `taskflow_history.json` (Task history)
- `taskflow_geometry.json` (Window position and size)
- `taskflow_settings.json` (Theme and pin preferences)

The built-in `LegacyDataMigrator` automatically imports these records into SQLite and safely renames the original files with a `.migrated` extension.

### Customizing Completion Audio
To use a custom chime sound:
1. Replace `bell.mp3` located in your data directory (e.g. `~/.TaskFlow/bell.mp3`).
2. TaskFlow will automatically use your custom sound file. If removed, the default bundled chime is restored.

---

## Continuous Integration

Multi-platform GitHub Actions workflows are configured in `.github/workflows/`:
- **`ci.yml`**: Compiles, runs tests, checks formatting, and produces native images on Linux (`ubuntu-latest`), Windows (`windows-latest`), and macOS (`macos-latest`).
- **`release.yml`**: Automatically builds native packages (`.deb`, `.msi`, `.dmg`) upon tagging a release (`v*`).

---

## License

This project is licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License (CC BY-NC-SA 4.0)](LICENSE).
