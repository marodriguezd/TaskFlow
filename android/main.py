"""TaskFlow Android (Kivy).

Implementación móvil para generar APK con Buildozer.
Reutiliza el modelo básico de tareas (nombre, prioridad, duración, progreso, historial).
"""

from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from datetime import datetime
from pathlib import Path

from kivy.app import App
from kivy.clock import Clock
from kivy.properties import NumericProperty, StringProperty
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.popup import Popup
from kivy.uix.scrollview import ScrollView
from kivy.uix.textinput import TextInput


@dataclass
class Task:
    name: str
    priority: str
    duration: int
    remaining: int
    running: bool = False


class TaskRow(BoxLayout):
    task_name = StringProperty("")
    task_priority = StringProperty("Media")
    task_remaining = StringProperty("00:00")
    progress = NumericProperty(0)

    def __init__(self, task: Task, on_toggle, on_delete, **kwargs):
        super().__init__(orientation="horizontal", size_hint_y=None, height="50dp", spacing=8, **kwargs)
        self.task = task
        self.on_toggle = on_toggle
        self.on_delete = on_delete

        self.name_label = Label(text=f"[{task.priority}] {task.name}", halign="left", valign="middle")
        self.time_label = Label(text=self._fmt(task.remaining), size_hint_x=0.25)

        self.btn_toggle = Button(text="⏸" if task.running else "▶", size_hint_x=0.15)
        self.btn_toggle.bind(on_release=lambda *_: self.on_toggle(self.task))

        self.btn_delete = Button(text="✕", size_hint_x=0.15)
        self.btn_delete.bind(on_release=lambda *_: self.on_delete(self.task))

        self.add_widget(self.name_label)
        self.add_widget(self.time_label)
        self.add_widget(self.btn_toggle)
        self.add_widget(self.btn_delete)

    def refresh(self) -> None:
        self.name_label.text = f"[{self.task.priority}] {self.task.name}"
        self.time_label.text = self._fmt(self.task.remaining)
        self.btn_toggle.text = "⏸" if self.task.running else "▶"

    @staticmethod
    def _fmt(seconds: int) -> str:
        minutes, sec = divmod(max(0, int(seconds)), 60)
        return f"{minutes:02d}:{sec:02d}"


class TaskFlowAndroidRoot(BoxLayout):
    def __init__(self, app: "TaskFlowAndroidApp", **kwargs):
        super().__init__(orientation="vertical", spacing=8, padding=10, **kwargs)
        self.app = app

        header = BoxLayout(orientation="horizontal", size_hint_y=None, height="45dp", spacing=8)
        header.add_widget(Label(text="TaskFlow Android", bold=True))
        add_button = Button(text="+ Nueva tarea", size_hint_x=0.35)
        add_button.bind(on_release=lambda *_: self.open_add_dialog())
        header.add_widget(add_button)
        self.add_widget(header)

        self.scroll = ScrollView()
        self.task_container = BoxLayout(orientation="vertical", size_hint_y=None, spacing=6)
        self.task_container.bind(minimum_height=self.task_container.setter("height"))
        self.scroll.add_widget(self.task_container)
        self.add_widget(self.scroll)

        self.history_label = Label(text="Completadas hoy: 0", size_hint_y=None, height="28dp")
        self.add_widget(self.history_label)

        self.render()

    def open_add_dialog(self) -> None:
        content = BoxLayout(orientation="vertical", spacing=8, padding=8)
        name_input = TextInput(hint_text="Nombre de tarea", multiline=False)
        mins_input = TextInput(hint_text="Duración en minutos", multiline=False, input_filter="int")
        priority_input = TextInput(hint_text="Prioridad: Alta/Media/Baja", multiline=False, text="Media")

        buttons = BoxLayout(size_hint_y=None, height="44dp", spacing=8)
        cancel_btn = Button(text="Cancelar")
        save_btn = Button(text="Guardar")
        buttons.add_widget(cancel_btn)
        buttons.add_widget(save_btn)

        content.add_widget(name_input)
        content.add_widget(mins_input)
        content.add_widget(priority_input)
        content.add_widget(buttons)

        popup = Popup(title="Nueva tarea", content=content, size_hint=(0.9, 0.45))

        def save_task(*_):
            name = name_input.text.strip()
            priority = priority_input.text.strip().title() or "Media"
            if priority not in {"Alta", "Media", "Baja"}:
                priority = "Media"
            minutes = int(mins_input.text or "25")
            if not name:
                name = "Tarea"
            duration = max(1, minutes) * 60
            self.app.add_task(name, priority, duration)
            popup.dismiss()

        save_btn.bind(on_release=save_task)
        cancel_btn.bind(on_release=lambda *_: popup.dismiss())
        popup.open()

    def render(self) -> None:
        self.task_container.clear_widgets()
        for task in self.app.tasks:
            row = TaskRow(task, self.app.toggle_task, self.app.delete_task)
            self.task_container.add_widget(row)
        self.history_label.text = f"Completadas hoy: {self.app.completed_today_count()}"


