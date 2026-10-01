package io.github.marodriguezd.taskflow.domain;

/** Event type for items logged in the task history. */
public enum HistoryEventType {
    COMPLETED("completed", "Completada"),
    DELETED("deleted", "Eliminada");

    private final String code;
    private final String label;

    HistoryEventType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static HistoryEventType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return COMPLETED;
        }
        String trimmed = code.trim();
        for (HistoryEventType type : values()) {
            if (type.code.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        return COMPLETED;
    }
}
