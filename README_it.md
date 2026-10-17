<div align="center">
  <img src="assets/generated/taskflow-128.png" alt="logo TaskFlow" height="96" />
</div>

# TaskFlow

Un task manager desktop leggero e multipiattaforma con timer in stile Pomodoro, costruito con Java 21 e JavaFX. Le attività sono salvate localmente in SQLite e l'applicazione è distribuita come pacchetto nativo con runtime Java incluso — l'utente finale non ha bisogno di Java installato.

[![CI](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/marodriguezd/TaskFlow)](https://github.com/marodriguezd/TaskFlow/releases/latest)
[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](LICENSE)

[English](README.md) · [Español](README_es.md) · [Deutsch](README_de.md) · [Français](README_fr.md) · Italiano · [中文](README_zh.md)

## Screenshot

| Tema scuro | Tema chiaro |
| --- | --- |
| ![TaskFlow tema scuro](docs/TaskFlow-dark.png) | ![TaskFlow tema chiaro](docs/TaskFlow-light.png) |

## Funzionalità

- **Gestione attività** — creare, modificare, completare ed eliminare attività
- **Priorità** — assegnare un livello di priorità a ogni attività
- **Timer Pomodoro** — conto alla rovescia per attività
- **Invariante timer unico** — avviare un timer mette automaticamente in pausa qualsiasi altro timer in corso (pausa automatica)
- **Tracciamento avanzamento** — barra di avanzamento visiva della sessione attiva
- **Cronologia e ripristino** — le attività completate ed eliminate sono registrate in una cronologia con possibilità di ripristino
- **Internazionalizzazione** — interfaccia in 6 lingue (English, Español, Deutsch, Français, Italiano, 中文简体) con cambio live, rilevamento della lingua del sistema al primo avvio e inglese come fallback deterministico
- **Persistenza SQLite** — tutte le attività, la cronologia e le preferenze sono salvate localmente in un database SQLite
- **Cambio tema** — temi chiaro e scuro con stile Apple Liquid Glass e traslucenza nativa delle finestre
- **Modalità sempre in primo piano** — fissaggio finestra commutabile
- **Geometria persistente** — posizione e dimensione della finestra ricordate tra le sessioni
- **Migrazione dati legacy** — importazione automatica una tantum dei dati JSON delle versioni precedenti
- **Avviso sonoro** — una campana opzionale suona al completamento di un timer (sostituibile; una copia WAV inclusa mantiene il suono quando il backend multimediale di sistema non decodifica MP3)

## Tecnologia

| Area | Tecnologia |
| --- | --- |
| Linguaggio | Java 21 |
| Toolkit UI | JavaFX 21 |
| Build | Gradle (Kotlin DSL, wrapper incluso) |
| Persistenza | SQLite via JDBC (`sqlite-jdbc`) |
| Test | JUnit 6, AssertJ, JaCoCo (minimo 45% di copertura righe) |
| Stile codice | Spotless con Google Java Format |
| Packaging | `jpackage` con runtime Java 21 incluso |
| Logging | SLF4J + Logback |
| Migrazione legacy | Jackson (JSON) |
| Internazionalizzazione | `ResourceBundle` Java (UTF-8, 6 locale) |

La pipeline CI compila, testa, verifica la formattazione e pacchettizza l'applicazione su Ubuntu, Windows e macOS.

## Architettura

Il codice segue una struttura a livelli con separazione chiara tra presentazione, servizi applicativi, modello di dominio, persistenza e integrazione di piattaforma. Package radice: `io.github.marodriguezd.taskflow`.

```
io.github.marodriguezd.taskflow
├── TaskFlowApp           # Punto di ingresso dell'applicazione JavaFX
├── TaskFlowLauncher      # Launcher con main() statico
├── domain/               # Classi del modello puro, senza dipendenze dal framework
│   ├── Task, Priority
│   ├── HistoryItem, HistoryEventType
│   ├── ThemeMode, UserPreferences, WindowGeometry
├── persistence/          # Accesso dati SQLite
│   ├── DatabaseManager               # Gestione connessione e schema
│   ├── TaskRepository / SqliteTaskRepository
│   ├── HistoryRepository / SqliteHistoryRepository
│   ├── PreferenceRepository / SqlitePreferenceRepository
│   ├── LegacyDataMigrator            # Import JSON legacy → SQLite
│   └── PersistenceException
├── service/              # Servizi applicativi/business
│   ├── TaskService       # Casi d'uso e validazione attività
│   ├── TimerService      # Timer countdown, un solo timer attivo
│   ├── PlatformService   # Rilevamento OS, directory dati, default
│   ├── SoundService      # Audio a completamento timer
│   └── ValidationException
├── ui/                   # Livello presentazione JavaFX
│   ├── MainWindow
│   ├── component/        # HeaderView, TaskCardView, ProgressBarView,
│   │                     # EmptyStateView, Icons
│   ├── dialog/           # AddTaskDialog, EditTaskDialog, HistoryDialog,
│   │                     # AboutDialog
│   ├── i18n/             # Messages, LocaleManager, Languages, PriorityLabels
│   └── theme/            # ThemeManager, UIConstants
└── util/                 # DateTimeUtil, TimeFormatter
```

- **`ui`** non conosce JDBC; parla con **`service`**, che orchestra **`domain`** e **`persistence`**.
- **`domain`** è senza dipendenze e completamente unit-testabile.
- **`persistence`** è l'unico livello che tocca SQLite, esposto tramite interfacce repository.
- **`service.PlatformService`** isola tutto il comportamento specifico del sistema operativo (directory dati, convenzioni finestra, stato di default del fissaggio).

Le risorse vivono in `src/main/resources`: i temi CSS (`css/base.css`, `css/light.css`, `css/dark.css`), i bundle di traduzione (`i18n/messages*.properties`), le icone (`assets/`), il suono di notifica predefinito (`assets/bell.mp3` più la copia `assets/bell.wav` per i backend senza supporto MP3) e la configurazione Logback.

## Primi passi

### Prerequisiti

- Un JDK 21 (il toolchain Gradle lo individua o lo fornisce)

### Esecuzione in sviluppo

```bash
./gradlew run
```

### Comandi comuni

```bash
./gradlew test             # Eseguire la suite di test
./gradlew spotlessCheck    # Verificare la formattazione del codice
./gradlew spotlessApply    # Applicare la formattazione del codice
./gradlew build            # Compilare, testare e assemblare
./gradlew jpackageImage    # Costruire immagine standalone con jpackage
./gradlew jpackagePackage  # Costruire pacchetto nativo (msi/dmg/AppImage) per l'OS corrente
```

L'immagine pacchettizzata è scritta in `build/dist/TaskFlow` e può essere avviata direttamente:

```bash
./build/dist/TaskFlow/bin/TaskFlow
```

## Distribuzione

`./gradlew jpackageImage` produce un'immagine applicativa autocontenuta via [`jpackage`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html). L'immagine include un runtime Java 21, quindi **l'utente finale non ha bisogno di Java installato** per eseguire l'applicazione pacchettizzata.

`./gradlew jpackagePackage` costruisce un installer nativo per il sistema operativo corrente:

| OS | Tipo pacchetto |
| --- | --- |
| Windows | `.msi` |
| macOS | `.dmg` |
| Linux | `.AppImage` (x86_64; richiede userspace Linux compatibile e librerie desktop/runtime) |

TaskFlow funziona su Windows, Linux e macOS. I workflow GitHub Actions (`.github/workflows/ci.yml`, `.github/workflows/release.yml`) compilano e pacchettizzano l'applicazione su tutte e tre le piattaforme.

### Release

> [!NOTE]
> **Ultima: [TaskFlow v1.2.3](https://github.com/marodriguezd/TaskFlow/releases/tag/v1.2.3)** — aggiunge uno slider di intensità Liquid Glass regolabile e rende gli sfondi dei dialoghi modali opachi al 98% per massima leggibilità. I pacchetti nativi includono un runtime Java 21 completo per Windows (`.msi`), Linux (`.AppImage`) e macOS (`.dmg`, Apple Silicon), con checksum SHA-256 per verificare ogni download.

Le release ufficiali sono pubblicate automaticamente nella [pagina GitHub Releases](https://github.com/marodriguezd/TaskFlow/releases) quando viene pushato un tag `vMAGGIORE.MINORE.PATCH` (es. `v1.0.0`). La pipeline:

1. valida il formato del tag, che corrisponda alla versione del progetto Gradle e che punti al commit compilato;
2. compila e testa l'applicazione su runner Windows, Linux e macOS;
3. produce l'installer nativo di ogni piattaforma con `jpackage`;
4. verifica un timbro di provenienza `RELEASE_VERSION` incluso in ogni artefatto;
5. pubblica una GitHub Release stabile con i pacchetti `.msi`, `.AppImage` e `.dmg` più `checksums.txt` SHA-256.

Ogni installer include un runtime Java 21, quindi l'utente finale non ha bisogno di Java installato.

## Dati

TaskFlow salva tutti i dati utente in una directory per utente contenente il database SQLite (`taskflow.db`) e il suono di notifica (`bell.mp3`). La posizione dipende dalla piattaforma:

| Piattaforma | Directory dati |
| --- | --- |
| Windows | `%APPDATA%\TaskFlow` |
| macOS | `~/Library/Application Support/TaskFlow` |
| Linux | `$XDG_DATA_HOME/TaskFlow`, con fallback a `~/.TaskFlow` |

Se esiste già una directory legacy `~/.TaskFlow`, viene conservata come directory dati per continuità.

## Lingua

L'interfaccia TaskFlow esiste in esattamente sei lingue:

| Lingua | Tag |
| --- | --- |
| Inglese | `en` |
| Spagnolo | `es` |
| Tedesco | `de` |
| Francese | `fr` |
| Italiano | `it` |
| Cinese semplificato | `zh-Hans` |

- **Cambio live** — scegli una lingua dal pulsante globo nell'header; l'intera UI (incluso lo stato aperto come il badge attività e i tooltip) viene ridisegnata all'istante, senza riavvio.
- **Persistente per utente** — la scelta è salvata nella tabella `preferences` (chiave `language`) come tag BCP 47 indipendente dal locale. I database esistenti dalla 1.0.0 non necessitano migrazione: la chiave viene semplicemente aggiunta al primo salvataggio.
- **Rilevamento al primo avvio** — al primo avvio la lingua è rilevata dal locale del SO. Le lingue non supportate (inclusi portoghese e qualsiasi lingua non distribuita) ricadono sull'inglese; anche i locale di cinese tradizionale ricadono sull'inglese, poiché viene distribuito solo il cinese semplificato.
- **Inglese come fallback deterministico** — le traduzioni vivono in `src/main/resources/i18n/messages*.properties`; qualsiasi chiave mancante risolve sul bundle base inglese, e una chiave mancante ovunque viene restituita così com'è (loggata una volta) invece di fallire. La suite di test esige che esistano esattamente questi sei bundle e che siano completi (parità chiavi e placeholder).
- **Non tradotto** — output dei log, valori del database, codici di priorità/cronologia e identificatori interni restano in inglese indipendente dal locale per compatibilità dei dati.

## Migrazione

Se usavi una versione precedente di TaskFlow che salvava i dati come JSON, l'applicazione rileva i file legacy al primo avvio e li importa automaticamente in SQLite:

- `taskflow_data.json` (attività)
- `taskflow_history.json` (cronologia)
- `taskflow_geometry.json` (geometria finestra)
- `taskflow_settings.json` (tema e preferenze finestra)

I file legacy sono cercati nella directory dati e nella radice della home (`~/.taskflow_data.json`, `~/.taskflow_history.json`, `~/.taskflow_geometry.json`). Ogni file elaborato è rinominato con suffisso `.migrated` (es. `taskflow_data.json.migrated`); il contenuto originale è conservato come backup. I fallimenti di migrazione sono loggati e non impediscono mai l'avvio dell'applicazione.

Dallo schema 2 il database conserva una provenienza durevole: `legacy_sources` registra il tipo di sorgente e lo SHA-256 dei byte originali, mentre le righe importate portano l'id della sorgente e una firma canonica. Sorgente, righe e provenienza vengono confermate in un'unica transazione; solo dopo il file viene rinominato. Ripetizioni, ripristini e copie identiche non creano quindi duplicati. Solo le righe importate partecipano alla deduplicazione: una riga creata dall'applicazione non sopprime mai un record legacy. Le voci identiche dentro un file mantengono la loro molteplicità e gli snapshot sovrapposti importano ogni record condiviso una volta. L'identità della cronologia usa l'ora locale canonica del file, non un istante risolto: un cambio di fuso orario non duplica nulla e non altera l'ora salvata. `event_at` ha precedenza, poi `completed_at`; i valori illeggibili ricevono un orario di riserva che non identifica i record. La geometria dalla radice della home viene applicata prima di quella della directory dati (quest'ultima vince); le impostazioni sono lette solo dalla directory dati. L'aggiornamento dello schema è additivo e preserva tutti i dati; le righe precedenti allo schema 2 non vengono etichettate come importate perché la loro origine non è dimostrabile.

## Sviluppo

- **Formattazione** imposta con Spotless (Google Java Format, stile AOSP). Esegui `./gradlew spotlessApply` prima di committare; la CI fallisce su violazioni di `spotlessCheck`.
- **Test** con JUnit 6 e AssertJ: coprono modello di dominio, servizi, repository, migrazione legacy, internazionalizzazione (completezza bundle, fallback, rilevamento lingua), casi limite dei timer, comportamento schema/transazioni, recupero geometria e un flusso end-to-end. JaCoCo esige una soglia minima del 45% di copertura righe. I test girano headless — senza display né toolkit JavaFX. La CI verifica l'immagine pacchettizzata e le sue risorse e avvia/chiude l'applicazione su tutte e tre le piattaforme (Xvfb su Linux). `python3 tools/upgrade_check.py` avvia l'app pacchettizzata con un database v1 reale (fixture `src/test/resources/schema-v1.sql`) e verifica l'aggiornamento a v2, la conservazione dei dati e la provenienza NULL. `python3 tools/data_preservation_check.py` registra database, `bell.mp3` e un'attività sentinella, rimuove il pacchetto installato (disinstallazione MSI, `.app` copiata, AppImage) e verifica che i dati sopravvivano intatti. Su Linux, `python3 tools/acceptance_check.py` guida l'applicazione pacchettizzata su un display Xvfb privato, con profilo usa-e-getta e un PCM ALSA su file privato: CRUD, timer, cronologia, persistenza di tema/lingua/geometria e i percorsi suono attivo/disattivo/corrotto/assente, richiedendo **campioni catturati non silenziosi** durante la riproduzione e silenzio con audio disattivato, senza usare desktop, mouse o scheda audio. L'udibilità fisica e la qualità visiva restano verifiche manuali.

## Amministrazione del repository

La protezione raccomandata del branch `main`, inclusi check CI richiesti e considerazioni sui commit firmati, è documentata in [docs/REPOSITORY_HARDENING.md](docs/REPOSITORY_HARDENING.md). Le regole di branch richiedono amministrazione del repository GitHub e non si configurano con file sorgente.
