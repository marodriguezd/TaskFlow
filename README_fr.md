<div align="center">
  <img src="assets/generated/taskflow-128.png" alt="logo TaskFlow" height="96" />
</div>

# TaskFlow

Un gestionnaire de tâches de bureau léger et multiplateforme avec minuteurs de style Pomodoro, construit avec Java 21 et JavaFX. Les tâches sont persistées localement dans SQLite, et l'application est livrée comme paquet natif avec un runtime Java embarqué — l'utilisateur final n'a pas besoin de Java installé.

[![CI](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/marodriguezd/TaskFlow)](https://github.com/marodriguezd/TaskFlow/releases/latest)
[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](LICENSE)

[English](README.md) · [Español](README_es.md) · [Deutsch](README_de.md) · Français · [Italiano](README_it.md) · [中文](README_zh.md)

## Captures d'écran

| Thème sombre | Thème clair |
| --- | --- |
| ![TaskFlow thème sombre](docs/TaskFlow-dark.png) | ![TaskFlow thème clair](docs/TaskFlow-light.png) |

## Fonctionnalités

- **Gestion des tâches** — créer, modifier, terminer et supprimer des tâches
- **Priorités** — assigner un niveau de priorité à chaque tâche
- **Minuteurs Pomodoro** — comptes à rebours par tâche
- **Invariant minuteur unique** — démarrer un minuteur met automatiquement en pause tout autre minuteur en cours (pause auto)
- **Suivi de progression** — barre de progression visuelle de la session active
- **Historique et restauration** — les tâches terminées et supprimées sont journalisées dans un historique avec possibilité de les restaurer
- **Internationalisation** — interface en 6 langues (English, Español, Deutsch, Français, Italiano, 中文简体) avec bascule en direct, détection de la langue du système au premier lancement et anglais comme repli déterministe
- **Persistance SQLite** — toutes les tâches, l'historique et les préférences sont stockés localement dans une base SQLite
- **Bascule de thème** — thèmes clair et sombre avec style Apple Liquid Glass et translucidité native des fenêtres
- **Mode toujours visible** — épinglage de fenêtre commutable
- **Géométrie persistante** — la position et la taille de la fenêtre sont mémorisées entre sessions
- **Migration des données héritées** — import automatique unique des données JSON des versions précédentes
- **Notification sonore** — une cloche optionnelle retentit à la fin d'un minuteur (remplaçable)

## Technologie

| Domaine | Technologie |
| --- | --- |
| Langage | Java 21 |
| Boîte à outils UI | JavaFX 21 |
| Build | Gradle (Kotlin DSL, wrapper inclus) |
| Persistance | SQLite via JDBC (`sqlite-jdbc`) |
| Tests | JUnit 6, AssertJ, JaCoCo (minimum 45 % de couverture de lignes) |
| Style de code | Spotless avec Google Java Format |
| Empaquetage | `jpackage` avec runtime Java 21 embarqué |
| Journalisation | SLF4J + Logback |
| Migration héritée | Jackson (JSON) |
| Internationalisation | `ResourceBundle` Java (UTF-8, 6 locales) |

Le pipeline CI compile, teste, vérifie le formatage et empaquette l'application sur Ubuntu, Windows et macOS.

## Architecture

Le code suit une structure en couches avec séparation claire entre présentation, services applicatifs, modèle de domaine, persistance et intégration plateforme. Racine des paquets : `io.github.marodriguezd.taskflow`.

```
io.github.marodriguezd.taskflow
├── TaskFlowApp           # Point d'entrée de l'application JavaFX
├── TaskFlowLauncher      # Lanceur avec main() statique
├── domain/               # Classes du modèle pur, sans dépendances framework
│   ├── Task, Priority
│   ├── HistoryItem, HistoryEventType
│   ├── ThemeMode, UserPreferences, WindowGeometry
├── persistence/          # Accès aux données SQLite
│   ├── DatabaseManager               # Gestion connexion et schéma
│   ├── TaskRepository / SqliteTaskRepository
│   ├── HistoryRepository / SqliteHistoryRepository
│   ├── PreferenceRepository / SqlitePreferenceRepository
│   ├── LegacyDataMigrator            # Import JSON hérité → SQLite
│   └── PersistenceException
├── service/              # Services applicatifs/métier
│   ├── TaskService       # Cas d'usage et validation des tâches
│   ├── TimerService      # Minuteurs, un seul minuteur actif
│   ├── PlatformService   # Détection OS, répertoires de données, défauts
│   ├── SoundService      # Audio de fin de minuteur
│   └── ValidationException
├── ui/                   # Couche de présentation JavaFX
│   ├── MainWindow
│   ├── component/        # HeaderView, TaskCardView, ProgressBarView,
│   │                     # EmptyStateView, Icons
│   ├── dialog/           # AddTaskDialog, EditTaskDialog, HistoryDialog,
│   │                     # AboutDialog
│   ├── i18n/             # Messages, LocaleManager, Languages, PriorityLabels
│   └── theme/            # ThemeManager, UIConstants
└── util/                 # DateTimeUtil, TimeFormatter
```

- **`ui`** ne connaît pas JDBC ; il parle à **`service`**, qui orchestre **`domain`** et **`persistence`**.
- **`domain`** est sans dépendances et entièrement unit-testable.
- **`persistence`** est la seule couche qui touche SQLite, exposée via des interfaces de dépôt.
- **`service.PlatformService`** isole tout comportement spécifique au système d'exploitation (répertoires de données, conventions de fenêtrage, état d'épinglage par défaut).

