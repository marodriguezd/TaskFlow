"""Build script for TaskFlow using PyInstaller.

Generates a single-file executable and applies the icon from ./assets.
Usage:
    python compiler.py
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
MAIN_FILE = ROOT / "src" / "main.py"
ASSETS_DIR = ROOT / "assets"


def _resolve_icon() -> Path:
    preferred_candidates = (
        ASSETS_DIR / "taskflow.ico",
        ASSETS_DIR / "TaskFlow.ico",
        ASSETS_DIR / "taskflow.png",
        ASSETS_DIR / "TaskFlow.png",
        ASSETS_DIR / "taskflow.icns",
        ASSETS_DIR / "TaskFlow.icns",
    )
    for candidate in preferred_candidates:
        if candidate.exists():
            return candidate

    for pattern in ("*.ico", "*.icns", "*.png"):
        matches = sorted(ASSETS_DIR.glob(pattern))
        if matches:
            return matches[0]

    raise FileNotFoundError(
        "No se encontró icono en ./assets. Añade por ejemplo assets/taskflow.ico o assets/TaskFlow.png"
    )


def build() -> None:
    if not MAIN_FILE.exists():
        raise FileNotFoundError(f"No existe entrypoint: {MAIN_FILE}")

    icon = _resolve_icon()

    command = [
        sys.executable,
        "-m",
        "PyInstaller",
        "--noconfirm",
        "--clean",
        "--onefile",
        "--windowed",
        "--name",
        "TaskFlow",
        "--icon",
        str(icon),
        "--add-data",
        f"{icon}{';' if sys.platform.startswith('win') else ':'}assets",
        str(MAIN_FILE),
    ]

    print("Ejecutando:", " ".join(command))
    subprocess.run(command, check=True, cwd=ROOT)
    print("Build completada. Ejecutable en ./dist/TaskFlow")


if __name__ == "__main__":
    build()
