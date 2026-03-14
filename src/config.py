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
#  Temas
# ─────────────────────────────────────────────
THEMES = {
    "dark": {
        "BG_BASE": "#111114",
        "BG_SURFACE": "#1a1a1f",
        "BG_ELEVATED": "#222228",
        "BORDER": "#2e2e38",
        "BORDER_LIGHT": "#3e3e4e",
        "TEXT_HI": "#f0f0f8",
        "TEXT_MID": "#a0a0b8",
        "TEXT_LO": "#606078",
        "ACCENT": "#7c6af7",
        "ACCENT_LT": "#a898ff",
    },
    "light": {
        "BG_BASE": "#f4f7fc",
        "BG_SURFACE": "#ffffff",
        "BG_ELEVATED": "#f8fbff",
        "BORDER": "#d4deed",
        "BORDER_LIGHT": "#bfcee4",
        "TEXT_HI": "#132033",
        "TEXT_MID": "#4f6382",
        "TEXT_LO": "#7b8faa",
        "ACCENT": "#2f7ef7",
        "ACCENT_LT": "#5a9bff",
    },
}

BG_BASE = THEMES["dark"]["BG_BASE"]
BG_SURFACE = THEMES["dark"]["BG_SURFACE"]
BG_ELEVATED = THEMES["dark"]["BG_ELEVATED"]
BORDER = THEMES["dark"]["BORDER"]
BORDER_LIGHT = THEMES["dark"]["BORDER_LIGHT"]
TEXT_HI = THEMES["dark"]["TEXT_HI"]
TEXT_MID = THEMES["dark"]["TEXT_MID"]
TEXT_LO = THEMES["dark"]["TEXT_LO"]
ACCENT = THEMES["dark"]["ACCENT"]
ACCENT_LT = THEMES["dark"]["ACCENT_LT"]




def with_alpha(color: str, alpha: int) -> str:
    """Devuelve un color rgba() a partir de #RRGGBB + alpha [0..255]."""
    if not isinstance(color, str) or not color.startswith("#") or len(color) != 7:
        return color
    try:
        r = int(color[1:3], 16)
        g = int(color[3:5], 16)
        b = int(color[5:7], 16)
        a = max(0, min(255, int(alpha)))
    except ValueError:
        return color
    return f"rgba({r}, {g}, {b}, {a})"
def apply_theme(theme_name: str) -> str:
    """Aplica el tema indicado y devuelve el nombre efectivo."""
    theme = THEMES.get(theme_name, THEMES["dark"])
    effective_name = theme_name if theme_name in THEMES else "dark"

    global BG_BASE, BG_SURFACE, BG_ELEVATED, BORDER, BORDER_LIGHT
    global TEXT_HI, TEXT_MID, TEXT_LO, ACCENT, ACCENT_LT

    BG_BASE = theme["BG_BASE"]
    BG_SURFACE = theme["BG_SURFACE"]
    BG_ELEVATED = theme["BG_ELEVATED"]
    BORDER = theme["BORDER"]
    BORDER_LIGHT = theme["BORDER_LIGHT"]
    TEXT_HI = theme["TEXT_HI"]
    TEXT_MID = theme["TEXT_MID"]
    TEXT_LO = theme["TEXT_LO"]
    ACCENT = theme["ACCENT"]
    ACCENT_LT = theme["ACCENT_LT"]

    return effective_name


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
PREFS_FILE = os.path.join(DATA_DIR, "taskflow_settings.json")

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


def load_preferences() -> dict:
    """Carga preferencias de UI (tema, chincheta, etc.)."""
    defaults = {"theme": "dark", "always_on_top": None}
    if os.path.exists(PREFS_FILE):
        try:
            with open(PREFS_FILE, encoding="utf-8") as f:
                data = json.load(f)
            if isinstance(data, dict):
                defaults.update(data)
        except (json.JSONDecodeError, OSError):
            pass
    return defaults


def save_preferences(preferences: dict) -> None:
    """Guarda preferencias de UI en disco."""
    _ensure_data_dir()
    try:
        with open(PREFS_FILE, "w", encoding="utf-8") as f:
            json.dump(preferences, f, indent=2, ensure_ascii=False)
    except OSError:
        pass
