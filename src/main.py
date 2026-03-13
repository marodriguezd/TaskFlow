"""
TaskFlow — Panel de tareas con temporizador.

Entry point de la aplicación.
Instala dependencias con:
    pip install PyQt6
"""

import sys

from PyQt6.QtGui import QColor, QPalette
from PyQt6.QtWidgets import QApplication

from config import BG_BASE, BG_SURFACE, TEXT_HI, ACCENT
from app import TaskFlow


def main() -> None:
    app = QApplication(sys.argv)
    app.setApplicationName("TaskFlow")

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