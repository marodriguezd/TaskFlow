"""
TaskFlow — Panel de tareas con temporizador.

Entry point de la aplicación.
Instala dependencias con:
    pip install PyQt6
"""

import sys
from pathlib import Path

from PyQt6.QtGui import QColor, QIcon, QPalette
from PyQt6.QtWidgets import QApplication

from config import BG_BASE, BG_SURFACE, TEXT_HI, ACCENT
from app import TaskFlow


def _icon_path() -> Path | None:
    """Devuelve la ruta del icono (soporta ejecución normal y PyInstaller)."""
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        base = Path(sys._MEIPASS)
    else:
        base = Path(__file__).resolve().parent.parent

    candidates = [
        base / "assets" / "taskflow.ico",
        base / "assets" / "taskflow.png",
        base / "assets" / "taskflow.icns",
    ]
    for icon in candidates:
        if icon.exists():
            return icon
    return None


def main() -> None:
    app = QApplication(sys.argv)
    app.setApplicationName("TaskFlow")

    icon = _icon_path()
    if icon is not None:
        app.setWindowIcon(QIcon(str(icon)))

    # Paleta oscura global
    palette = QPalette()
    palette.setColor(QPalette.ColorRole.Window, QColor(BG_BASE))
    palette.setColor(QPalette.ColorRole.WindowText, QColor(TEXT_HI))
    palette.setColor(QPalette.ColorRole.Base, QColor(BG_SURFACE))
    palette.setColor(QPalette.ColorRole.Text, QColor(TEXT_HI))
    palette.setColor(QPalette.ColorRole.ButtonText, QColor(TEXT_HI))
    palette.setColor(QPalette.ColorRole.Highlight, QColor(ACCENT))
    palette.setColor(QPalette.ColorRole.HighlightedText, QColor("#ffffff"))
    app.setPalette(palette)

    window = TaskFlow()
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()