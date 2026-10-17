-- TaskFlow schema version 1 (the pre-provenance release).
--
-- This is the single source of the legacy schema. It is used by
-- DatabaseManagerTest (upgrade in-process) and by tools/upgrade_check.py
-- (upgrade through the packaged application's real startup path), so the two
-- checks cannot drift apart. Keep it byte-identical to what v1 shipped.

CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    priority TEXT NOT NULL,
    total_seconds INTEGER NOT NULL,
    remaining_seconds INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_tasks_priority ON tasks(priority);

CREATE TABLE history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    priority TEXT NOT NULL,
    total_seconds INTEGER NOT NULL,
    remaining_seconds INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    completed_manually INTEGER NOT NULL,
    event_at TEXT NOT NULL
);

CREATE INDEX idx_history_event_at ON history(event_at DESC);

CREATE TABLE preferences (
    pref_key TEXT PRIMARY KEY,
    pref_value TEXT NOT NULL
);

PRAGMA user_version = 1;