class TaskFlowAndroidApp(App):
    def build(self):
        self.title = "TaskFlow"
        self.tasks: list[Task] = []
        self.history: list[dict] = []
        self.load_state()
        self.root_view = TaskFlowAndroidRoot(self)
        Clock.schedule_interval(self._tick, 1)
        return self.root_view

    def get_data_dir(self) -> Path:
        user_dir = self.user_data_dir
        return Path(user_dir)

    def state_path(self) -> Path:
        return self.get_data_dir() / "taskflow_android_data.json"

    def history_path(self) -> Path:
        return self.get_data_dir() / "taskflow_android_history.json"

    def load_state(self) -> None:
        self.get_data_dir().mkdir(parents=True, exist_ok=True)

        if self.state_path().exists():
            with self.state_path().open("r", encoding="utf-8") as file:
                raw_tasks = json.load(file)
            self.tasks = [Task(**item) for item in raw_tasks]

        if self.history_path().exists():
            with self.history_path().open("r", encoding="utf-8") as file:
                self.history = json.load(file)

    def save_state(self) -> None:
        self.get_data_dir().mkdir(parents=True, exist_ok=True)
        with self.state_path().open("w", encoding="utf-8") as file:
            json.dump([asdict(task) for task in self.tasks], file, ensure_ascii=False, indent=2)
        with self.history_path().open("w", encoding="utf-8") as file:
            json.dump(self.history, file, ensure_ascii=False, indent=2)

    def add_task(self, name: str, priority: str, duration: int) -> None:
        self.tasks.append(Task(name=name, priority=priority, duration=duration, remaining=duration))
        self.save_state()
        self.root_view.render()

    def delete_task(self, task: Task) -> None:
        self.tasks = [item for item in self.tasks if item is not task]
        self.save_state()
        self.root_view.render()

    def toggle_task(self, task: Task) -> None:
        for item in self.tasks:
            if item is not task:
                item.running = False
        task.running = not task.running
        self.save_state()
        self.root_view.render()

    def completed_today_count(self) -> int:
        today = datetime.now().date().isoformat()
        return sum(1 for item in self.history if item.get("completed_at", "").startswith(today))

    def _tick(self, _dt: float) -> None:
        changed = False
        for task in self.tasks:
            if not task.running:
                continue
            task.remaining = max(0, task.remaining - 1)
            changed = True
            if task.remaining == 0:
                task.running = False
                self.history.append(
                    {
                        "name": task.name,
                        "priority": task.priority,
                        "duration": task.duration,
                        "completed_at": datetime.now().isoformat(timespec="seconds"),
                    }
                )

        if changed:
            self.save_state()
            self.root_view.render()


if __name__ == "__main__":
    TaskFlowAndroidApp().run()
