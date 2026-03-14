"""
app.py — Ventana principal de TaskFlow.

Responsabilidad: orquestar la UI (cabecera, lista de tareas, footer)
y gestionar el ciclo de vida de las tareas.
"""

import sys

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtGui import QColor, QCursor
from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QFrame, QLabel, QPushButton, QScrollArea,
    QGraphicsDropShadowEffect, QApplication, QDialog,
)

from datetime import datetime
from config import (
    PANEL_MIN_WIDTH, PANEL_MAX_WIDTH, PANEL_MIN_HEIGHT, PANEL_MAX_HEIGHT,
    BG_BASE, BG_SURFACE, BG_ELEVATED, BORDER, BORDER_LIGHT,
    TEXT_HI, TEXT_MID, TEXT_LO, ACCENT, ACCENT_LT,
    P_ORDER, load_tasks, save_tasks, load_geometry, save_geometry,
    load_history, save_history,
)
from widgets import DragHeader
from task_card import TaskCard
from dialogs import AddDialog, HistoryDialog

# Margen en px para detección de bordes de redimensionamiento
_RESIZE_MARGIN = 8


def _detect_edges(pos, width, height):
    """Detecta en qué borde(s) está el cursor. Devuelve Qt.Edges."""
    edges = Qt.Edge(0)
    if pos.x() <= _RESIZE_MARGIN:
        edges |= Qt.Edge.LeftEdge
    if pos.x() >= width - _RESIZE_MARGIN:
        edges |= Qt.Edge.RightEdge
    if pos.y() <= _RESIZE_MARGIN:
        edges |= Qt.Edge.TopEdge
    if pos.y() >= height - _RESIZE_MARGIN:
        edges |= Qt.Edge.BottomEdge
    return edges


def _cursor_for_edges(edges):
    """Devuelve el cursor apropiado según los bordes detectados."""
    if edges == (Qt.Edge.TopEdge | Qt.Edge.LeftEdge):
        return Qt.CursorShape.SizeFDiagCursor
    if edges == (Qt.Edge.BottomEdge | Qt.Edge.RightEdge):
        return Qt.CursorShape.SizeFDiagCursor
    if edges == (Qt.Edge.TopEdge | Qt.Edge.RightEdge):
        return Qt.CursorShape.SizeBDiagCursor
    if edges == (Qt.Edge.BottomEdge | Qt.Edge.LeftEdge):
        return Qt.CursorShape.SizeBDiagCursor
    if edges in (Qt.Edge.LeftEdge, Qt.Edge.RightEdge):
        return Qt.CursorShape.SizeHorCursor
    if edges in (Qt.Edge.TopEdge, Qt.Edge.BottomEdge):
        return Qt.CursorShape.SizeVerCursor
    return None


