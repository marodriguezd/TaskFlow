# AGENTS.md — TaskFlow

Authoritative project context for coding agents. Derived from the repository as it exists today; do not invent conventions that are not here.

## 1. Project overview

TaskFlow is a cross-platform desktop task manager with Pomodoro-style timers. The current implementation is **Java 21 + JavaFX**; there is no Python/PyQt6 application code left in the repository (only `tools/release_check.py`, CI tooling, remains Python). The legacy JSON → SQLite migration (`LegacyDataMigrator`) is retained solely for data compatibility with pre-2.0 installs.

User data is local-only: SQLite database plus a replaceable notification sound. No account, no cloud.

## 2. Technology stack

Verified dependencies/tools (from `build.gradle.kts`):

- Java 21 (Gradle toolchain), JavaFX 21.0.12 (OpenJFX Gradle plugin; controls, fxml, media, graphics)
- Gradle 9.8.0 via wrapper (`./gradlew`), Kotlin DSL (`build.gradle.kts`)
- SQLite via `org.xerial:sqlite-jdbc` (WAL mode)
- Jackson (`jackson-databind`, `jackson-datatype-jsr310`) — legacy JSON migration
- SLF4J + Logback (`src/main/resources/logback.xml`)
- JUnit 5 + AssertJ (tests)
- Spotless 8.10.3 with Google Java Format 1.36.1, **AOSP style** (4-space indent)
- `jpackage` (JDK 21) for native packaging
- `appimagetool` (SHA-256-pinned, fetched by `tools/build-appimage.sh`) for the Linux AppImage

## 3. Architecture

Package root `io.github.marodriguezd.taskflow` under `src/main/java`:

| Layer | Packages / classes | Responsibility |
| --- | --- | --- |
| Entry points | `TaskFlowApp` (JavaFX `Application`), `TaskFlowLauncher` (static `main`, required by jpackage) | Composition root: wires repositories → services → UI, runs migration on startup, closes `DatabaseManager` on stop |
| Domain | `domain`: `Task`, `Priority`, `HistoryItem`, `HistoryEventType`, `ThemeMode`, `UserPreferences`, `WindowGeometry` | Pure records/enums, no framework or JDBC dependencies |
| Application services | `service`: `TaskService`, `TimerService`, `SoundService`, `PlatformService`, `ValidationException` | Business logic, timer coordination, validation, sound |
| Persistence | `persistence`: `DatabaseManager`, `TaskRepository`/`SqliteTaskRepository`, `HistoryRepository`/`SqliteHistoryRepository`, `PreferenceRepository`/`SqlitePreferenceRepository`, `LegacyDataMigrator`, `PersistenceException` | Owns all SQLite access; repository interfaces are the only exposure |
| UI | `ui`: `MainWindow`, `ui.component` (`HeaderView`, `TaskCardView`, `ProgressBarView`, `EmptyStateView`, `Icons`), `ui.dialog` (`AddTaskDialog`, `EditTaskDialog`, `HistoryDialog`), `ui.i18n` (`Messages`, `LocaleManager`, `Languages`, `PriorityLabels`), `ui.theme` (`ThemeManager`, `UIConstants`) | JavaFX presentation only |
| Utilities | `util`: `DateTimeUtil`, `TimeFormatter` | Formatting/parsing helpers |

**Dependency direction:** `ui` → `service` → `persistence`/`domain`; `persistence` → `domain`. Never reverse it:

- UI must not touch JDBC, `DatabaseManager`, or SQL — it goes through services and repository interfaces.
- Persistence stays behind repository interfaces; keep SQL inside the `Sqlite*` implementations.
- `domain` stays dependency-free and unit-testable.
- OS-specific behavior (data directories, default always-on-top, frameless window) lives only in `PlatformService`.

## 4. Behavioral invariants

Changes must preserve these (tests pin them: `TimerServiceTest`, `TaskServiceTest`, `EndToEndFlowTest`, `LegacyDataMigratorTest`, `PlatformServiceTest`):

