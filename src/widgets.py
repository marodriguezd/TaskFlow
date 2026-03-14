"""
widgets.py — Widgets reutilizables sin lógica de negocio.

Contiene:
  - ProgressBar: barra de progreso con degradado pintada a mano.
  - DragHeader:  cabecera que permite arrastrar una ventana frameless.
  - ResizeGrip:  grip para redimensionar una ventana frameless.
"""

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QColor, QPainter, QLinearGradient, QCursor
from PyQt6.QtWidgets import QWidget

import config


# ─────────────────────────────────────────────
#  ProgressBar
# ─────────────────────────────────────────────
class ProgressBar(QWidget):
    """Barra de progreso mínima con degradado."""

    def __init__(self, total: int, current: int, color: str, parent=None):
        super().__init__(parent)
        self.total = max(1, total)
        self.current = current
        self.color = QColor(color)
        self.setFixedHeight(4)

    def set_value(self, value: int) -> None:
        self.current = value
        self.update()

    # -- pintura personalizada ------------------------------------------------
    def paintEvent(self, _event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        w, h = self.width(), self.height()
        r = h // 2

        # Fondo
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor(config.BORDER))
        painter.drawRoundedRect(0, 0, w, h, r, r)

        # Relleno
        fill_w = max(0, int(w * self.current / self.total))
        if fill_w > 0:
            grad = QLinearGradient(0, 0, fill_w, 0)
            grad.setColorAt(0.0, self.color.darker(130))
            grad.setColorAt(1.0, self.color)
            painter.setBrush(grad)
            painter.drawRoundedRect(0, 0, fill_w, h, r, r)


# ─────────────────────────────────────────────
#  DragHeader
# ─────────────────────────────────────────────
class DragHeader(QWidget):
    """Cabecera que permite arrastrar la ventana padre (frameless).

    Usa ``QWindow.startSystemMove()`` para compatibilidad con Wayland,
    donde ``QWidget.move()`` es ignorado por el compositor.
    """

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(52)
        self.setCursor(QCursor(Qt.CursorShape.OpenHandCursor))

    # -- eventos de ratón -----------------------------------------------------
    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            window_handle = self.window().windowHandle()
            if window_handle is not None:
                window_handle.startSystemMove()
        super().mousePressEvent(event)
