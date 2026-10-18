# Project: TaskFlow Refactoring & Optimization

## Architecture
TaskFlow follows a strict layered architecture:
`ui` → `service` → `persistence` / `domain`; `persistence` → `domain`.

- **Domain** (`domain`): Pure immutable records and enums (`Task`, `Priority`, `HistoryItem`, `HistoryEventType`, `ThemeMode`, `UserPreferences`, `WindowGeometry`).
- **Application Services** (`service`): Business logic, timer coordination, monotonic clock calculation, sound, validation, and platform resolution (`TaskService`, `TimerService`, `SoundService`, `PlatformService`, `ValidationException`).
- **Persistence** (`persistence`): SQLite repository layer behind clean interfaces (`DatabaseManager`, `TaskRepository`/`SqliteTaskRepository`, `HistoryRepository`/`SqliteHistoryRepository`, `PreferenceRepository`/`SqlitePreferenceRepository`, `LegacyDataMigrator`, `PersistenceException`).
- **Presentation** (`ui`): JavaFX components, dialogs, theming, and i18n (`MainWindow`, `HeaderView`, `TaskCardView`, `ProgressBarView`, `Icons`, `ThemeManager`, `LocaleManager`, `Messages`).
- **Entry Points**: `TaskFlowLauncher` and `TaskFlowApp`.

---

## Feature Inventory
| # | Feature / Work Item | Description | Milestone | Source |
|---|---|---|---|---|
| 1 | SQLite PRAGMA & Connection Optimization | Eliminate repeated `PRAGMA journal_mode = WAL` on every query; optimize statement/connection lifecycle for timer persistence updates | M1 | Survey 1 & 2 |
| 2 | Statement Batching & Reuse in Preferences | Reuse `PreparedStatement` in `savePreferences` and batch geometry updates | M1 | Survey 1 |
| 3 | Idempotent Completion Archival | Enforce `AND remaining_seconds > 0` in `SqliteTaskRepository.archiveCompletion` to prevent duplicate history records | M1 | Survey 1 |
| 4 | Path Traversal & XDG Compliance | Validate `isAbsolute()` and `.normalize()` on `XDG_DATA_HOME` and `APPDATA` in `PlatformService` | M1 | Survey 1 |
| 5 | Defensive Directory Check in SoundService | Guard `userSound.getParent()` null check before `Files.createDirectories` | M1 | Survey 2 |
| 6 | Alien Method Removal in TimerService | Dispatch `notifyTick`, `notifyRunningState`, `notifyCompletion` outside `synchronized (this)` | M2 | Survey 2 |
| 7 | Single Allocation in HistoryItem Restoration | Direct constructor invocation in `HistoryItem.toRestoredTask()` | M2 | Survey 2 |
| 8 | Domain & Utility Documentation Cleanups | Correct Javadoc in `UserPreferences` and `TimeFormatter` | M2 | Survey 2 |
| 9 | JavaFX Spinner Uncommitted Value Fix | Call `minutesSpinner.commitValue()` in `AddTaskDialog`/`EditTaskDialog.handleConfirm()` | M3 | Survey 3 |
| 10 | Dialog Multi-Monitor Coordinate Resolution | Replace `Math.max(0, ...)` with monitor-aware positioning in `AddTaskDialog` and `HistoryDialog` | M3 | Survey 3 |
| 11 | Ticker CSS Class Thrash Elimination | Avoid removing/re-adding unchanged CSS style classes every second in `TaskCardView` | M3 | Survey 3 |
| 12 | Proportional Gradient Caching in ProgressBarView | Use `proportional = true` and cache `LinearGradient` in `ProgressBarView` | M3 | Survey 3 |
| 13 | Atomic CSS Updates & Static URLs | Cache stylesheet URLs and use `setAll(...)` in `ThemeManager` | M3 | Survey 3 |
| 14 | Modal Dialog Chrome Deduplication | Extract common dialog setup (dragging, ESC handler, modal stage init, theme unregister) | M3 | Survey 3 |
| 15 | Multi-Resolution Application Icons | Register available PNG resolutions (`16, 24, 32, 48, 64, 128, 256, 512`) in `TaskFlowApp` | M3 | Survey 3 |
| 16 | Dead Code Removal in Icons | Remove unused `fillHex` parameter and dead branch in `Icons.java` | M3 | Survey 3 |
| 17 | Full Verification & Invariant Audit | Spotless AOSP formatting, 100% test pass, JaCoCo >= 45%, AGENTS.md invariant verification | M4 | Original Request |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| M1 | Persistence & I/O Optimization | DatabaseManager, SqliteTaskRepository, SqlitePreferenceRepository, PlatformService, SoundService | None | DONE |
| M2 | Concurrency, Domain & Service Hardening | TimerService, TaskService, HistoryItem, UserPreferences, TimeFormatter | M1 | DONE |
| M3 | UI Ticker, Dialogs & Component Optimization | AddTaskDialog, EditTaskDialog, HistoryDialog, AboutDialog, TaskCardView, ProgressBarView, ThemeManager, HeaderView, TaskFlowApp, Icons | M2 | DONE |
| M4 | Final Verification & Invariant Audit | SpotlessCheck, Test Suite, JaCoCo coverage, Forensic Integrity Audit | M1, M2, M3 | DONE |