- **Single active timer.** `TimerService.start()` autopauses any other running task (single-active-timer invariant). Only one countdown runs at any moment.
- Timer state uses absolute monotonic elapsed time; persistence writes run on a dedicated background executor. On expiry the task archives a `COMPLETED` history item transactionally and its remaining time is set to `0` (the task stays in the active list at 00:00).
- Editing a task stops its timer; remaining time scales proportionally to the new duration (an already-expired task resets to the full new duration). Duration is validated to 1–999 minutes; names must be non-empty (`ValidationException`).
- **History semantics:** manual completion → `COMPLETED` + `completed_manually=true`, task deleted; delete → `DELETED` archive (skipped when the task is already expired); restoring a history item creates a new task **and removes the history row** (move semantics). Completed/deleted tasks are archived, never silently lost.
- **Preferences persist:** theme (`DARK` default), always-on-top, sound-enabled, UI language, and window geometry (saved on move/resize/close, restored on start).
- **Language (i18n):** exactly five UI languages are supported — `en`, `es`, `de`, `it`, `zh-Hans` (French and Portuguese are deliberately NOT supported; the set is pinned by `LocaleFilesTest`, which fails on any stray bundle file). The language is persisted as a locale-independent BCP 47 tag under the `language` preference key; blank = never chosen → first-run detection from the OS locale (unsupported OS locales, incl. `fr`/`pt`, and Traditional Chinese → English). Runtime switching must update the live UI without restart (modals are closed during a switch). Translatable UI text lives only in `src/main/resources/i18n/messages*.properties`; the English base bundle is the deterministic fallback for missing keys. **Never translate:** log messages, SQL, preference keys, enum names/codes, package/class names, `ValidationException`/`PersistenceException` messages, or legacy-migrator data defaults (they become user data). `Priority` labels are localized only at render time (`ui.i18n.PriorityLabels`); parsing (`Priority.fromDisplayName`, English + legacy Spanish labels) and storage (`HIGH`/`MEDIUM`/`LOW` enum names) stay locale-independent.
- **Theme:** `ThemeManager` stacks `css/base.css` + `css/light.css`/`css/dark.css` over every registered `Scene`; switching must update all registered scenes live.
- **Legacy migration:** runs once at startup, non-fatal on any error (startup must never be blocked), skips import when the target tables already contain rows, and renames processed files to `*.migrated` as a backup — originals are never deleted or overwritten. Legacy Spanish priority labels (`alta`/`media`/`baja`) still parse via `Priority.fromDisplayName`.
- **User data preservation:** never delete or rewrite the user's database or legacy files as a side effect of refactoring.

## 5. Persistence

- **Database:** `taskflow.db` inside the per-user data directory resolved by `PlatformService`:
  - Windows `%APPDATA%\TaskFlow`, macOS `~/Library/Application Support/TaskFlow`, Linux `$XDG_DATA_HOME/TaskFlow` falling back to `~/.TaskFlow`; an existing legacy `~/.TaskFlow` directory wins for continuity.
  - The data directory also holds the user-replaceable `bell.mp3`.
- **Schema ownership:** only `DatabaseManager.initializeSchema()` creates schema — tables `tasks`, `history`, `preferences` (+ indexes `idx_tasks_priority`, `idx_history_event_at`). SQLite `PRAGMA user_version` is explicitly versioned (current baseline 1; existing schema is unchanged). Connections use `PRAGMA foreign_keys=ON`, WAL journal, `busy_timeout=3000`. `DatabaseManager.inMemory()` exists for tests.
- **Preference keys (introduced in 1.1.0):** `theme`, `always_on_top`, `sound_enabled`, `language` (BCP 47 tag; `""` = unset → first-run OS detection), `geo_x`/`geo_y`/`geo_width`/`geo_height`. Adding a key is additive-only — no schema change is needed for the key/value table. `LegacyDataMigrator.migrateSettingsFile` must preserve `language`/`sound_enabled` (it only overwrites `theme`/`always_on_top`).
- **Repositories** are the only read/write path: `SqliteTaskRepository`, `SqliteHistoryRepository`, `SqlitePreferenceRepository`.
- **Migration behavior:** `LegacyDataMigrator` imports legacy JSON from the data directory and home root (`~/.taskflow_data.json`, `~/.taskflow_history.json`, `~/.taskflow_geometry.json`), then renames each processed file with a `.migrated` suffix so it imports exactly once; contents are preserved as a backup.
- **Changing the schema safely:** keep `CREATE TABLE IF NOT EXISTS` idempotent for fresh installs; existing installs need an explicit, additive migration step (SQLite cannot alter columns in place) — handle new columns with defaults, add/adjust tests in `src/test/.../persistence/`, and never rename or drop existing columns. Schema changes are release-worthy behavior changes: update README/RELEASE_NOTES if user-visible.

