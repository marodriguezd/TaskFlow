"""
config.py — Tokens de diseño, constantes y helpers de persistencia.

Responsabilidad única: almacenar la configuración visual y
proporcionar funciones puras de lectura/escritura de datos.
"""

import json
import os
import shutil

# ─────────────────────────────────────────────
#  Dimensiones por defecto
# ─────────────────────────────────────────────
PANEL_MIN_WIDTH = 300
PANEL_MAX_WIDTH = 500
PANEL_MIN_HEIGHT = 420
PANEL_MAX_HEIGHT = 800

# ─────────────────────────────────────────────
#  Paleta de colores
# ─────────────────────────────────────────────
BG_BASE = "#111114"
BG_SURFACE = "#1a1a1f"
BG_ELEVATED = "#222228"
BORDER = "#2e2e38"
BORDER_LIGHT = "#3e3e4e"
TEXT_HI = "#f0f0f8"
TEXT_MID = "#a0a0b8"
TEXT_LO = "#606078"
ACCENT = "#7c6af7"
ACCENT_LT = "#a898ff"

# ─────────────────────────────────────────────
#  Prioridades
# ─────────────────────────────────────────────
PRIORITY = {
    "Alta":  {"fg": "#ff5e78", "bg": "#1a1a1f", "pill": "#2a0a0a"},
    "Media": {"fg": "#ffb340", "bg": "#1a1a1f", "pill": "#2a1a00"},
    "Baja":  {"fg": "#3ddc84", "bg": "#1a1a1f", "pill": "#021a0c"},
}
P_ORDER = {"Alta": 0, "Media": 1, "Baja": 2}

# ─────────────────────────────────────────────
#  Persistencia
# ─────────────────────────────────────────────
HOME_DIR = os.path.expanduser("~")
DATA_DIR = os.path.join(HOME_DIR, ".TaskFlow")
DATA_FILE = os.path.join(DATA_DIR, "taskflow_data.json")
HISTORY_FILE = os.path.join(DATA_DIR, "taskflow_history.json")
GEOMETRY_FILE = os.path.join(DATA_DIR, "taskflow_geometry.json")

LEGACY_FILES = {
    os.path.join(HOME_DIR, ".taskflow_data.json"): DATA_FILE,
    os.path.join(HOME_DIR, ".taskflow_history.json"): HISTORY_FILE,
    os.path.join(HOME_DIR, ".taskflow_geometry.json"): GEOMETRY_FILE,
}


def _ensure_data_dir() -> None:
    """Garantiza que el directorio de datos exista."""
    try:
        os.makedirs(DATA_DIR, exist_ok=True)
    except OSError:
        pass


def _migrate_legacy_files() -> None:
    """Migra ficheros legacy del home a ~/.TaskFlow si existen."""
    found_legacy = any(os.path.exists(src) for src in LEGACY_FILES)
    if not found_legacy:
        return

    _ensure_data_dir()

    for src, dst in LEGACY_FILES.items():
        if not os.path.exists(src) or os.path.exists(dst):
            continue
        try:
            shutil.move(src, dst)
        except OSError:
            pass


_migrate_legacy_files()


def fmt(seconds: int) -> str:
    """Formatea *seconds* como MM:SS."""
    m, sec = divmod(max(0, seconds), 60)
    return f"{m:02d}:{sec:02d}"


def load_tasks() -> list[dict]:
    """Carga la lista de tareas desde disco (lista vacía si no existe)."""
    if os.path.exists(DATA_FILE):
        try:
            with open(DATA_FILE, encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            pass
    return []


def save_tasks(tasks: list[dict]) -> None:
    """Persiste la lista de tareas a disco."""
    _ensure_data_dir()
    try:
        with open(DATA_FILE, "w", encoding="utf-8") as f:
            json.dump(tasks, f, indent=2, ensure_ascii=False)
    except OSError:
        pass


def load_history() -> list[dict]:
    """Carga el historial de tareas completadas."""
    if os.path.exists(HISTORY_FILE):
        try:
            with open(HISTORY_FILE, encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            pass
    return []


def save_history(history: list[dict]) -> None:
    """Persiste el historial de tareas completadas."""
    _ensure_data_dir()
    try:
        with open(HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, indent=2, ensure_ascii=False)
    except OSError:
        pass


def load_geometry() -> dict | None:
    """Carga la geometría guardada (x, y, width, height) o None."""
    if os.path.exists(GEOMETRY_FILE):
        try:
            with open(GEOMETRY_FILE, encoding="utf-8") as f:
                data = json.load(f)
            # Validar que tiene las claves necesarias
            if all(k in data for k in ("x", "y", "width", "height")):
                return data
        except (json.JSONDecodeError, OSError):
            pass
    return None


def save_geometry(x: int, y: int, width: int, height: int) -> None:
    """Persiste la geometría de la ventana a disco."""
    _ensure_data_dir()
    try:
        with open(GEOMETRY_FILE, "w", encoding="utf-8") as f:
            json.dump({"x": x, "y": y, "width": width, "height": height},
                      f, indent=2)
    except OSError:
        pass