---

## Verification (M4)

- `./gradlew spotlessApply` + `./gradlew spotlessCheck`: clean (Google Java Format, AOSP).
- `./gradlew test`: 279 tests, 0 failures, 0 errors, 0 skipped.
- `./gradlew build`: successful; JaCoCo bundle line coverage **71.3%** (threshold 45%).
- Invariants from `AGENTS.md` untouched: single active timer, monotonic deadline arithmetic,
  history move semantics, six UI languages, schema v2, WAV sound fallback.

### M3 follow-ups intentionally not implemented

Items 14 (shared `BaseDialog` chrome extraction) and the `HeaderView` tooltip-reuse optimization were
not part of this execution slice; they remain optional maintainability work with no behavioral
impact. Dialog chrome continues to live in each dialog class.

---

## Interface Contracts

### Persistence ↔ Service
- `TaskRepository.updateRemainingSeconds(long id, int remainingSeconds)`: Updates remaining seconds and timestamp. Must be fast and thread-safe.
- `TaskRepository.archiveCompletion(Task task, HistoryItem historyItem)`: Atomically archives completion. Must be idempotent (`remaining_seconds > 0`).
- `PreferenceRepository.savePreferences(UserPreferences preferences)`: Upserts all preference keys atomically with statement reuse.
- `PlatformService.resolveDataDirectory()`: Resolves data directory adhering to OS specifications; rejects relative paths from environment variables.

### TimerService ↔ UI / Service Listeners
- `TimerService.addTickListener`, `addRunningStateListener`, `addCompletionListener`: Listeners are invoked outside the monitor lock (`synchronized (this)`) to guarantee zero lock inversion or UI thread stalls.

### UI Dialogs & Components
- Dialogs encapsulate window dragging, ESC dismissal, and theme registration/unregistration through a clean helper or base class.
- `TaskCardView` exposes lightweight time updates or avoids mutating unchanged priority CSS style classes on active countdown ticks.
- `ProgressBarView` computes fill rectangle width using cached immutable proportional gradient.

---

## Code Layout
- `src/main/java/io/github/marodriguezd/taskflow/persistence/`
  - `DatabaseManager.java`, `SqliteTaskRepository.java`, `SqlitePreferenceRepository.java`, `SqliteHistoryRepository.java`
- `src/main/java/io/github/marodriguezd/taskflow/service/`
  - `TimerService.java`, `TaskService.java`, `SoundService.java`, `PlatformService.java`
- `src/main/java/io/github/marodriguezd/taskflow/domain/`
  - `Task.java`, `HistoryItem.java`, `UserPreferences.java`, `WindowGeometry.java`
- `src/main/java/io/github/marodriguezd/taskflow/ui/`
  - `MainWindow.java`, `TaskFlowApp.java`, `TaskFlowLauncher.java`
- `src/main/java/io/github/marodriguezd/taskflow/ui/component/`
  - `TaskCardView.java`, `ProgressBarView.java`, `HeaderView.java`, `Icons.java`
- `src/main/java/io/github/marodriguezd/taskflow/ui/dialog/`
  - `AddTaskDialog.java`, `EditTaskDialog.java`, `HistoryDialog.java`, `AboutDialog.java`, `BaseDialog.java`
