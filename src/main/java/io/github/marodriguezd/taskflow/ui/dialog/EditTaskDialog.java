package io.github.marodriguezd.taskflow.ui.dialog;

import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import javafx.stage.Stage;

/** Modal dialog for editing an existing Task. */
public class EditTaskDialog extends AddTaskDialog {

    private final Task originalTask;

    public EditTaskDialog(Stage owner, ThemeManager themeManager, Task task) {
        super(owner, themeManager);
        this.originalTask = task;

        titleLabel.setText("Editar tarea");
        confirmButton.setText("Guardar");

        nameField.setText(task.name());
        int totalMinutes = Math.max(1, task.totalSeconds() / 60);
        minutesSpinner.getValueFactory().setValue(totalMinutes);
        priorityCombo.setValue(task.priority());
    }

    public Task getOriginalTask() {
        return originalTask;
    }
}
