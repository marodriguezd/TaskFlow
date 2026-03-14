"""
app.py — Ventana principal de TaskFlow.

Responsabilidad: orquestar la UI (cabecera, lista de tareas, footer)
y gestionar el ciclo de vida de las tareas.
"""

import sys

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QColor, QCursor, QPalette
from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QFrame, QLabel, QPushButton, QScrollArea,
    QGraphicsDropShadowEffect, QApplication, QDialog,
)

from datetime import datetime
import config
from widgets import DragHeader
from task_card import TaskCard
from dialogs import AddDialog, EditDialog, HistoryDialog

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
        self.tasks = config.load_tasks()
        self._cards: list[TaskCard] = []
        self._initialized = False
        self._is_windows = sys.platform.startswith("win")
        self._preferences = config.load_preferences()
        self._theme_name = config.apply_theme(self._preferences.get("theme", "dark"))
        pref_pin = self._preferences.get("always_on_top")
        default_pin = not self._is_windows
        self._always_on_top = default_pin if pref_pin is None else bool(pref_pin)

        self.setWindowTitle("TaskFlow")

        # Linux/Wayland: experiencia frameless actual.
        # Windows: ventana normal para minimizar/maximizar/snap layout.
        if self._is_windows:
            self.setWindowFlags(Qt.WindowType.Window)
            self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, False)
        else:
            self.setWindowFlags(
                Qt.WindowType.FramelessWindowHint
                | (Qt.WindowType.WindowStaysOnTopHint if self._always_on_top else Qt.WindowType(0))
                | Qt.WindowType.Tool
            )
            self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)

        # Habilitar tracking del ratón para detección de bordes
        self.setMouseTracking(True)

        # Dimensiones flexibles (no fixed)
        self.setMinimumWidth(config.PANEL_MIN_WIDTH)
        self.setMinimumHeight(config.PANEL_MIN_HEIGHT)
        if self._is_windows:
            # Permite snap vertical completo y anchura libre en Windows.
            self.setMaximumWidth(16777215)
            self.setMaximumHeight(16777215)
        else:
            self.setMaximumWidth(config.PANEL_MAX_WIDTH)
            self.setMaximumHeight(config.PANEL_MAX_HEIGHT)
        self.resize(config.PANEL_MIN_WIDTH, config.PANEL_MIN_HEIGHT)

        self._apply_app_palette()
        self._build()
        self._render()
        self._restore_geometry()

    def _apply_app_palette(self) -> None:
        """Sincroniza la paleta global de Qt con el tema actual."""
        app = QApplication.instance()
        if app is None:
            return
        palette = QPalette()
        palette.setColor(QPalette.ColorRole.Window, QColor(config.BG_BASE))
        palette.setColor(QPalette.ColorRole.WindowText, QColor(config.TEXT_HI))
        palette.setColor(QPalette.ColorRole.Base, QColor(config.BG_SURFACE))
        palette.setColor(QPalette.ColorRole.Text, QColor(config.TEXT_HI))
        palette.setColor(QPalette.ColorRole.ButtonText, QColor(config.TEXT_HI))
        palette.setColor(QPalette.ColorRole.Highlight, QColor(config.ACCENT))
        palette.setColor(QPalette.ColorRole.HighlightedText, QColor("#ffffff"))
        app.setPalette(palette)

    # ─────────────────────────────────────────
    #  Construcción de la interfaz
    # ─────────────────────────────────────────
    def _build(self) -> None:
        # Fallback defensivo por si un merge elimina la asignación en __init__.
        if not hasattr(self, "_always_on_top"):
            self._always_on_top = not getattr(self, "_is_windows", False)

        root = QWidget()
        self.setCentralWidget(root)
        root_bg = "transparent" if not self._is_windows else config.BG_BASE
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
            f"#mainPanel {{ background: {config.BG_BASE};"
            f"  border: 1px solid {config.BORDER}; border-radius: {panel_radius}px; }}"
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
            f"#headerWidget {{ background: {config.BG_SURFACE};"
            f"  border-radius: {header_radius}; }}"
        )

        header_layout = QHBoxLayout(self.header)
        header_layout.setContentsMargins(16, 0, 12, 0)

        icon = QLabel("⏱")
        icon.setStyleSheet(
            f"color: {config.ACCENT_LT}; font-size: 16px; background: transparent;"
        )
        title = QLabel("TaskFlow")
        title.setStyleSheet(
            f"color: {config.TEXT_HI}; font-size: 14px; font-weight: 700;"
            "background: transparent;"
        )

        self.lbl_count = QLabel()
        self.lbl_count.setStyleSheet(
            f"color: {config.TEXT_MID}; font-size: 11px; background: {config.BG_ELEVATED};"
            f"border: 1px solid {config.BORDER}; border-radius: 9px; padding: 2px 8px;"
        )

        self.btn_pin = QPushButton("📌")
        self.btn_pin.setCheckable(True)
        self.btn_pin.setChecked(self._always_on_top)
        self.btn_pin.setFixedSize(26, 26)
        self.btn_pin.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_pin.setToolTip("Alternar siempre en primer plano")
        self.btn_pin.setStyleSheet(
            f"QPushButton {{ background: transparent; color: {config.TEXT_LO};"
            "  border: none; font-size: 14px; border-radius: 13px; }}"
            f"QPushButton:hover {{ color: {config.ACCENT}; background: {config.ACCENT}20; }}"
            f"QPushButton:checked {{ color: {config.ACCENT_LT}; background: {config.ACCENT}33; }}"
        )
        self.btn_pin.toggled.connect(self._toggle_always_on_top)

        self.btn_history = QPushButton("📜")
        self.btn_history.setFixedSize(26, 26)
        self.btn_history.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_history.setStyleSheet(
            f"QPushButton {{ background: transparent; color: {config.TEXT_LO};"
            "  border: none; font-size: 14px; border-radius: 13px; }}"
            f"QPushButton:hover {{ color: {config.ACCENT}; background: {config.ACCENT}20; }}"
        )
        self.btn_history.clicked.connect(self._show_history)

        self.btn_theme = QPushButton("☀" if self._theme_name == "dark" else "🌙")
        self.btn_theme.setFixedSize(26, 26)
        self.btn_theme.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_theme.setToolTip("Cambiar tema claro/oscuro")
        self.btn_theme.setStyleSheet(
            f"QPushButton {{ background: transparent; color: {config.TEXT_LO};"
            "  border: none; font-size: 14px; border-radius: 13px; }}"
            f"QPushButton:hover {{ color: {config.ACCENT}; background: {config.ACCENT}20; }}"
        )
        self.btn_theme.clicked.connect(self._toggle_theme)

        btn_close = None
        if not self._is_windows:
            btn_close = QPushButton("✕")
            btn_close.setFixedSize(26, 26)
            btn_close.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
            btn_close.setStyleSheet(
                f"QPushButton {{ background: transparent; color: {config.TEXT_LO};"
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
        header_layout.addWidget(self.btn_pin)
        header_layout.addSpacing(4)
        header_layout.addWidget(self.btn_history)
        header_layout.addSpacing(4)
        header_layout.addWidget(self.btn_theme)
        if btn_close is not None:
            header_layout.addSpacing(6)
            header_layout.addWidget(btn_close)
        panel_layout.addWidget(self.header)

        # --- Divisor ---
        divider = QFrame()
        divider.setFrameShape(QFrame.Shape.HLine)
        divider.setStyleSheet(f"color: {config.BORDER};")
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
            f"QScrollBar:vertical {{ background: {config.BG_BASE}; width: 5px;"
            "  border-radius: 2px; }}"
            f"QScrollBar::handle:vertical {{ background: {config.BORDER_LIGHT};"
            "  border-radius: 2px; min-height: 24px; }}"
            f"QScrollBar::handle:vertical:hover {{ background: {config.TEXT_LO}; }}"
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
            f"color: {config.TEXT_MID}; font-size: 13px; background: transparent;"
        )

        empty_hint = QLabel("Pulsa + Nueva tarea para empezar")
        empty_hint.setAlignment(Qt.AlignmentFlag.AlignCenter)
        empty_hint.setStyleSheet(
            f"color: {config.TEXT_LO}; font-size: 11px; background: transparent;"
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
            f"#footerWidget {{ background: {config.BG_SURFACE};"
            f"  border-radius: {footer_radius}; }}"
        )

        footer_lay = QHBoxLayout(footer)
        footer_lay.setContentsMargins(12, 10, 12, 10)

        self.btn_add = QPushButton("＋  Nueva tarea")
        self.btn_add.setFixedHeight(36)
        self.btn_add.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.btn_add.setStyleSheet(
            f"QPushButton {{ background: {config.ACCENT}; color: #ffffff;"
            "  border: none; border-radius: 10px;"
            "  font-size: 13px; font-weight: 700; }}"
            f"QPushButton:hover {{ background: {config.ACCENT_LT}; }}"
            f"QPushButton:pressed {{ background: {config.ACCENT}; }}"
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
                key=lambda x: config.P_ORDER[x[1]["priority"]],
            )
            # Eliminar el stretch existente al final
            self.task_lay.takeAt(self.task_lay.count() - 1)

            for orig_idx, task in ordered:
                card = TaskCard(task, orig_idx)
                card.sig_delete.connect(self._delete)
                card.sig_edit.connect(self._edit)
                card.sig_tick.connect(lambda: config.save_tasks(self.tasks))
                card.sig_play_requested.connect(self._on_play_requested)
                card.sig_completed.connect(self._on_task_completed)
                card.sig_completed_manual.connect(self._on_task_completed_manual)
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
            config.save_tasks(self.tasks)
            self._render()

    def _edit(self, idx: int) -> None:
        if not (0 <= idx < len(self.tasks)):
            return

        for card in self._cards:
            if card.index == idx:
                card.stop()
                break

        dlg = EditDialog(self.tasks[idx], self)
        geo = self.frameGeometry()
        dlg.move(max(0, geo.left() - 305), geo.top() + 60)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.tasks[idx] = dlg.get()
            config.save_tasks(self.tasks)
            self._render()

    def _delete(self, idx: int, track_history: bool = True) -> None:
        if 0 <= idx < len(self.tasks):
            for card in self._cards:
                if card.index == idx:
                    card.stop()
                    break

            # Si ya estaba agotada, asumimos que ya fue registrada como completada.
            should_track_delete = track_history and int(self.tasks[idx].get("remaining", 1)) > 0
            if should_track_delete:
                self._store_task_in_history(idx, event_type="deleted")

            self.tasks.pop(idx)
            config.save_tasks(self.tasks)
            self._render()

    def _on_play_requested(self, index: int) -> None:
        """Detiene todas las tareas excepto la que acaba de empezar."""
        for card in self._cards:
            if card.index != index and card.running:
                card.toggle() # toggle() pausará si estaba running y actualizará UI

    def _store_task_in_history(self, idx: int, event_type: str, manual: bool = False) -> None:
        """Guarda una copia de la tarea en historial con metadata de evento."""
        if not (0 <= idx < len(self.tasks)):
            return

        task = self.tasks[idx].copy()
        task["history_event"] = event_type
        task["event_at"] = datetime.now().strftime("%H:%M  —  %d/%m/%y")
        task["completed_at"] = task["event_at"]  # Compatibilidad con historial previo
        task["completed_manually"] = manual
        task["remaining"] = max(0, int(task.get("remaining", task.get("total_seconds", 0))))

        history = config.load_history()
        history.append(task)
        config.save_history(history)

    def _on_task_completed(self, idx: int) -> None:
        """Registra la tarea en el historial cuando termina por temporizador."""
        self._store_task_in_history(idx, event_type="completed", manual=False)

    def _on_task_completed_manual(self, idx: int) -> None:
        """Registra y elimina una tarea completada manualmente."""
        self._store_task_in_history(idx, event_type="completed", manual=True)
        self._delete(idx, track_history=False)

    def _show_history(self) -> None:
        """Muestra el diálogo de historial."""
        history = config.load_history()
        dlg = HistoryDialog(history, self)
        dlg.sig_restore_task.connect(self._restore_task_from_history)
        # Posicionar cerca del botón de historial
        geo = self.frameGeometry()
        dlg.move(max(0, geo.left() - 325), geo.top() + 40)
        dlg.exec()

    def _restore_task_from_history(self, history_idx: int) -> None:
        """Restaura una tarea del historial al listado principal."""
        history = config.load_history()
        if not (0 <= history_idx < len(history)):
            return

        task = history.pop(history_idx)
        task.pop("completed_at", None)
        task.pop("completed_manually", None)
        task.pop("history_event", None)
        task.pop("event_at", None)

        total_seconds = max(60, int(task.get("total_seconds", 1500)))
        remaining = int(task.get("remaining", total_seconds))
        task["total_seconds"] = total_seconds
        task["remaining"] = min(total_seconds, max(0, remaining))

        self.tasks.append(task)
        config.save_tasks(self.tasks)
        config.save_history(history)
        self._render()

    def _save_preferences(self) -> None:
        """Guarda preferencias de usuario (tema y chincheta)."""
        self._preferences["theme"] = self._theme_name
        self._preferences["always_on_top"] = self._always_on_top
        config.save_preferences(self._preferences)

    def _toggle_theme(self) -> None:
        """Alterna entre tema oscuro y claro y reconstruye estilos."""
        self._theme_name = "light" if self._theme_name == "dark" else "dark"
        config.apply_theme(self._theme_name)
        self._apply_app_palette()
        self._save_preferences()
        self._build()
        self._render()

    def _toggle_always_on_top(self, enabled: bool) -> None:
        """Activa o desactiva mantener la ventana en primer plano."""
        self._always_on_top = enabled
        self.setWindowFlag(Qt.WindowType.WindowStaysOnTopHint, enabled)
        self.show()
        if enabled:
            self.raise_()
        self._save_preferences()

    def _close(self) -> None:
        config.save_tasks(self.tasks)
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
    def _restore_geometry(self) -> None:
        """Carga dimensiones guardadas y centra la ventana."""
        geo = config.load_geometry()
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
        config.save_geometry(pos.x(), pos.y(), size.width(), size.height())

    # ─────────────────────────────────────────
    #  Evento de cierre
    # ─────────────────────────────────────────
    def closeEvent(self, event):
        config.save_tasks(self.tasks)
        self._save_geometry()
        super().closeEvent(event)
