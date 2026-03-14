"""
dialogs.py — Diálogos de la aplicación.

Contiene:
  - AddDialog: formulario modal para crear una nueva tarea.
"""

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QColor, QCursor
from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QFrame,
    QLabel, QLineEdit, QSpinBox, QComboBox,
    QPushButton, QGraphicsDropShadowEffect, QScrollArea, QWidget,
)
import config


class AddDialog(QDialog):
    """Diálogo para añadir una nueva tarea."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowFlags(Qt.WindowType.Dialog | Qt.WindowType.FramelessWindowHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setFixedSize(290, 240)
        self._drag_pos = None
        self._build()

    # -- construcción de la interfaz -----------------------------------------
    def _build(self) -> None:
        outer = QVBoxLayout(self)
        outer.setContentsMargins(8, 8, 8, 8)

        card = QFrame()
        card.setObjectName("addDialogCard")
        card.setStyleSheet(
            f"#addDialogCard {{ background: {config.BG_ELEVATED};"
            f"  border: 1px solid {config.BORDER_LIGHT}; border-radius: 14px; }}"
        )

        shadow = QGraphicsDropShadowEffect()
        shadow.setBlurRadius(40)
        shadow.setOffset(0, 10)
        shadow.setColor(QColor(0, 0, 0, 220))
        card.setGraphicsEffect(shadow)

        lay = QVBoxLayout(card)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.setSpacing(10)

        # Título
        self.title = QLabel("Nueva tarea")
        self.title.setStyleSheet(
            f"color: {config.TEXT_HI}; font-size: 15px; font-weight: 700;"
            "background: transparent;"
        )
        lay.addWidget(self.title)

        # Campo de nombre
        self.inp = QLineEdit()
        self.inp.setPlaceholderText("¿Qué vas a hacer?")
        self.inp.setStyleSheet(
            f"QLineEdit {{ background: {config.BG_SURFACE}; color: {config.TEXT_HI};"
            f"  border: 1px solid {config.BORDER}; border-radius: 8px;"
            f"  padding: 7px 10px; font-size: 13px; }}"
            f"QLineEdit:focus {{ border-color: {config.ACCENT}; }}"
        )
        lay.addWidget(self.inp)

        # Fila: minutos + prioridad
        row = QHBoxLayout()
        row.setSpacing(10)

        # — Columna tiempo
        col_time = QVBoxLayout()
        col_time.setSpacing(3)
        lbl_time = QLabel("Minutos")
        lbl_time.setStyleSheet(
            f"color: {config.TEXT_MID}; font-size: 11px; background: transparent;"
        )
        self.spin = QSpinBox()
        self.spin.setRange(1, 999)
        self.spin.setValue(25)
        self.spin.setStyleSheet(
            f"QSpinBox {{ background: {config.BG_SURFACE}; color: {config.TEXT_HI};"
            f"  border: 1px solid {config.BORDER}; border-radius: 8px;"
            f"  padding: 6px 8px; font-size: 13px; }}"
            f"QSpinBox::up-button, QSpinBox::down-button {{"
            f"  width: 18px; background: {config.BORDER}; border: none; }}"
            f"QSpinBox::up-button:hover, QSpinBox::down-button:hover {{ background: {config.BORDER_LIGHT}; }}"
            f"QSpinBox::up-button:pressed, QSpinBox::down-button:pressed {{ background: rgba(255, 255, 255, 0.4); }}"
            f"QSpinBox::up-button {{ border-bottom: 0.5px solid {config.BORDER_LIGHT}; border-top-right-radius: 7px; }}"
            f"QSpinBox::down-button {{ border-bottom-right-radius: 7px; }}"
        )
        col_time.addWidget(lbl_time)
        col_time.addWidget(self.spin)

        # — Columna prioridad
        col_pri = QVBoxLayout()
        col_pri.setSpacing(3)
        lbl_pri = QLabel("Prioridad")
        lbl_pri.setStyleSheet(
            f"color: {config.TEXT_MID}; font-size: 11px; background: transparent;"
        )
        self.combo = QComboBox()
        self.combo.addItems(["Alta", "Media", "Baja"])
        self.combo.setStyleSheet(
            f"QComboBox {{ background: {config.BG_SURFACE}; color: {config.TEXT_HI};"
            f"  border: 1px solid {config.BORDER}; border-radius: 8px;"
            f"  padding: 6px 10px; font-size: 13px; }}"
            f"QComboBox::drop-down {{ border: none; width: 22px; }}"
            f"QComboBox QAbstractItemView {{"
            f"  background: {config.BG_ELEVATED}; color: {config.TEXT_HI};"
            f"  border: 1px solid {config.BORDER_LIGHT};"
            f"  selection-background-color: {config.with_alpha(config.ACCENT, 0x44)}; }}"
        )
        col_pri.addWidget(lbl_pri)
        col_pri.addWidget(self.combo)

        row.addLayout(col_time)
        row.addLayout(col_pri)
        lay.addLayout(row)

        # Botones: Cancelar | Agregar
        btn_row = QHBoxLayout()
        btn_row.setSpacing(8)

        btn_cancel = QPushButton("Cancelar")
        btn_cancel.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        btn_cancel.setStyleSheet(
            f"QPushButton {{ background: transparent; color: {config.TEXT_MID};"
            f"  border: 1px solid {config.BORDER}; border-radius: 8px;"
            f"  padding: 7px 14px; font-size: 12px; }}"
            f"QPushButton:hover {{ color: {config.TEXT_HI}; border-color: {config.BORDER_LIGHT}; }}"
        )
        btn_cancel.clicked.connect(self.reject)

        self.btn_ok = QPushButton("Agregar")
        self.btn_ok.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_ok.setStyleSheet(
            f"QPushButton {{ background: {config.ACCENT}; color: #ffffff;"
            f"  border: none; border-radius: 8px;"
            f"  padding: 7px 14px; font-size: 12px; font-weight: 700; }}"
            f"QPushButton:hover {{ background: {config.ACCENT_LT}; }}"
        )
        self.btn_ok.clicked.connect(self._ok)

        btn_row.addWidget(btn_cancel, 1)
        btn_row.addWidget(self.btn_ok, 1)
        lay.addLayout(btn_row)

        outer.addWidget(card)

    # -- validación / datos ---------------------------------------------------
    def _ok(self) -> None:
        if not self.inp.text().strip():
            self.inp.setFocus()
            return
        self.accept()

    def get(self) -> dict:
        """Devuelve un diccionario con los datos de la nueva tarea."""
        minutes = self.spin.value()
        return {
            "name": self.inp.text().strip(),
            "priority": self.combo.currentText(),
            "total_seconds": minutes * 60,
            "remaining": minutes * 60,
        }

    # -- arrastre del diálogo -------------------------------------------------
    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self._drag_pos = (
                event.globalPosition().toPoint() - self.frameGeometry().topLeft()
            )

    def mouseMoveEvent(self, event):
        if event.buttons() == Qt.MouseButton.LeftButton and self._drag_pos is not None:
            self.move(event.globalPosition().toPoint() - self._drag_pos)

    def mouseReleaseEvent(self, _event):
        self._drag_pos = None


class EditDialog(AddDialog):
    """Diálogo para editar una tarea existente."""

    def __init__(self, task: dict, parent=None):
        self._task = task
        super().__init__(parent)
        self._hydrate_task()

    def _build(self) -> None:
        super()._build()
        self.title.setText("Editar tarea")
        self.btn_ok.setText("Guardar")

    def _hydrate_task(self) -> None:
        self.inp.setText(self._task.get("name", ""))
        total_seconds = max(60, int(self._task.get("total_seconds", 1500)))
        minutes = max(1, total_seconds // 60)
        self.spin.setValue(minutes)

        priority = self._task.get("priority", "Media")
        idx = self.combo.findText(priority)
        self.combo.setCurrentIndex(idx if idx >= 0 else 1)

    def get(self) -> dict:
        data = super().get()
        previous_remaining = max(0, int(self._task.get("remaining", data["total_seconds"])))
        old_total = max(1, int(self._task.get("total_seconds", data["total_seconds"])))

        # Escalar el progreso para no perder el avance relativo al cambiar tiempo
        ratio = previous_remaining / old_total
        data["remaining"] = max(0, int(round(data["total_seconds"] * ratio)))
        return data


class HistoryDialog(QDialog):
    """Diálogo para ver el historial de tareas completadas y eliminadas."""

    sig_restore_task = pyqtSignal(int)

    def __init__(self, history: list[dict], parent=None):
        super().__init__(parent)
        self.history = history
        self.setWindowFlags(Qt.WindowType.Dialog | Qt.WindowType.FramelessWindowHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setFixedSize(420, 420)
        self._drag_pos = None
        self._build()

    def _build(self) -> None:
        outer = QVBoxLayout(self)
        outer.setContentsMargins(8, 8, 8, 8)

        card = QFrame()
        card.setObjectName("historyCard")
        card.setStyleSheet(
            f"#historyCard {{ background: {config.BG_ELEVATED};"
            f"  border: 1px solid {config.BORDER_LIGHT}; border-radius: 14px; }}"
        )

        shadow = QGraphicsDropShadowEffect()
        shadow.setBlurRadius(40)
        shadow.setOffset(0, 10)
        shadow.setColor(QColor(0, 0, 0, 220))
        card.setGraphicsEffect(shadow)

        lay = QVBoxLayout(card)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.setSpacing(12)

        # Título y cerrar
        header = QHBoxLayout()
        title = QLabel("Historial")
        title.setStyleSheet(
            f"color: {config.TEXT_HI}; font-size: 16px; font-weight: 700;"
            "background: transparent;"
        )
        btn_close = QPushButton("✕")
        btn_close.setFixedSize(24, 24)
        btn_close.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        btn_close.setStyleSheet(
            f"QPushButton {{ background: transparent; color: {config.TEXT_MID};"
            "  border: none; font-size: 11px; border-radius: 12px; }}"
            f"QPushButton:hover {{ color: {config.ACCENT}; background: {config.with_alpha(config.ACCENT, 0x20)}; }}"
        )
        btn_close.clicked.connect(self.accept)

        header.addWidget(title)
        header.addStretch()
        header.addWidget(btn_close)
        lay.addLayout(header)

        # Área de scroll
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setStyleSheet(
            "QScrollArea { border: none; background: transparent; }"
            f"QScrollBar:vertical {{ background: transparent; width: 4px; }}"
            f"QScrollBar::handle:vertical {{ background: {config.BORDER}; border-radius: 2px; }}"
        )

        content = QWidget()
        item_lay = QVBoxLayout(content)
        item_lay.setContentsMargins(0, 0, 6, 0)
        item_lay.setSpacing(8)

        if not self.history:
            empty = QLabel("No hay tareas en el historial")
            empty.setStyleSheet(f"color: {config.TEXT_MID}; font-size: 12px;")
            empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
            item_lay.addWidget(empty)
        else:
            # Mostrar de más reciente a más antigua
            for history_idx in range(len(self.history) - 1, -1, -1):
                h = self.history[history_idx]
                f = QFrame()
                f.setStyleSheet(
                    f"background: {config.BG_SURFACE}; border: 1px solid {config.BORDER};"
                    "border-radius: 8px; padding: 6px;"
                )
                flay = QVBoxLayout(f)
                flay.setContentsMargins(10, 8, 10, 8)
                flay.setSpacing(6)

                name = QLabel(h["name"])
                name.setStyleSheet(f"color: {config.TEXT_HI}; font-size: 13px; font-weight: 600; border: none;")

                date_str = h.get("event_at", h.get("completed_at", "Desconocido"))
                event_type = h.get("history_event", "completed")
                if event_type == "deleted":
                    mode = "Eliminada"
                elif h.get("completed_manually"):
                    mode = "Completada manual"
                else:
                    mode = "Completada por temporizador"
                date = QLabel(f"{date_str} · {mode}")
                date.setStyleSheet(f"color: {config.TEXT_MID}; font-size: 10px; border: none;")

                btn_restore = QPushButton("Restaurar")
                btn_restore.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
                btn_restore.setStyleSheet(
                    f"QPushButton {{ background: transparent; color: {config.ACCENT_LT};"
                    f" border: 1px solid {config.BORDER}; border-radius: 7px;"
                    " padding: 4px 8px; font-size: 11px; font-weight: 600; }}"
                    f"QPushButton:hover {{ background: {config.with_alpha(config.ACCENT, 0x22)}; border-color: {config.ACCENT}; }}"
                )
                btn_restore.clicked.connect(
                    lambda _checked=False, idx=history_idx: self._restore(idx)
                )

                footer = QHBoxLayout()
                footer.addWidget(date)
                footer.addStretch()
                footer.addWidget(btn_restore)

                flay.addWidget(name)
                flay.addLayout(footer)
                item_lay.addWidget(f)

        item_lay.addStretch()
        scroll.setWidget(content)
        lay.addWidget(scroll)

        outer.addWidget(card)

    def _restore(self, history_idx: int) -> None:
        """Notifica que se quiere restaurar una tarea del historial."""
        self.sig_restore_task.emit(history_idx)
        self.accept()

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self._drag_pos = (
                event.globalPosition().toPoint() - self.frameGeometry().topLeft()
            )

    def mouseMoveEvent(self, event):
        if event.buttons() == Qt.MouseButton.LeftButton and self._drag_pos is not None:
            self.move(event.globalPosition().toPoint() - self._drag_pos)

    def mouseReleaseEvent(self, _event):
        self._drag_pos = None
