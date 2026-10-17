<div align="center">
  <img src="assets/generated/taskflow-128.png" alt="logotipo de TaskFlow" height="96" />
</div>

# TaskFlow

Un gestor de tareas de escritorio ligero y multiplataforma con temporizadores de estilo Pomodoro, construido con Java 21 y JavaFX. Las tareas se guardan localmente en SQLite y la aplicación se distribuye como paquete nativo con un runtime de Java incluido — el usuario final no necesita tener Java instalado.

[![CI](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/marodriguezd/TaskFlow/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/marodriguezd/TaskFlow)](https://github.com/marodriguezd/TaskFlow/releases/latest)
[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](LICENSE)

[English](README.md) · Español · [Deutsch](README_de.md) · [Français](README_fr.md) · [Italiano](README_it.md) · [中文](README_zh.md)

## Capturas

| Tema oscuro | Tema claro |
| --- | --- |
| ![TaskFlow tema oscuro](docs/TaskFlow-dark.png) | ![TaskFlow tema claro](docs/TaskFlow-light.png) |

## Características

- **Gestión de tareas** — crear, editar, completar y eliminar tareas
- **Prioridades** — asignar un nivel de prioridad a cada tarea
- **Temporizadores Pomodoro** — temporizadores de cuenta atrás por tarea
- **Invariante de temporizador único** — iniciar un temporizador pausa automáticamente cualquier otro en marcha (pausa automática)
- **Seguimiento del progreso** — barra de progreso visual de la sesión activa
- **Historial y restauración** — las tareas completadas y eliminadas se registran en un historial con posibilidad de restaurarlas
- **Internacionalización** — interfaz en 6 idiomas (English, Español, Deutsch, Français, Italiano, 中文简体) con cambio en vivo, detección del idioma del sistema en el primer arranque y inglés como reserva determinista
- **Persistencia SQLite** — todas las tareas, el historial y las preferencias se guardan localmente en una base SQLite
- **Cambio de tema** — temas claro y oscuro con estilo Apple Liquid Glass y translucidez nativa de ventana
- **Modo siempre visible** — fijado de ventana conmutable
- **Geometría persistente** — la posición y el tamaño de la ventana se recuerdan entre sesiones
- **Migración de datos heredados** — importación automática y única de datos JSON de versiones anteriores
- **Aviso sonoro** — una campana opcional suena al completar un temporizador (reemplazable por el usuario; incluye una copia WAV que mantiene el aviso cuando el backend multimedia del sistema no puede decodificar MP3)

## Tecnología

| Área | Tecnología |
| --- | --- |
| Lenguaje | Java 21 |
| Toolkit de UI | JavaFX 21 |
| Compilación | Gradle (Kotlin DSL, wrapper incluido) |
| Persistencia | SQLite vía JDBC (`sqlite-jdbc`) |
| Pruebas | JUnit 6, AssertJ, JaCoCo (mínimo 45% de cobertura de líneas) |
| Estilo de código | Spotless con Google Java Format |
| Empaquetado | `jpackage` con runtime de Java 21 incluido |
| Registro | SLF4J + Logback |
| Migración heredada | Jackson (JSON) |
| Internacionalización | `ResourceBundle` de Java (UTF-8, 6 locales) |

El pipeline de CI compila, prueba, verifica el formato y empaqueta la aplicación en Ubuntu, Windows y macOS.

## Arquitectura

El código sigue una estructura por capas con separación clara entre presentación, servicios de aplicación, modelo de dominio, persistencia e integración de plataforma. Paquete raíz: `io.github.marodriguezd.taskflow`.

```
io.github.marodriguezd.taskflow
├── TaskFlowApp           # Punto de entrada de la aplicación JavaFX
├── TaskFlowLauncher      # Lanzador con main() estático
├── domain/               # Clases del modelo puro, sin dependencias de framework
│   ├── Task, Priority
│   ├── HistoryItem, HistoryEventType
│   ├── ThemeMode, UserPreferences, WindowGeometry
├── persistence/          # Acceso a datos SQLite
│   ├── DatabaseManager               # Gestión de conexión y esquema
│   ├── TaskRepository / SqliteTaskRepository
│   ├── HistoryRepository / SqliteHistoryRepository
│   ├── PreferenceRepository / SqlitePreferenceRepository
│   ├── LegacyDataMigrator            # Importación JSON heredada → SQLite
│   └── PersistenceException
├── service/              # Servicios de aplicación/negocio
│   ├── TaskService       # Casos de uso y validación de tareas
│   ├── TimerService      # Temporizadores, un solo temporizador activo
│   ├── PlatformService   # Detección de SO, directorios de datos, valores por defecto
│   ├── SoundService      # Audio al completar un temporizador
│   └── ValidationException
├── ui/                   # Capa de presentación JavaFX
│   ├── MainWindow
│   ├── component/        # HeaderView, TaskCardView, ProgressBarView,
│   │                     # EmptyStateView, Icons
│   ├── dialog/           # AddTaskDialog, EditTaskDialog, HistoryDialog,
│   │                     # AboutDialog
│   ├── i18n/             # Messages, LocaleManager, Languages, PriorityLabels
│   └── theme/            # ThemeManager, UIConstants
└── util/                 # DateTimeUtil, TimeFormatter
```

- **`ui`** no conoce JDBC; habla con **`service`**, que orquesta **`domain`** y **`persistence`**.
- **`domain`** no tiene dependencias y es totalmente unit-testeable.
- **`persistence`** es la única capa que toca SQLite, expuesta mediante interfaces de repositorio.
- **`service.PlatformService`** aísla todo el comportamiento específico del sistema operativo (directorios de datos, convenciones de ventana, estado por defecto de siempre visible).

Los recursos viven en `src/main/resources`: los temas CSS (`css/base.css`, `css/light.css`, `css/dark.css`), los bundles de traducción (`i18n/messages*.properties`), los iconos (`assets/`), el sonido de aviso por defecto (`assets/bell.mp3` y la reserva `assets/bell.wav` para backends sin soporte MP3) y la configuración de Logback.

## Primeros pasos

### Requisitos

- Un JDK 21 (el toolchain de Gradle lo localiza o lo provisiona)

### Ejecutar en desarrollo

```bash
./gradlew run
```

### Comandos habituales

```bash
./gradlew test             # Ejecutar la suite de pruebas
./gradlew spotlessCheck    # Verificar el formato del código
./gradlew spotlessApply    # Aplicar el formato del código
./gradlew build            # Compilar, probar y ensamblar
./gradlew jpackageImage    # Construir imagen standalone con jpackage
./gradlew jpackagePackage  # Construir paquete nativo (msi/dmg/AppImage) para el SO actual
```

La imagen empaquetada se escribe en `build/dist/TaskFlow` y puede lanzarse directamente:

```bash
./build/dist/TaskFlow/bin/TaskFlow
```

## Distribución

`./gradlew jpackageImage` produce una imagen de aplicación autocontenida vía [`jpackage`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html). La imagen incluye un runtime de Java 21, así que **el usuario final no necesita Java instalado** para ejecutar la aplicación empaquetada.

`./gradlew jpackagePackage` construye un instalador nativo para el sistema operativo actual:

| SO | Tipo de paquete |
| --- | --- |
| Windows | `.msi` |
| macOS | `.dmg` |
| Linux | `.AppImage` (x86_64; requiere un userspace Linux compatible y librerías de escritorio/runtime) |

TaskFlow funciona en Windows, Linux y macOS. Los workflows de GitHub Actions (`.github/workflows/ci.yml`, `.github/workflows/release.yml`) compilan y empaquetan la aplicación en las tres plataformas.

### Publicaciones

> [!NOTE]
> **Última: [TaskFlow v1.2.3](https://github.com/marodriguezd/TaskFlow/releases/tag/v1.2.3)** — añade un deslizador de intensidad Liquid Glass ajustable y hace los fondos de diálogos modales 98% opacos para máxima legibilidad. Los paquetes nativos incluyen un runtime completo de Java 21 para Windows (`.msi`), Linux (`.AppImage`) y macOS (`.dmg`, Apple Silicon), con checksums SHA-256 para verificar cada descarga.

Las publicaciones oficiales se generan automáticamente en la [página de GitHub Releases](https://github.com/marodriguezd/TaskFlow/releases) cuando se sube una etiqueta `vMAYOR.MENOR.PARCHE` (p. ej. `v1.0.0`). El pipeline:

1. valida el formato de la etiqueta, que coincida con la versión del proyecto Gradle y que apunte al commit compilado;
2. compila y prueba la aplicación en runners de Windows, Linux y macOS;
3. produce el instalador nativo de cada plataforma con `jpackage`;
4. verifica un sello de procedencia `RELEASE_VERSION` incluido en cada artefacto;
5. publica una GitHub Release estable con los paquetes `.msi`, `.AppImage` y `.dmg` más `checksums.txt` SHA-256.

Cada instalador incluye un runtime de Java 21, así que el usuario final no necesita Java instalado.

## Datos

TaskFlow guarda todos los datos de usuario en un directorio por usuario con la base SQLite (`taskflow.db`) y el sonido de aviso (`bell.mp3`). La ubicación depende de la plataforma:

| Plataforma | Directorio de datos |
| --- | --- |
| Windows | `%APPDATA%\TaskFlow` |
| macOS | `~/Library/Application Support/TaskFlow` |
| Linux | `$XDG_DATA_HOME/TaskFlow`, con reserva a `~/.TaskFlow` |

Si ya existe un directorio heredado `~/.TaskFlow`, se conserva como directorio de datos por continuidad.

## Idioma

La interfaz de TaskFlow existe en exactamente seis idiomas:

| Idioma | Etiqueta |
| --- | --- |
| English | `en` |
| Spanish | `es` |
| German | `de` |
| French | `fr` |
| Italian | `it` |
| Chino simplificado | `zh-Hans` |

- **Cambio en vivo** — elige un idioma desde el botón del globo en la cabecera; toda la UI (incluido el estado abierto como la insignia de tareas y los tooltips) se redibuja al instante, sin reiniciar.
- **Persistente por usuario** — la elección se guarda en la tabla `preferences` (clave `language`) como etiqueta BCP 47 independiente del locale. Las bases existentes de 1.0.0 no necesitan migración: la clave simplemente se añade al guardar.
- **Detección en primer arranque** — en el primer lanzamiento el idioma se detecta desde el locale del SO. Los idiomas no soportados (incluido el portugués y cualquier idioma no distribuido) caen al inglés; los locales de chino tradicional también caen al inglés, ya que solo se distribuye chino simplificado.
- **Inglés como reserva determinista** — las traducciones viven en `src/main/resources/i18n/messages*.properties`; cualquier clave ausente resuelve al bundle base inglés, y una clave ausente en todas partes se devuelve tal cual (registrada una vez) en lugar de fallar. La suite de pruebas exige que existan exactamente estos seis bundles y que estén completos (paridad de claves y placeholders).
- **No traducido** — la salida de logs, los valores de base de datos, los códigos de prioridad/historial y los identificadores internos permanecen en inglés independiente del locale por compatibilidad de datos.

## Migración

Si usaste una versión anterior de TaskFlow que guardaba datos como JSON, la aplicación detecta los ficheros heredados en el primer arranque y los importa a SQLite automáticamente:

- `taskflow_data.json` (tareas)
- `taskflow_history.json` (historial)
- `taskflow_geometry.json` (geometría de ventana)
- `taskflow_settings.json` (tema y preferencias de ventana)

Los ficheros heredados se buscan en el directorio de datos y en la raíz del home (`~/.taskflow_data.json`, `~/.taskflow_history.json`, `~/.taskflow_geometry.json`). Cada fichero procesado se renombra con sufijo `.migrated` (p. ej. `taskflow_data.json.migrated`); el contenido original se conserva como copia de seguridad. Los fallos de migración se registran y nunca impiden arrancar la aplicación.

Desde el esquema 2 la base de datos conserva procedencia duradera: `legacy_sources` registra el tipo de origen y el SHA-256 de los bytes originales, y las filas importadas llevan el origen y una firma canónica. La fuente, las filas y su procedencia se confirman en una sola transacción, y el archivo solo se renombra después; así, reintentos, restauraciones y copias idénticas no duplican datos. Solo las filas importadas participan en la deduplicación: una fila creada por la aplicación nunca suprime un registro heredado. Las entradas repetidas dentro de un fichero conservan su multiplicidad y las instantáneas solapadas importan cada registro compartido una vez. La identidad del historial usa la hora local canónica del fichero, no un instante resuelto, de modo que un cambio de zona horaria no duplica ni altera la hora guardada; `event_at` tiene prioridad, luego `completed_at`, y los valores ilegibles usan una hora de reserva que no identifica registros. La geometría de la raíz del home se aplica antes que la del directorio de datos (gana esta última); los ajustes solo se leen del directorio de datos. La actualización de esquema es aditiva y conserva todos los datos; las filas anteriores al esquema 2 no se etiquetan como importadas porque su origen no se puede demostrar.

## Desarrollo

- **Formato** impuesto con Spotless (Google Java Format, estilo AOSP). Ejecuta `./gradlew spotlessApply` antes de commitear; CI falla ante violaciones de `spotlessCheck`.
- **Pruebas** con JUnit 6 y AssertJ: cubren el modelo de dominio, servicios, repositorios, migración heredada, internacionalización (completitud de bundles, reserva, detección de idioma), casos borde de temporizadores, comportamiento de esquema y transacciones, recuperación de geometría y un flujo end-to-end. JaCoCo exige un umbral mínimo del 45% de cobertura de líneas. Las pruebas corren headless — sin pantalla ni toolkit JavaFX. CI verifica la imagen empaquetada y sus recursos y arranca/cierra la aplicación en las tres plataformas (Xvfb en Linux). `python3 tools/upgrade_check.py` arranca la app empaquetada con una base v1 real (fixture `src/test/resources/schema-v1.sql`) y comprueba la actualización a v2, la conservación de datos y la procedencia NULL. `python3 tools/data_preservation_check.py` registra la base, `bell.mp3` y una tarea centinela, elimina el paquete instalado (desinstalación MSI, `.app` copiada, AppImage) y verifica que los datos sobreviven intactos. En Linux, `python3 tools/acceptance_check.py` ejecuta la app empaquetada en un Xvfb privado, con perfil desechable y un PCM ALSA de fichero privado: CRUD, temporizadores, historial, persistencia de tema/idioma/geometría y las rutas de sonido activado/desactivado/corrupto/ausente, exigiendo **muestras capturadas no silenciosas** al reproducir y silencio con el sonido desactivado, sin usar tu escritorio, ratón ni tarjeta de sonido. La audibilidad física y la calidad visual siguen requiriendo las comprobaciones manuales.

## Administración del repositorio

La protección recomendada de la rama `main`, incluidos checks de CI requeridos y consideraciones de commits firmados, está documentada en [docs/REPOSITORY_HARDENING.md](docs/REPOSITORY_HARDENING.md). Las reglas de rama requieren administración del repositorio en GitHub y no se configuran con ficheros fuente.