class TaskFlow(QMainWindow):
    """Ventana principal: panel lateral con lista de tareas."""

    def __init__(self):
        super().__init__()
        self.tasks = load_tasks()
        self._cards: list[TaskCard] = []
        self._initialized = False
        self._is_windows = sys.platform.startswith("win")

        self.setWindowTitle("TaskFlow")

        # Linux/Wayland: experiencia frameless actual.
        # Windows: ventana normal para minimizar/maximizar/snap layout.
        if self._is_windows:
            self.setWindowFlags(Qt.WindowType.Window)
            self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, False)
        else:
            self.setWindowFlags(
                Qt.WindowType.FramelessWindowHint
                | Qt.WindowType.WindowStaysOnTopHint
                | Qt.WindowType.Tool
            )
            self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)

        # Habilitar tracking del ratón para detección de bordes
        self.setMouseTracking(True)

        # Dimensiones flexibles (no fixed)
        self.setMinimumWidth(PANEL_MIN_WIDTH)
        self.setMaximumWidth(PANEL_MAX_WIDTH)
        self.setMinimumHeight(PANEL_MIN_HEIGHT)
        self.setMaximumHeight(PANEL_MAX_HEIGHT)
        self.resize(PANEL_MIN_WIDTH, PANEL_MIN_HEIGHT)

        self._build()
        self._render()
        self._restore_geometry()

    # ─────────────────────────────────────────
    #  Construcción de la interfaz
    # ─────────────────────────────────────────
    def _build(self) -> None:
        root = QWidget()
        self.setCentralWidget(root)
        root_bg = "transparent" if not self._is_windows else BG_BASE
        root.setStyleSheet(f"background: {root_bg};")

        wrap = QVBoxLayout(root)
        wrap_margin = 0 if self._is_windows else 6
        wrap.setContentsMargins(wrap_margin, wrap_margin, wrap_margin, wrap_margin)
        wrap.setSpacing(0)

        # Panel principal
        self.panel = QFrame()
        self.panel.setObjectName("mainPanel")
        panel_radius = 0 if self._is_windows else 16
        self.panel.setStyleSheet(
            f"#mainPanel {{ background: {BG_BASE};"
            f"  border: 1px solid {BORDER}; border-radius: {panel_radius}px; }}"
        )
        if not self._is_windows:
            shadow = QGraphicsDropShadowEffect()
            shadow.setBlurRadius(32)
            shadow.setOffset(0, 6)
            shadow.setColor(QColor(0, 0, 0, 220))
            self.panel.setGraphicsEffect(shadow)

        panel_layout = QVBoxLayout(self.panel)
        panel_layout.setContentsMargins(0, 0, 0, 0)
        panel_layout.setSpacing(0)

        # --- Cabecera arrastrable ---
        self.header = DragHeader()
        self.header.setObjectName("headerWidget")
        header_radius = "0 0 0 0" if self._is_windows else "16px 16px 0 0"
        self.header.setStyleSheet(
            f"#headerWidget {{ background: {BG_SURFACE};"
            f"  border-radius: {header_radius}; }}"
        )

        header_layout = QHBoxLayout(self.header)
        header_layout.setContentsMargins(16, 0, 12, 0)

        icon = QLabel("⏱")
        icon.setStyleSheet(
            "color: #a898ff; font-size: 16px; background: transparent;"
        )
        title = QLabel("TaskFlow")
        title.setStyleSheet(
            f"color: {TEXT_HI}; font-size: 14px; font-weight: 700;"
            "background: transparent;"
        )

        self.lbl_count = QLabel()
        self.lbl_count.setStyleSheet(
            f"color: {TEXT_MID}; font-size: 11px; background: {BG_ELEVATED};"
            f"border: 1px solid {BORDER}; border-radius: 9px; padding: 2px 8px;"
        )

        self.btn_history = QPushButton("📜")
        self.btn_history.setFixedSize(26, 26)
        self.btn_history.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_history.setStyleSheet(
            f"QPushButton {{ background: transparent; color: {TEXT_LO};"
            "  border: none; font-size: 14px; border-radius: 13px; }}"
            f"QPushButton:hover {{ color: {ACCENT}; background: {ACCENT}20; }}"
        )
        self.btn_history.clicked.connect(self._show_history)

        btn_close = None
        if not self._is_windows:
            btn_close = QPushButton("✕")
            btn_close.setFixedSize(26, 26)
            btn_close.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
            btn_close.setStyleSheet(
                f"QPushButton {{ background: transparent; color: {TEXT_LO};"
                "  border: none; font-size: 11px; border-radius: 13px; }}"
                "QPushButton:hover { color: #ff5e78; background: #ff5e7820; }"
            )
            btn_close.clicked.connect(self._close)

        header_layout.addWidget(icon)
        header_layout.addSpacing(6)
        header_layout.addWidget(title)
        header_layout.addStretch()
        header_layout.addWidget(self.lbl_count)
        header_layout.addSpacing(6)
        header_layout.addWidget(self.btn_history)
        if btn_close is not None:
            header_layout.addSpacing(6)
            header_layout.addWidget(btn_close)
        panel_layout.addWidget(self.header)

        # --- Divisor ---
        divider = QFrame()
        divider.setFrameShape(QFrame.Shape.HLine)
        divider.setStyleSheet(f"color: {BORDER};")
        divider.setFixedHeight(1)
        panel_layout.addWidget(divider)

        # --- Área de scroll ---
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setHorizontalScrollBarPolicy(
            Qt.ScrollBarPolicy.ScrollBarAlwaysOff
        )
        self.scroll.setVerticalScrollBarPolicy(
            Qt.ScrollBarPolicy.ScrollBarAsNeeded
        )
        self.scroll.setStyleSheet(
            "QScrollArea { border: none; background: transparent; }"
            f"QScrollBar:vertical {{ background: {BG_BASE}; width: 5px;"
            "  border-radius: 2px; }}"
            f"QScrollBar::handle:vertical {{ background: {BORDER_LIGHT};"
            "  border-radius: 2px; min-height: 24px; }}"
            f"QScrollBar::handle:vertical:hover {{ background: {TEXT_LO}; }}"
            "QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical"
            " { height: 0; }"
        )

        self.container = QWidget()
        self.container.setStyleSheet("background: transparent;")
        self.task_lay = QVBoxLayout(self.container)
        self.task_lay.setContentsMargins(10, 10, 10, 10)
        self.task_lay.setSpacing(8)
        self.task_lay.addStretch()
        self.scroll.setWidget(self.container)
        panel_layout.addWidget(self.scroll, 1)

        # --- Estado vacío ---
        self.empty_widget = QWidget()
        self.empty_widget.setStyleSheet("background: transparent;")
        empty_lay = QVBoxLayout(self.empty_widget)
        empty_lay.setContentsMargins(20, 20, 20, 20)

        empty_icon = QLabel("📋")
        empty_icon.setAlignment(Qt.AlignmentFlag.AlignCenter)
        empty_icon.setStyleSheet("font-size: 32px; background: transparent;")

        empty_text = QLabel("Sin tareas todavía")
        empty_text.setAlignment(Qt.AlignmentFlag.AlignCenter)
        empty_text.setStyleSheet(
            f"color: {TEXT_MID}; font-size: 13px; background: transparent;"
        )

        empty_hint = QLabel("Pulsa + Nueva tarea para empezar")
        empty_hint.setAlignment(Qt.AlignmentFlag.AlignCenter)
        empty_hint.setStyleSheet(
            f"color: {TEXT_LO}; font-size: 11px; background: transparent;"
        )

        empty_lay.addStretch()
        empty_lay.addWidget(empty_icon)
        empty_lay.addSpacing(6)
        empty_lay.addWidget(empty_text)
        empty_lay.addWidget(empty_hint)
        empty_lay.addStretch()
        panel_layout.addWidget(self.empty_widget)

        # --- Footer ---
        footer = QWidget()
        footer.setObjectName("footerWidget")
        footer.setFixedHeight(58)
        footer_radius = "0 0 0 0" if self._is_windows else "0 0 16px 16px"
        footer.setStyleSheet(
            f"#footerWidget {{ background: {BG_SURFACE};"
            f"  border-radius: {footer_radius}; }}"
        )

        footer_lay = QHBoxLayout(footer)
        footer_lay.setContentsMargins(12, 10, 12, 10)

        self.btn_add = QPushButton("＋  Nueva tarea")
        self.btn_add.setFixedHeight(36)
        self.btn_add.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_add.setStyleSheet(
            f"QPushButton {{ background: {ACCENT}; color: #ffffff;"
            "  border: none; border-radius: 10px;"
            "  font-size: 13px; font-weight: 700; }}"
            f"QPushButton:hover {{ background: {ACCENT_LT}; }}"
            f"QPushButton:pressed {{ background: {ACCENT}; }}"
        )
        self.btn_add.clicked.connect(self._add)
        footer_lay.addWidget(self.btn_add)
        panel_layout.addWidget(footer)

        wrap.addWidget(self.panel)

    # ─────────────────────────────────────────
    #  Renderizado de tarjetas
    # ─────────────────────────────────────────
    def _render(self) -> None:
        # Limpiar tarjetas existentes
        for card in self._cards:
            card.stop()
            self.task_lay.removeWidget(card)
            card.deleteLater()
        self._cards.clear()

        count = len(self.tasks)
        self.scroll.setVisible(count > 0)
        self.empty_widget.setVisible(count == 0)

        if count > 0:
            ordered = sorted(
                enumerate(self.tasks),
                key=lambda x: P_ORDER[x[1]["priority"]],
            )
            # Eliminar el stretch existente al final
            self.task_lay.takeAt(self.task_lay.count() - 1)

            for orig_idx, task in ordered:
                card = TaskCard(task, orig_idx)
                card.sig_delete.connect(self._delete)
                card.sig_tick.connect(lambda: save_tasks(self.tasks))
                card.sig_play_requested.connect(self._on_play_requested)
                card.sig_completed.connect(self._on_task_completed)
                self._cards.append(card)
                self.task_lay.addWidget(card)

            self.task_lay.addStretch()

        self.lbl_count.setText(f"{count} tarea{'s' if count != 1 else ''}")
        self.lbl_count.setVisible(count > 0)

    # ─────────────────────────────────────────
    #  Acciones
    # ─────────────────────────────────────────
    def _add(self) -> None:
        dlg = AddDialog(self)
        geo = self.frameGeometry()
        dlg.move(max(0, geo.left() - 305), geo.top() + 60)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.tasks.append(dlg.get())
            save_tasks(self.tasks)
            self._render()

    def _delete(self, idx: int) -> None:
        if 0 <= idx < len(self.tasks):
            for card in self._cards:
                if card.index == idx:
                    card.stop()
                    break
            self.tasks.pop(idx)
            save_tasks(self.tasks)
            self._render()

    def _on_play_requested(self, index: int) -> None:
        """Detiene todas las tareas excepto la que acaba de empezar."""
        for card in self._cards:
            if card.index != index and card.running:
                card.toggle() # toggle() pausará si estaba running y actualizará UI

    def _on_task_completed(self, idx: int) -> None:
        """Registra la tarea en el historial cuando termina."""
        if 0 <= idx < len(self.tasks):
            task = self.tasks[idx].copy()
            task["completed_at"] = datetime.now().strftime("%H:%M  —  %d/%m/%y")
            
            history = load_history()
            history.append(task)
            save_history(history)

    def _show_history(self) -> None:
        """Muestra el diálogo de historial."""
        history = load_history()
        dlg = HistoryDialog(history, self)
        # Posicionar cerca del botón de historial
        geo = self.frameGeometry()
        dlg.move(max(0, geo.left() - 325), geo.top() + 40)
        dlg.exec()

    def _close(self) -> None:
        save_tasks(self.tasks)
        self._save_geometry()
        QApplication.quit()

    # ─────────────────────────────────────────
    #  Redimensionamiento por bordes (Wayland)
    # ─────────────────────────────────────────
    def mouseMoveEvent(self, event):
        """Actualiza el cursor cuando el ratón está cerca de un borde."""
        if self._is_windows:
            return super().mouseMoveEvent(event)
        edges = _detect_edges(event.pos(), self.width(), self.height())
        cursor_shape = _cursor_for_edges(edges)
        if cursor_shape is not None:
            self.setCursor(QCursor(cursor_shape))
        else:
            self.unsetCursor()
        super().mouseMoveEvent(event)

    def resizeEvent(self, event):
        """Guarda la geometría cuando se redimensiona."""
        self._save_geometry()
        super().resizeEvent(event)

    def moveEvent(self, event):
        """Guarda la geometría cuando se mueve."""
        self._save_geometry()
        super().moveEvent(event)

    def mousePressEvent(self, event):
        """Inicia un redimensionamiento del sistema si se pulsa en un borde."""
        if self._is_windows:
            return super().mousePressEvent(event)
        if event.button() == Qt.MouseButton.LeftButton:
            edges = _detect_edges(event.pos(), self.width(), self.height())
            if edges:
                window_handle = self.windowHandle()
                if window_handle is not None:
                    window_handle.startSystemResize(edges)
                    return
        super().mousePressEvent(event)

    # ─────────────────────────────────────────
    #  Persistencia de geometría
    # ─────────────────────────────────────────
    # ─────────────────────────────────────────
    #  Persistencia de geometría
    # ─────────────────────────────────────────
    def _restore_geometry(self) -> None:
        """Carga dimensiones guardadas y centra la ventana."""
        geo = load_geometry()
        if geo is not None:
            self.move(geo["x"], geo["y"])
            self.resize(geo["width"], geo["height"])
        else:
            # Centrar solo la primera vez
            screen = QApplication.primaryScreen().availableGeometry()
            x = (screen.width() - self.width()) // 2
            y = (screen.height() - self.height()) // 2
            self.move(x, y)
        self._initialized = True

    def _save_geometry(self) -> None:
        """Guarda posición y tamaño actuales de la ventana."""
        if not self._initialized:
            return
        pos = self.pos()
        size = self.size()
        save_geometry(pos.x(), pos.y(), size.width(), size.height())

    # ─────────────────────────────────────────
    #  Evento de cierre
    # ─────────────────────────────────────────
    def closeEvent(self, event):
        save_tasks(self.tasks)
        self._save_geometry()
        super().closeEvent(event)
