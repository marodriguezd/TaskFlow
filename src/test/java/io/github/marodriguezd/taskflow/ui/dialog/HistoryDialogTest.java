package io.github.marodriguezd.taskflow.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.ui.i18n.Messages;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Headless coverage for history-mode presentation. This behavior previously lived in {@code
 * HistoryItem.getModeDescription()} (English copy inside the domain) and was deliberately moved to
 * the UI layer for i18n; the mapping and its English rendering are asserted here so no behavioral
 * coverage is lost. No JavaFX toolkit is required: only a static key-mapping method is exercised.
 */
class HistoryDialogTest {

    @BeforeEach
    void setUp() {
        Messages.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Locale.ENGLISH);
    }

    private static HistoryItem item(HistoryEventType type, boolean completedManually) {
        return new HistoryItem(
                1L, "T1", Priority.MEDIUM, 600, 300, type, completedManually, Instant.now());
    }

    @Test
    @DisplayName("Timer completion maps to 'Completed by timer' (English rendering preserved)")
    void testTimerCompletionMode() {
        HistoryItem timer = item(HistoryEventType.COMPLETED, false);
        String key = HistoryDialog.modeKey(timer);
        assertThat(key).isEqualTo("history.mode.timer");
        assertThat(Messages.get(key)).isEqualTo("Completed by timer");
    }

    @Test
    @DisplayName("Manual completion maps to 'Completed manually' (English rendering preserved)")
    void testManualCompletionMode() {
        HistoryItem manual = item(HistoryEventType.COMPLETED, true);
        String key = HistoryDialog.modeKey(manual);
        assertThat(key).isEqualTo("history.mode.manual");
        assertThat(Messages.get(key)).isEqualTo("Completed manually");
    }

    @Test
    @DisplayName("Deletion maps to 'Deleted' regardless of completedManually")
    void testDeletedMode() {
        HistoryItem deleted = item(HistoryEventType.DELETED, false);
        String key = HistoryDialog.modeKey(deleted);
        assertThat(key).isEqualTo("history.mode.deleted");
        assertThat(Messages.get(key)).isEqualTo("Deleted");
    }
}