## 6. Cross-platform behavior

Packaging targets actually produced by the pipeline:

| Platform | Artifact |
| --- | --- |
| Windows x64 | `TaskFlow-<version>.msi` |
| Linux x86_64 | `TaskFlow-<version>-x86_64.AppImage` (compatible Linux userspace and desktop/runtime libraries required) |
| macOS Apple Silicon | `TaskFlow-<version>.dmg` (built on ARM64 runners) |

- **Do not claim Intel or universal macOS builds.** Windows is x64 only.
- Runtime OS differences are isolated in `PlatformService` (data dirs, `getDefaultAlwaysOnTop()` = non-Windows, `usesFramelessWindow()` = Linux) and in `build.gradle.kts` packaging flags (`--win-shortcut/--win-menu` on Windows, AppImage assembly on Linux). Keep new OS conditionals in those two places.

## 7. Build and development commands

```bash
./gradlew run              # run the app in development
./gradlew test             # test suite
./gradlew spotlessCheck    # verify formatting (fails CI on violations)
./gradlew spotlessApply    # apply formatting (run before committing)
./gradlew build            # compile + test + assemble
./gradlew jpackageImage    # standalone app image (build/dist/TaskFlow)
./gradlew jpackagePackage  # native package for the current OS (msi / dmg / AppImage)
```

Also present: `./gradlew releaseStamp` (writes `build/dist/RELEASE_VERSION`, normally run automatically) and `jpackageLinuxAppImage` (internal step of Linux packaging).

Note: Linux `jpackagePackage` needs `APPIMAGETOOL_SHA256` set (CI pins it in `release.yml`) or `APPIMAGETOOL_BIN` pointing to an existing `appimagetool` binary.

## 8. Testing rules

- After code changes, normally run at least `./gradlew spotlessCheck test`; use `./gradlew build` before declaring a change done. JaCoCo enforces at least 45% bundle line coverage in `check`/`build`. Persistence/timer/platform/packaging changes should also be verified with the specific tests below.
- Prefer deterministic tests: use `DatabaseManager.inMemory()` for persistence, no sleeps in timer tests (drive `TimerService` synchronously), no reliance on a display.
- **Never weaken, delete, or `@Disabled` a test just to make CI pass.** Fix the code; if a test is genuinely wrong, explain why and update it deliberately.
- When touching a area, update its tests in the same change:
  - persistence/schema → `SqliteTaskRepositoryTest`, `SqliteHistoryRepositoryTest`, `SqlitePreferenceRepositoryTest`, `LegacyDataMigratorTest`
  - timers/tasks/history → `TimerServiceTest`, `TaskServiceTest`, `EndToEndFlowTest` (history presentation mapping → `HistoryDialogTest`)
  - i18n/languages → `LanguagesTest`, `MessagesBundleTest`, `LocaleFilesTest`, `LocaleManagerTest`, `PriorityLabelsTest` (bundle parity/bijection, fallback, detection, switching)
  - platform paths/OS logic → `PlatformServiceTest`
  - domain/formatting → `domain/*Test`, `util/*Test`
  - packaging → verify with `jpackageImage`/`jpackagePackage` locally; the release validator is `tools/release_check.py` (has strict positive/negative behavior — keep it that way)

## 9. CI/CD and release rules

Workflows (`.github/workflows/`):

