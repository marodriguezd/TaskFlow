<div align="center">
  <img src="assets/generated/taskflow-128.png" alt="TaskFlow-Logo" height="96" />
</div>

# TaskFlow

Ein schlanker, plattformübergreifender Desktop-Taskmanager mit Timern im Pomodoro-Stil, gebaut mit Java 21 und JavaFX. Aufgaben werden lokal in SQLite gespeichert, und die Anwendung wird als natives Paket mit gebündelter Java-Laufzeitumgebung ausgeliefert — Endanwender benötigen kein installiertes Java.

[![CI](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/marodriguezd/TaskFlow)](https://github.com/marodriguezd/TaskFlow/releases/latest)
[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](LICENSE)

[English](README.md) · [Español](README_es.md) · Deutsch · [Français](README_fr.md) · [Italiano](README_it.md) · [中文](README_zh.md)

## Screenshots

| Dunkles Design | Helles Design |
| --- | --- |
| ![TaskFlow dunkles Design](docs/TaskFlow-dark.png) | ![TaskFlow helles Design](docs/TaskFlow-light.png) |

## Funktionen

- **Aufgabenverwaltung** — Aufgaben erstellen, bearbeiten, abschließen und löschen
- **Prioritäten** — jeder Aufgabe eine Prioritätsstufe zuweisen
- **Pomodoro-Timer** — Countdown-Timer pro Aufgabe
- **Ein-Timer-Invariante** — das Starten eines Timers pausiert automatisch jeden anderen laufenden Timer (Autopause)
- **Fortschrittsanzeige** — visueller Fortschrittsbalken für die aktive Sitzung
- **Verlauf & Wiederherstellung** — erledigte und gelöschte Aufgaben werden in einem Verlauf protokolliert und können wiederhergestellt werden
- **Internationalisierung** — Oberfläche in 6 Sprachen (English, Español, Deutsch, Français, Italiano, 中文简体) mit Live-Umschaltung, Erkennung der Betriebssystemsprache beim ersten Start und deterministischem englischem Fallback
- **SQLite-Speicherung** — alle Aufgaben, Verläufe und Einstellungen werden lokal in einer SQLite-Datenbank gespeichert
- **Designwechsel** — helle und dunkle Designs mit Apple-Liquid-Glass-Stil und nativer Fenstertransluzenz
- **Immer-im-Vordergrund-Modus** — umschaltbares Fenster-Anheften
- **Fenstergeometrie-Speicherung** — Fensterposition und -größe bleiben zwischen Sitzungen erhalten
- **Legacy-Datenmigration** — automatischer einmaliger Import von JSON-Daten aus früheren Versionen
- **Audio-Benachrichtigung** — beim Abschluss eines Timers ertönt optional ein Glockenton (austauschbar; eine mitgelieferte WAV-Kopie hält den Ton am Laufen, wenn das Medien-Backend des Systems kein MP3 dekodieren kann)

## Technologie

| Bereich | Technologie |
| --- | --- |
| Sprache | Java 21 |
| UI-Toolkit | JavaFX 21 |
| Build | Gradle (Kotlin DSL, Wrapper enthalten) |
| Persistenz | SQLite via JDBC (`sqlite-jdbc`) |
| Tests | JUnit 6, AssertJ, JaCoCo (mindestens 45 % Zeilenabdeckung) |
| Code-Stil | Spotless mit Google Java Format |
| Paketierung | `jpackage` mit gebündelter Java-21-Laufzeit |
| Logging | SLF4J + Logback |
| Legacy-Migration | Jackson (JSON) |
| Internationalisierung | Java-`ResourceBundle` (UTF-8, 6 Locales) |

Die CI-Pipeline baut, testet, prüft die Formatierung und paketiert die Anwendung unter Ubuntu, Windows und macOS.

## Architektur

Die Codebasis folgt einer Schichtenstruktur mit klarer Trennung zwischen Präsentation, Anwendungsdiensten, Domänenmodell, Persistenz und Plattformintegration. Paketwurzel: `io.github.marodriguezd.taskflow`.

```
io.github.marodriguezd.taskflow
├── TaskFlowApp           # JavaFX-Anwendungseinstiegspunkt
├── TaskFlowLauncher      # Statischer main()-Starter
├── domain/               # Reine Modellklassen, ohne Framework-Abhängigkeiten
│   ├── Task, Priority
│   ├── HistoryItem, HistoryEventType
│   ├── ThemeMode, UserPreferences, WindowGeometry
├── persistence/          # SQLite-Datenzugriff
│   ├── DatabaseManager               # Verbindungs- & Schemaverwaltung
│   ├── TaskRepository / SqliteTaskRepository
│   ├── HistoryRepository / SqliteHistoryRepository
│   ├── PreferenceRepository / SqlitePreferenceRepository
│   ├── LegacyDataMigrator            # Legacy-JSON → SQLite-Import
│   └── PersistenceException
├── service/              # Anwendungs-/Fachservices
│   ├── TaskService       # Aufgaben-Anwendungsfälle & Validierung
│   ├── TimerService      # Countdown-Timer, Ein-Timer-Durchsetzung
│   ├── PlatformService   # OS-Erkennung, Datenverzeichnisse, Plattform-Defaults
│   ├── SoundService      # Timer-Abschlussaudio
│   └── ValidationException
├── ui/                   # JavaFX-Präsentationsschicht
│   ├── MainWindow
│   ├── component/        # HeaderView, TaskCardView, ProgressBarView,
│   │                     # EmptyStateView, Icons
│   ├── dialog/           # AddTaskDialog, EditTaskDialog, HistoryDialog,
│   │                     # AboutDialog
│   ├── i18n/             # Messages, LocaleManager, Languages, PriorityLabels
│   └── theme/            # ThemeManager, UIConstants
└── util/                 # DateTimeUtil, TimeFormatter
```

- **`ui`** kennt kein JDBC; es spricht mit **`service`**, das **`domain`** und **`persistence`** orchestriert.
- **`domain`** ist abhängigkeitsfrei und vollständig unit-testbar.
- **`persistence`** ist die einzige Schicht, die SQLite berührt, offengelegt über Repository-Interfaces.
- **`service.PlatformService`** isoliert sämtliches betriebssystemspezifisches Verhalten (Datenverzeichnisse, Fensterkonventionen, Standard-Anheftstatus).

Ressourcen liegen unter `src/main/resources`: die CSS-Designs (`css/base.css`, `css/light.css`, `css/dark.css`), die Übersetzungsbundles (`i18n/messages*.properties`), Anwendungssymbole (`assets/`), der Standard-Benachrichtigungston (`assets/bell.mp3` plus `assets/bell.wav` als Ausweichdatei für Medien-Backends ohne MP3-Unterstützung) und die Logback-Konfiguration.

## Erste Schritte

### Voraussetzungen

- Ein JDK 21 (die Gradle-Toolchain findet oder provisioniert eines)

### Ausführen in der Entwicklung

```bash
./gradlew run
```

### Gängige Befehle

```bash
./gradlew test             # Testsuite ausführen
./gradlew spotlessCheck    # Codeformatierung prüfen
./gradlew spotlessApply    # Codeformatierung anwenden
./gradlew build            # Kompilieren, testen und assemblieren
./gradlew jpackageImage    # Eigenständiges Anwendungsabbild mit jpackage bauen
./gradlew jpackagePackage  # Natives Paket (msi/dmg/AppImage) für das aktuelle OS bauen
```

Das paketierte Anwendungsabbild wird nach `build/dist/TaskFlow` geschrieben und kann direkt gestartet werden:

```bash
./build/dist/TaskFlow/bin/TaskFlow
```

## Distribution

`./gradlew jpackageImage` erzeugt ein eigenständiges Anwendungsabbild via [`jpackage`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html). Das Abbild bündelt eine Java-21-Laufzeit, sodass **Endanwender kein installiertes Java benötigen**, um die paketierte Anwendung auszuführen.

`./gradlew jpackagePackage` baut ein natives Installationspaket für das aktuelle Betriebssystem:

| OS | Pakettyp |
| --- | --- |
| Windows | `.msi` |
| macOS | `.dmg` |
| Linux | `.AppImage` (x86_64; erfordert eine kompatible Linux-Userspace- und Desktop-/Runtime-Bibliotheken) |

TaskFlow läuft unter Windows, Linux und macOS. Die GitHub-Actions-Workflows (`.github/workflows/ci.yml`, `.github/workflows/release.yml`) bauen und paketieren die Anwendung auf allen drei Plattformen.

### Releases

> [!NOTE]
> **Aktuell: [TaskFlow v1.0.0](https://github.com/marodriguezd/TaskFlow/releases/tag/v1.0.0)** — bietet Pomodoro-Timer, lokale SQLite-Persistenz, transluzentes Apple Liquid Glass-Design und native Pakete mit Java 21 für Windows (`.msi`), Linux (`.AppImage`) und macOS (`.dmg`, Apple Silicon), mit SHA-256-Prüfsummen zur Verifizierung jedes Downloads.

Offizielle Releases werden automatisch auf der [GitHub-Releases-Seite](https://github.com/marodriguezd/TaskFlow/releases) veröffentlicht, wenn ein `vMAJOR.MINOR.PATCH`-Tag gepusht wird (z. B. `v1.0.0`). Die Pipeline:

1. validiert das Tag-Format, dass es mit der Gradle-Projektversion übereinstimmt und auf den gebauten Commit zeigt;
2. baut und testet die Anwendung auf Windows-, Linux- und macOS-Runnern;
3. erzeugt das native Installationsprogramm jeder Plattform mit `jpackage`;
4. verifiziert einen `RELEASE_VERSION`-Provenienzstempel, der jedem Artefakt beiliegt;
5. veröffentlicht ein stabiles GitHub Release mit den `.msi`-, `.AppImage`- und `.dmg`-Paketen plus SHA-256-`checksums.txt`.

Jedes Installationsprogramm bündelt eine Java-21-Laufzeit, sodass Endanwender kein installiertes Java benötigen.

## Daten

TaskFlow speichert alle Anwenderdaten in einem benutzerspezifischen Verzeichnis mit der SQLite-Datenbank (`taskflow.db`) und dem Benachrichtigungston (`bell.mp3`). Der Ort hängt von der Plattform ab:

| Plattform | Datenverzeichnis |
| --- | --- |
| Windows | `%APPDATA%\TaskFlow` |
| macOS | `~/Library/Application Support/TaskFlow` |
| Linux | `$XDG_DATA_HOME/TaskFlow`, Rückfall auf `~/.TaskFlow` |

Falls bereits ein Legacy-Verzeichnis `~/.TaskFlow` existiert, wird es aus Kontinuitätsgründen als Datenverzeichnis beibehalten.

## Sprache

Die TaskFlow-Oberfläche erscheint in genau sechs Sprachen:

| Sprache | Tag |
| --- | --- |
| Englisch | `en` |
| Spanisch | `es` |
| Deutsch | `de` |
| Französisch | `fr` |
| Italienisch | `it` |
| Vereinfachtes Chinesisch | `zh-Hans` |

- **Live-Umschaltung** — Sprache über die Globus-Schaltfläche in der Kopfzeile wählen; die gesamte UI (einschließlich offenem Zustand wie Aufgaben-Badge und Tooltips) wird sofort neu gerendert, ohne Neustart.
- **Persistent pro Anwender** — die Wahl wird in der `preferences`-Tabelle (Schlüssel `language`) als locale-unabhängiger BCP-47-Tag gespeichert. Bestehende Datenbanken aus früheren Versionen benötigen keine Migration: der Schlüssel wird beim ersten Speichern einfach hinzugefügt.
- **Erststart-Erkennung** — beim ersten Start wird die Oberflächensprache aus der OS-Locale erkannt. Nicht unterstützte OS-Sprachen (einschließlich Portugiesisch und nicht ausgelieferter Sprachen) fallen auf Englisch zurück; traditionelle chinesische Locales fallen ebenfalls auf Englisch zurück, da nur vereinfachtes Chinesisch ausgeliefert wird.
- **Deterministischer englischer Fallback** — Übersetzungen liegen in `src/main/resources/i18n/messages*.properties`; jeder fehlende Schlüssel löst auf das englische Basisbundle auf, und ein überall fehlender Schlüssel wird unverändert zurückgegeben (einmal geloggt), statt abzustürzen. Die Testsuite erzwingt, dass genau diese sechs Bundles existieren und vollständig sind (Schlüssel- und Platzhalterparität).
- **Nicht übersetzt** — Logausgaben, Datenbankwerte, Prioritäts-/Verlaufscodes und interne Kennungen bleiben aus Datenkompatibilitätsgründen locale-unabhängiges Englisch.

## Migration

Wer eine frühere TaskFlow-Version nutzte, die Daten als JSON speicherte: Die Anwendung erkennt die Legacy-Dateien beim ersten Start und importiert sie automatisch nach SQLite:

- `taskflow_data.json` (Aufgaben)
- `taskflow_history.json` (Verlauf)
- `taskflow_geometry.json` (Fenstergeometrie)
- `taskflow_settings.json` (Design- und Fenstereinstellungen)

Legacy-Dateien werden im Datenverzeichnis und im Home-Verzeichniswurzel (`~/.taskflow_data.json`, `~/.taskflow_history.json`, `~/.taskflow_geometry.json`) gesucht. Jede verarbeitete Datei wird mit Suffix `.migrated` umbenannt (z. B. `taskflow_data.json.migrated`); der Originalinhalt bleibt als Sicherung erhalten. Migrationsfehler werden geloggt und verhindern niemals den Anwendungsstart.

Seit Schema 2 speichert die Datenbank dauerhafte Herkunft: `legacy_sources` erfasst Quellentyp und SHA-256 der Originalbytes; importierte Zeilen tragen Quellen-ID und kanonische Signatur. Quelle, Zeilen und Herkunft werden in einer Transaktion bestätigt, erst danach wird die Datei umbenannt. Wiederholungen, Wiederherstellungen und identische Kopien erzeugen daher keine Duplikate. Nur importierte Zeilen nehmen an der Deduplizierung teil: eine in der Anwendung erstellte Zeile unterdrückt nie einen Legacy-Datensatz. Gleiche Einträge innerhalb einer Datei behalten ihre Vielfachheit, überlappende Snapshots importieren jeden gemeinsamen Datensatz einmal. Die Verlaufsidentität nutzt die kanonische lokale Uhrzeit der Datei, nicht einen aufgelösten Zeitpunkt; eine Zeitzonenänderung dupliziert daher nichts und ändert die gespeicherte Zeit nicht. `event_at` hat Vorrang, dann `completed_at`; unlesbare Werte erhalten eine Ersatzzeit, die keine Identität ist. Geometrie aus dem Home-Wurzelverzeichnis wird vor der aus dem Datenverzeichnis angewendet (letztere gewinnt); Einstellungen werden nur aus dem Datenverzeichnis gelesen. Das Schema-Upgrade ist additiv und erhält alle Daten; Zeilen vor Schema 2 werden nicht als importiert markiert, da ihre Herkunft nicht beweisbar ist.

## Entwicklung

- **Formatierung** wird mit Spotless (Google Java Format, AOSP-Stil) erzwungen. Vor dem Committen `./gradlew spotlessApply` ausführen; CI scheitert bei `spotlessCheck`-Verstößen.
- **Tests** nutzen JUnit 6 mit AssertJ und decken Domänenmodell, Services, Repositories, Legacy-Migration, Internationalisierung (Bundle-Vollständigkeit, Fallback, Spracherkennung), Timer-Randfälle, Schema- und Transaktionsverhalten, Geometrie-Wiederherstellung und einen End-to-End-Anwendungsfluss ab. JaCoCo erzwingt eine Mindest-Zeilenabdeckung von 45 %. Tests laufen headless — kein Display oder JavaFX-Toolkit erforderlich. CI prüft das gepackte Abbild und seine Ressourcen und startet/beendet die Anwendung auf allen drei Plattformen (Xvfb unter Linux). `python3 tools/upgrade_check.py` startet die gepackte App mit einer echten v1-Datenbank (Fixture `src/test/resources/schema-v1.sql`) und prüft Upgrade auf v2, Datenerhalt und NULL-Provenienz. `python3 tools/data_preservation_check.py` erfasst Datenbank, `bell.mp3` und eine Sentinel-Aufgabe, entfernt das installierte Paket (MSI-Deinstallation, kopierte `.app`, AppImage) und prüft, dass die Daten unverändert überleben. Unter Linux treibt `python3 tools/acceptance_check.py` die gepackte Anwendung auf einem privaten Xvfb-Display mit Wegwerfprofil und einem privaten ALSA-Datei-PCM an: CRUD, Timer, Verlauf, Persistenz von Theme/Sprache/Geometrie sowie die Pfade Ton an/aus/korrupt/fehlend, mit **nicht stummen aufgezeichneten Samples** bei Wiedergabe und Stille bei deaktiviertem Ton — ohne Desktop, Maus oder Soundkarte zu verwenden. Physische Hörbarkeit und visuelle Qualität erfordern weiterhin manuelle Prüfungen.

## Repository-Administration

Der empfohlene `main`-Branch-Schutz, einschließlich erforderlicher CI-Checks und Signatur-Überlegungen für Commits, ist in [docs/REPOSITORY_HARDENING.md](docs/REPOSITORY_HARDENING.md) dokumentiert. Branch-Regeln erfordern GitHub-Repository-Administration und werden nicht durch Quelldateien konfiguriert.