Les ressources vivent dans `src/main/resources` : les thèmes CSS (`css/base.css`, `css/light.css`, `css/dark.css`), les bundles de traduction (`i18n/messages*.properties`), les icônes (`assets/`), le son de notification par défaut (`assets/bell.mp3`) et la configuration Logback.

## Démarrage

### Prérequis

- Un JDK 21 (le toolchain Gradle le localise ou le provisionne)

### Exécution en développement

```bash
./gradlew run
```

### Commandes courantes

```bash
./gradlew test             # Exécuter la suite de tests
./gradlew spotlessCheck    # Vérifier le formatage du code
./gradlew spotlessApply    # Appliquer le formatage du code
./gradlew build            # Compiler, tester et assembler
./gradlew jpackageImage    # Construire une image standalone avec jpackage
./gradlew jpackagePackage  # Construire un paquet natif (msi/dmg/AppImage) pour l'OS courant
```

L'image empaquetée est écrite dans `build/dist/TaskFlow` et peut être lancée directement :

```bash
./build/dist/TaskFlow/bin/TaskFlow
```

## Distribution

`./gradlew jpackageImage` produit une image d'application autonome via [`jpackage`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html). L'image embarque un runtime Java 21, donc **l'utilisateur final n'a pas besoin de Java installé** pour exécuter l'application empaquetée.

`./gradlew jpackagePackage` construit un installateur natif pour le système d'exploitation courant :

| OS | Type de paquet |
| --- | --- |
| Windows | `.msi` |
| macOS | `.dmg` |
| Linux | `.AppImage` (x86_64 ; nécessite un userspace Linux compatible et des bibliothèques desktop/runtime) |

TaskFlow fonctionne sur Windows, Linux et macOS. Les workflows GitHub Actions (`.github/workflows/ci.yml`, `.github/workflows/release.yml`) compilent et empaquettent l'application sur les trois plateformes.

### Publications