- **`ci.yml`** — push/PR to `main`/`master`. 3-OS matrix (Ubuntu/Windows/macOS), JDK **21** (Temurin), `--no-daemon`, runs `spotlessCheck`, `test`, `build`, `jpackageImage`, uploads `taskflow-<os>-app-image` (path glob `build/dist/TaskFlow*` because macOS emits `TaskFlow.app`).
- **`release.yml`** — tag-driven: `push` of `v*` tags (plus `workflow_dispatch` with `validate_only`). Jobs: `validate` → `build` (3-OS matrix) → `release`.
  - Stable tags must be strict `vMAJOR.MINOR.PATCH` (no leading zeros); tag version, `build.gradle.kts` version, and built commit must all match — enforced by `tools/release_check.py --strict` in the validate job and again in the release job with `--artifacts-dir --require-platform-artifacts`.
  - Every artifact carries a `RELEASE_VERSION` provenance stamp (written by `releaseStamp`); the validator compares it to the tag.
  - **Only the `release` job has `contents: write`**; everything else is read-only.
  - Release assets are generated by the pipeline (never uploaded manually): `.msi`, `.AppImage`, `.dmg`, plus `checksums.txt` (SHA-256 of all three packages, also embedded in the release body).
- **Agents must NOT create, modify, delete, or retag releases or tags unless the user explicitly requests it.**

## 10. Versioning

- `build.gradle.kts` `version = "..."` is the single source of the project version; the release tag must equal `v` + that value.
- Current published version is `1.1.2` (dependency/build maintenance release; no behavior changes); `1.1.1` is historical, not a development target. Future development bumps `build.gradle.kts` for the next release and tags consistently (validator enforces the match). Published releases/tags must remain immutable.
- Do not touch existing tags or the published release as part of unrelated work.

## 11. Packaging

- Packages are built with `jpackage` and **bundle a full Java 21 runtime** — end users do not need Java installed. Never claim otherwise.
- Packaging configuration lives in `build.gradle.kts` (`jpackageImage`, `jpackageLinuxAppImage`, `jpackagePackage`, `releaseStamp`), `tools/build-appimage.sh` (AppDir assembly + `appimagetool`), and the platform flags in `release.yml`. Repo-root `assets/` holds the icons passed to jpackage (`taskflow.ico`, `taskflow.icns`, `TaskFlow.png`).
- Do not casually replace the packaging mechanism (jpackage/AppImage) with another tool; it is validated end-to-end by the release pipeline.

## 12. Documentation

- `README.md` and `RELEASE_NOTES.md` must describe the actual implementation and the artifacts the pipeline actually publishes. Do not invent features, platforms, installers, or compatibility claims.
- When behavior/build/release contracts change, update the relevant doc section in the same change (README sections: Distribution, Releases, Data, Migration; release notes are regenerated per release by the pipeline).

## 13. Licensing

- License: **GNU General Public License v3.0 only (GPL-3.0-only)** — `LICENSE` full text; jar manifest carries `Implementation-License: GPL-3.0-only`.
- Do not replace or dual-license the project unless explicitly instructed by the project owner.

## 14. Agent workflow rules

1. Read the relevant files before modifying; derive changes from actual code, not assumptions.
2. Make focused changes. No unrelated refactors, no drive-by reformatting, no new dependencies without need.
3. Preserve the architecture in §3 unless there is a demonstrated reason to change it.
4. Run relevant tests (and `spotlessCheck`) after modifications; fix failures rather than bypassing them.
5. Update documentation when behavior, build, or release contracts change.
6. Never hide failures by weakening validation, tests, or release checks.
7. Keep generated/build output out of commits: `build/`, `.gradle/`, `dist/`, `out/`, `tools/__pycache__/`, IDE files are gitignored — never `git add -A` blindly.
8. Do not create/modify/delete releases or tags without an explicit user request (§9).
9. Do not commit unless the user asks; report state instead.

## 15. Definition of done

- [ ] Implementation complete and focused on the requested change
- [ ] `./gradlew spotlessCheck test` passes (`./gradlew build` for non-trivial changes)
- [ ] Relevant tests updated/added; no tests weakened
- [ ] Packaging verified when packaging/platform code changed (`jpackageImage`/`jpackagePackage`)
- [ ] README/RELEASE_NOTES updated if behavior, build, or release contracts changed
- [ ] No unrelated files changed; `git status` shows only the intended changes; no generated files staged
