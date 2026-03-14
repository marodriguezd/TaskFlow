"""
task_card.py — Widget de tarjeta de tarea.

Contiene:
  - TaskCard: muestra nombre, prioridad, temporizador y barra de progreso.
"""

from PyQt6.QtCore import Qt, QTimer, pyqtSignal
from PyQt6.QtGui import QCursor
from PyQt6.QtWidgets import (
    QFrame, QHBoxLayout, QVBoxLayout, QWidget,
    QLabel, QPushButton, QSizePolicy, QApplication,
)

import config
from widgets import ProgressBar


class TaskCard(QFrame):
    """Tarjeta visual para una tarea individual con temporizador."""

    sig_delete = pyqtSignal(int)
    sig_edit = pyqtSignal(int)
    sig_tick = pyqtSignal()
    sig_play_requested = pyqtSignal(int)  # index de la tarea
    sig_completed = pyqtSignal(int)      # index de la tarea
    sig_completed_manual = pyqtSignal(int)  # index de la tarea

    def __init__(self, task: dict, index: int, parent=None):
        super().__init__(parent)
        self.task = task
        self.index = index
        self.running = False
        self._hovered = False
        self.remaining = task.get("remaining", task["total_seconds"])
        task["remaining"] = self.remaining

        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self.setMinimumHeight(86)

        self._build()
        self._refresh_style()

        self._timer = QTimer(self)
        self._timer.setInterval(1000)
        self._timer.timeout.connect(self._tick)

    # -- construcción de la interfaz -----------------------------------------
    def _build(self) -> None:
        pri = config.PRIORITY[self.task["priority"]]

        outer = QHBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)

        # Indicador lateral de color
        self._side = QFrame()
        self._side.setFixedWidth(4)
        self._side.setObjectName("sideBar")
        self._side.setStyleSheet(
            f"#sideBar {{ background: {pri['fg']};"
            "  border-radius: 4px 0 0 4px; }}"
        )
        outer.addWidget(self._side)

        # Contenido interior
        inner = QWidget()
        inner.setObjectName("cardInner")
        lay = QVBoxLayout(inner)
        lay.setContentsMargins(12, 10, 10, 10)
        lay.setSpacing(6)

        # Fila 1: nombre + pill + editar + borrar
        row1 = QHBoxLayout()
        row1.setSpacing(6)

        self.lbl_name = QLabel(self.task["name"])
        self.lbl_name.setStyleSheet(
            f"color: {config.TEXT_HI}; font-size: 13px; font-weight: 600;"
            "background: transparent;"
        )
        self.lbl_name.setWordWrap(True)

        self.pill = QLabel(self.task["priority"])
        self.pill.setStyleSheet(
            f"color: {pri['fg']}; background: {pri['pill']};"
            "font-size: 10px; font-weight: 700;"
            "border-radius: 5px; padding: 1px 6px;"
        )
        self.pill.setFixedHeight(18)

        self.btn_edit = QPushButton("✎")
        self.btn_edit.setFixedSize(20, 20)
        self.btn_edit.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_edit.setStyleSheet(
            f"QPushButton {{ background: transparent; color: {config.TEXT_LO};"
            "  border: none; font-size: 11px; border-radius: 10px; }}"
            f"QPushButton:hover {{ color: {config.ACCENT_LT}; background: {config.with_alpha(config.ACCENT_LT, 0x22)}; }}"
        )
        self.btn_edit.clicked.connect(lambda: self.sig_edit.emit(self.index))

        self.btn_del = QPushButton("✕")
        self.btn_del.setFixedSize(20, 20)
        self.btn_del.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_del.setStyleSheet(
            f"QPushButton {{ background: transparent; color: {config.TEXT_LO};"
            "  border: none; font-size: 10px; border-radius: 10px; }}"
            "QPushButton:hover { color: #ff5e78; background: #ff5e7822; }"
        )
        self.btn_del.clicked.connect(lambda: self.sig_delete.emit(self.index))

        row1.addWidget(self.lbl_name, 1)
        row1.addWidget(self.pill)
        row1.addWidget(self.btn_edit)
        row1.addWidget(self.btn_del)

        # Fila 2: tiempo + play/pause + completar manualmente
        row2 = QHBoxLayout()
        row2.setSpacing(10)

        self.lbl_time = QLabel(config.fmt(self.remaining))
        self.lbl_time.setStyleSheet(
            f"color: {pri['fg']}; font-size: 26px; font-weight: 700;"
            "font-family: 'Courier New', monospace; letter-spacing: 2px;"
            "background: transparent;"
        )

        self.btn_play = QPushButton()
        self.btn_play.setFixedSize(36, 36)
        self.btn_play.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_play.clicked.connect(self.toggle)
        self._style_play()

        self.btn_done = QPushButton("✓")
        self.btn_done.setFixedSize(24, 24)
        self.btn_done.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_done.setToolTip("Marcar como completada")
        self.btn_done.setObjectName("btnDone")
        self.btn_done.setStyleSheet(
            f"QPushButton#btnDone {{ background: {pri['pill']}; color: {pri['fg']};"
            "  border: none; border-radius: 12px; font-size: 12px;"
            "  font-weight: 800; }}"
            f"QPushButton#btnDone:hover {{ background: {config.with_alpha(pri['pill'], 0xEE)}; }}"
            f"QPushButton#btnDone:pressed {{ background: {config.with_alpha(pri['pill'], 0xCC)}; }}"
        )
        self.btn_done.clicked.connect(self._complete_manually)

        row2.addWidget(self.lbl_time, 1)
        row2.addWidget(self.btn_done)
        row2.addWidget(self.btn_play)

        # Barra de progreso
        self.bar = ProgressBar(self.task["total_seconds"], self.remaining, pri["fg"])

        lay.addLayout(row1)
        lay.addLayout(row2)
        lay.addWidget(self.bar)
        outer.addWidget(inner, 1)

    # -- estilos dinámicos ---------------------------------------------------
    def _refresh_style(self) -> None:
        pri = config.PRIORITY[self.task["priority"]]
        bg = config.CARD_HOVER if self._hovered else config.BG_SURFACE
        border = pri["fg"] if self._hovered else config.BORDER
        self.setStyleSheet(
            f"TaskCard {{ background: transparent; border: 1px solid {border};"
            "  border-left: none; border-radius: 0 10px 10px 0; }}"
            f"#cardInner {{ background: {bg}; border-radius: 0 10px 10px 0; }}"
        )

    def _style_play(self) -> None:
        pri = config.PRIORITY[self.task["priority"]]
        self.btn_play.setObjectName("btnPlay")
        if self.remaining <= 0:
            icon, bg_col, fg_col = "✓", config.TEXT_LO, config.TEXT_LO
            bg_alpha = config.with_alpha(bg_col, 0x1A)
        else:
            icon = "❚❚" if self.running else "▶"
            fg_col = pri["fg"]    # Color vibrante de la prioridad
            bg_col = pri["pill"]  # Mismo color de fondo que la "cajita"
            bg_alpha = ""         # Usar el color del pill tal cual (ya tiene alpha si es necesario)

        self.btn_play.setText(icon)
        icon_size = 14 if icon == "❚❚" else 16
        self.btn_play.setStyleSheet(
            f"QPushButton#btnPlay {{ "
            f"  background: {bg_alpha if self.remaining <= 0 else bg_col}; "
            f"  color: {fg_col}; "
            f"  border: none; "
            f"  border-radius: 18px; "
            f"  font-size: {icon_size}px; "
            f"  font-weight: 900; "
            f"  padding-left: {2 if icon == '▶' else 0}px; "
            f"}} "
            f"QPushButton#btnPlay:hover {{ background: {config.with_alpha(bg_col, 0xEE)}; }} "
            f"QPushButton#btnPlay:pressed {{ background: {config.with_alpha(bg_col, 0xCC)}; }} "
        )

    # -- control del temporizador --------------------------------------------
    def toggle(self) -> None:
        if self.remaining <= 0:
            self.sig_delete.emit(self.index)
            return
        self.running = not self.running
        if self.running:
            self.sig_play_requested.emit(self.index)
            self._timer.start()
        else:
            self._timer.stop()
        self._style_play()

    def _tick(self) -> None:
        self.remaining -= 1
        self.task["remaining"] = self.remaining
        self.lbl_time.setText(config.fmt(self.remaining))
        self.bar.set_value(self.remaining)
        self.sig_tick.emit()
        if self.remaining <= 0:
            self._timer.stop()
            self.running = False
            QApplication.beep()
            self.sig_completed.emit(self.index)
            self._style_play()
            pri = config.PRIORITY[self.task["priority"]]
            self.setStyleSheet(
                f"TaskCard {{ background: transparent;"
                f"  border: 1px solid {pri['fg']}88;"
                "  border-left: none; border-radius: 0 10px 10px 0; }}"
                f"#cardInner {{ background: {config.BG_SURFACE};"
                "  border-radius: 0 10px 10px 0; }}"
            )

    def _complete_manually(self) -> None:
        """Marca la tarea como completada sin esperar al temporizador."""
        self.stop()
        self.sig_completed_manual.emit(self.index)

    def stop(self) -> None:
        """Detiene el temporizador de esta tarjeta."""
        self._timer.stop()
        self.running = False

    # -- hover ---------------------------------------------------------------
    def enterEvent(self, event):
        self._hovered = True
        self._refresh_style()

    def leaveEvent(self, event):
        self._hovered = False
        self._refresh_style()