> [!NOTE]
> **Dernière : [TaskFlow v1.2.3](https://github.com/marodriguezd/TaskFlow/releases/tag/v1.2.3)** — ajoute un curseur d'intensité Liquid Glass réglable et rend les fonds de dialogues modaux opaques à 98 % pour une lisibilité maximale. Les paquets natifs embarquent un runtime Java 21 complet pour Windows (`.msi`), Linux (`.AppImage`) et macOS (`.dmg`, Apple Silicon), avec checksums SHA-256 pour vérifier chaque téléchargement.

Les publications officielles sont générées automatiquement sur la [page GitHub Releases](https://github.com/marodriguezd/TaskFlow/releases) quand un tag `vMAJEUR.MINEUR.PATCH` est poussé (p. ex. `v1.0.0`). Le pipeline :

1. valide le format du tag, sa correspondance avec la version du projet Gradle et son pointage vers le commit compilé ;
2. compile et teste l'application sur runners Windows, Linux et macOS ;
3. produit l'installateur natif de chaque plateforme avec `jpackage` ;
4. vérifie un sceau de provenance `RELEASE_VERSION` embarqué dans chaque artefact ;
5. publie une GitHub Release stable avec les paquets `.msi`, `.AppImage` et `.dmg` plus `checksums.txt` SHA-256.

Chaque installateur embarque un runtime Java 21, donc l'utilisateur final n'a pas besoin de Java installé.

## Données

TaskFlow stocke toutes les données utilisateur dans un répertoire par utilisateur contenant la base SQLite (`taskflow.db`) et le son de notification (`bell.mp3`). L'emplacement dépend de la plateforme :

| Plateforme | Répertoire de données |
| --- | --- |
| Windows | `%APPDATA%\TaskFlow` |
| macOS | `~/Library/Application Support/TaskFlow` |
| Linux | `$XDG_DATA_HOME/TaskFlow`, repli vers `~/.TaskFlow` |

Si un répertoire hérité `~/.TaskFlow` existe déjà, il est conservé comme répertoire de données par continuité.

## Langue

L'interface TaskFlow existe en exactement six langues :

| Langue | Étiquette |
| --- | --- |
| Anglais | `en` |
| Espagnol | `es` |
| Allemand | `de` |
| Français | `fr` |
| Italien | `it` |
| Chinois simplifié | `zh-Hans` |

- **Bascule en direct** — choisissez une langue depuis le bouton globe dans l'en-tête ; toute l'UI (y compris l'état ouvert comme le badge de tâches et les infobulles) est re-rendue aussitôt, sans redémarrage.
- **Persistant par utilisateur** — le choix est stocké dans la table `preferences` (clé `language`) comme étiquette BCP 47 indépendante de la locale. Les bases existantes de 1.0.0 n'ont besoin d'aucune migration : la clé est simplement ajoutée à la première sauvegarde.
- **Détection au premier lancement** — au premier lancement la langue est détectée depuis la locale du système. Les langues non supportées (dont le portugais et toute langue non livrée) retombent à l'anglais ; les locales de chinois traditionnel retombent aussi à l'anglais, seul le chinois simplifié étant livré.
- **Anglais comme repli déterministe** — les traductions vivent dans `src/main/resources/i18n/messages*.properties` ; toute clé manquante résout vers le bundle anglais de base, et une clé manquante partout est retournée telle quelle (journalisée une fois) au lieu de planter. La suite de tests exige que ces six bundles existent exactement et soient complets (parité des clés et des placeholders).
- **Non traduit** — les logs, les valeurs de base, les codes de priorité/historique et les identifiants internes restent en anglais indépendant de la locale pour compatibilité des données.

## Migration

Si vous utilisiez une version précédente de TaskFlow qui stockait les données en JSON, l'application détecte les fichiers hérités au premier lancement et les importe automatiquement dans SQLite :

- `taskflow_data.json` (tâches)
- `taskflow_history.json` (historique)
- `taskflow_geometry.json` (géométrie de fenêtre)
- `taskflow_settings.json` (thème et préférences de fenêtre)

Les fichiers hérités sont cherchés dans le répertoire de données et à la racine du home (`~/.taskflow_data.json`, `~/.taskflow_history.json`, `~/.taskflow_geometry.json`). Chaque fichier traité est renommé avec le suffixe `.migrated` (p. ex. `taskflow_data.json.migrated`) ; le contenu original est conservé comme sauvegarde. Les échecs de migration sont journalisés et n'empêchent jamais le démarrage.

Depuis le schéma 2, la base conserve une provenance durable : `legacy_sources` enregistre le type de source et le SHA-256 des octets d'origine, et les lignes importées portent l'identifiant de source et une signature canonique. La source, les lignes et leur provenance sont validées dans une seule transaction, puis le fichier est renommé. Les reprises, restaurations et copies identiques ne créent donc pas de doublons. Seules les lignes importées participent à la déduplication : une ligne créée par l'application ne supprime jamais un enregistrement hérité. Les entrées identiques d'un même fichier gardent leur multiplicité et les instantanés qui se chevauchent n'importent chaque enregistrement commun qu'une fois. L'identité de l'historique utilise l'heure locale canonique du fichier, pas un instant résolu : un changement de fuseau horaire ne duplique rien et ne modifie pas l'heure stockée. `event_at` est prioritaire, puis `completed_at` ; les valeurs illisibles reçoivent une heure de secours qui n'identifie pas les enregistrements. La géométrie de la racine du home est appliquée avant celle du répertoire de données (cette dernière gagne) ; les réglages ne sont lus que dans le répertoire de données. La mise à niveau du schéma est additive et préserve toutes les données ; les lignes antérieures au schéma 2 ne sont pas étiquetées comme importées, car leur origine n'est pas démontrable.

## Développement

- **Formatage** imposé avec Spotless (Google Java Format, style AOSP). Exécutez `./gradlew spotlessApply` avant de commiter ; la CI échoue sur violations de `spotlessCheck`.
- **Tests** avec JUnit 6 et AssertJ : couvrent le modèle de domaine, les services, les dépôts, la migration héritée, l'internationalisation (complétude des bundles, repli, détection de langue), les cas limites des minuteurs, le comportement schéma/transactions, la récupération de géométrie et un flux applicatif de bout en bout. JaCoCo exige un seuil minimum de 45 % de couverture de lignes. Les tests tournent headless — sans écran ni toolkit JavaFX. La CI vérifie aussi l'image empaquetée et ses ressources, et démarre/arrête l'application sur les trois plateformes (Xvfb sous Linux) ; la sortie audio physique n'est pas prouvée. Sous Linux, `python3 tools/acceptance_check.py` pilote aussi l'application empaquetée sur un affichage Xvfb privé avec un profil jetable : CRUD, minuteurs, historique, persistance du thème/de la langue/géométrie et décisions de son, sans utiliser votre bureau ni votre périphérique audio.

## Administration du dépôt

La protection recommandée de la branche `main`, incluant checks CI requis et considérations de commits signés, est documentée dans [docs/REPOSITORY_HARDENING.md](docs/REPOSITORY_HARDENING.md). Les règles de branche requièrent l'administration du dépôt GitHub et ne se configurent pas par fichiers sources.
