# TaskFlow

TaskFlow es una app de escritorio (PyQt6) para gestionar tareas con temporizador tipo Pomodoro, prioridad visual e historial.

Está preparada para:
- **Uso diario en Linux/Wayland y Windows**.
- **Build de producción** con `PyInstaller` en un solo ejecutable.
- **Pipeline de iconos** desde un PNG maestro.

---

## Características

- Tarjetas de tarea con:
  - Nombre
  - Prioridad (`Alta`, `Media`, `Baja`)
  - Temporizador
  - Barra de progreso
- Solo una tarea puede estar en reproducción simultánea (autopausa del resto).
- Historial de tareas completadas.
- Persistencia local de:
  - Tareas
  - Historial
  - Geometría de ventana
- Comportamiento de ventana por plataforma:
  - **Windows**: ventana nativa (minimizar/maximizar/snap), con chincheta para *always-on-top*.
  - **Linux/Wayland**: modo frameless con comportamiento flotante.

---

## Estructura del proyecto

```text
.
├── src/
│   ├── main.py           # Entry point
│   ├── app.py            # Ventana principal y layout global
│   ├── task_card.py      # Tarjetas + temporizador por tarea
│   ├── dialogs.py        # Diálogos (crear tarea, historial)
│   ├── widgets.py        # Widgets reutilizables
│   └── config.py         # Config visual + persistencia
├── assets/
│   └── README.md         # Flujo de iconos
├── generate_icons.py     # Generador de iconos desde PNG maestro
├── compiler.py           # Build one-file con PyInstaller
└── README.md
```

---

## Requisitos

- Python **3.11+** (recomendado 3.12 en Windows)
- `pip`
- Dependencias:
  - `PyQt6`
  - `Pillow` (solo para generar iconos)
  - `PyInstaller` (solo para compilar ejecutable)

---

## Entorno de desarrollo

### 1) Crear entorno virtual

**Windows (PowerShell):**

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

**Linux/macOS:**

```bash
python -m venv .venv
source .venv/bin/activate
```

### 2) Instalar dependencias

```bash
pip install PyQt6 pillow pyinstaller
```

### 3) Ejecutar en local

```bash
python src/main.py
```

---

## Pipeline de iconos (recomendado para producción)

1. Coloca el icono maestro en:
   - `assets/TaskFlow.png` (o `assets/taskflow.png`)
2. Genera variantes:

```bash
python generate_icons.py
```

Esto generará:
- `assets/taskflow.ico` (usado por Windows/PyInstaller)
- `assets/generated/taskflow-*.png`
- `assets/taskflow.icns` (si Pillow soporta ICNS)

---

## Build de producción (Windows/Linux)

Compila un único binario con icono:

```bash
python compiler.py
```

Salida esperada:
- `dist/TaskFlow` (Linux/macOS)
- `dist/TaskFlow.exe` (Windows)

### Qué hace `compiler.py`

- Detecta icono automáticamente desde `assets/` (`taskflow.*` y `TaskFlow.*`).
- Ejecuta `PyInstaller` en modo:
  - `--onefile`
  - `--windowed`
  - `--icon ...`
- Incluye el icono también como recurso para que la app pueda cargarlo en runtime.

---

## Persistencia de datos

TaskFlow guarda datos en el home del usuario:

- `~/.taskflow_data.json`
- `~/.taskflow_history.json`
- `~/.taskflow_geometry.json`

> En Windows se resuelven dentro de `C:\Users\<usuario>\`.

---

## Checklist rápida de release

1. `python -m py_compile compiler.py generate_icons.py src/*.py`
2. `python generate_icons.py`
3. `python compiler.py`
4. Probar `dist/TaskFlow(.exe)`:
   - abrir/cerrar
   - crear tareas
   - iniciar/pausar temporizador
   - historial
   - icono en app y ejecutable
   - comportamiento snap (Windows)

---

## Troubleshooting

### El icono no sale bien en Windows

- Usa un `.ico` multiresolución (16, 20, 24, 32, 40, 48, 64, 128, 256).
- Regenera iconos con `python generate_icons.py`.
- Recompila con `python compiler.py`.
- Si Explorer cachea iconos antiguos, reinicia explorer o limpia caché de iconos.

### `generate_icons.py` pide Pillow

Instala dependencia:

```bash
pip install pillow
```

### Error al compilar con PyInstaller

Verifica instalación:

```bash
pip install pyinstaller
```

---

## Licencia

Este proyecto se distribuye bajo la licencia indicada en `LICENSE`.
