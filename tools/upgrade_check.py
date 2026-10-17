#!/usr/bin/env python3
"""Verify the schema v1 -> v2 upgrade through the packaged application's startup.

The checker builds a genuine version-1 database from the shared
``src/test/resources/schema-v1.sql`` fixture (the same file ``DatabaseManagerTest``
uses), populates it with tasks, history and preferences, places it in a disposable
per-platform data directory, and launches the *packaged* application with its
``--smoke-test`` seam. It then reopens the database and asserts that:

* startup completed normally (marker written, exit code 0, no error lines);
* ``PRAGMA user_version`` is 2 and the provenance structures exist;
* every v1 task/history row and every non-geometry preference is byte-identical;
* pre-existing rows keep NULL provenance (origins are never invented);
* window geometry is present and still valid (the app may legitimately re-save it).

Only disposable directories are used, so no real user database is ever opened.

Usage:
    python3 tools/upgrade_check.py [--image build/dist/TaskFlow]
                                   [--timeout 120] [--keep]
"""

from __future__ import annotations

import argparse
import shutil
import sqlite3
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from app_profile import ProfileError, profile_environment  # noqa: E402
from app_smoke_test import SmokeFailure, platform_layout, run_packaged_smoke  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
SCHEMA_V1 = REPO_ROOT / "src" / "test" / "resources" / "schema-v1.sql"

V1_TASKS = [
    ("Kept task", "MEDIUM", 600, 300, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"),
    ("Second kept task", "HIGH", 900, 900, "2026-01-03T08:30:00Z", "2026-01-03T08:30:00Z"),
]
V1_HISTORY = [
    ("Kept history", "HIGH", 900, 0, "completed", 1, "2026-01-02T10:00:00Z"),
    ("Kept deletion", "LOW", 300, 12, "deleted", 0, "2026-01-04T07:15:00Z"),
]
V1_PREFERENCES = {
    "theme": "light",
    "always_on_top": "true",
    "language": "fr",
    "sound_enabled": "false",
    "glass_opacity": "0.65",
    "geo_x": "11",
    "geo_y": "22",
    "geo_width": "333",
    "geo_height": "555",
}
EXACT_PREFERENCES = ("theme", "always_on_top", "language", "sound_enabled", "glass_opacity")
GEOMETRY_PREFERENCES = ("geo_x", "geo_y", "geo_width", "geo_height")


class UpgradeFailure(RuntimeError):
    pass


def seed_version_one_database(database: Path) -> None:
    script = SCHEMA_V1.read_text(encoding="utf-8")
    statement_text = "\n".join(
        line for line in script.splitlines() if not line.strip().startswith("--")
    )
    connection = sqlite3.connect(database)
    try:
        for statement in statement_text.split(";"):
            if statement.strip():
                connection.execute(statement)
        connection.executemany(
            "INSERT INTO tasks(name, priority, total_seconds, remaining_seconds, "
            "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            V1_TASKS,
        )
        connection.executemany(
            "INSERT INTO history(name, priority, total_seconds, remaining_seconds, "
            "event_type, completed_manually, event_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            V1_HISTORY,
        )
        connection.executemany(
            "INSERT INTO preferences(pref_key, pref_value) VALUES (?, ?)",
            sorted(V1_PREFERENCES.items()),
        )
        connection.commit()
        version = connection.execute("PRAGMA user_version").fetchone()[0]
    finally:
        connection.close()
    if version != 1:
        raise UpgradeFailure(f"the fixture is not a v1 database (user_version={version})")


def launch_packaged(image: Path, env: dict[str, str], marker: Path, timeout: int) -> str:
    _, _, launcher, _ = platform_layout(image)
    if not launcher.is_file():
        raise UpgradeFailure(f"packaged launcher is missing: {launcher}")
    try:
        return run_packaged_smoke(launcher, env, marker, timeout)
    except SmokeFailure as error:
        raise UpgradeFailure(str(error)) from error


def verify_upgrade(database: Path, output: str) -> list[str]:
    failures: list[str] = []
    connection = sqlite3.connect(database)
    try:
        version = connection.execute("PRAGMA user_version").fetchone()[0]
        if version != 2:
            failures.append(f"schema user_version is {version}, expected 2")

        tables = {
            row[0]
            for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")
        }
        if "legacy_sources" not in tables:
            failures.append("legacy_sources table is missing")

        indexes = {
            row[0]
            for row in connection.execute("SELECT name FROM sqlite_master WHERE type='index'")
        }
        for index in (
            "idx_tasks_priority",
            "idx_history_event_at",
            "idx_tasks_legacy_signature",
            "idx_history_legacy_signature",
        ):
            if index not in indexes:
                failures.append(f"index {index} is missing")

        for table, columns in (
            ("tasks", ("legacy_source_id", "legacy_signature")),
            ("history", ("legacy_source_id", "legacy_signature", "legacy_source_timestamp")),
        ):
            present = {
                row[1] for row in connection.execute(f"PRAGMA table_info({table})")
            }
            for column in columns:
                if column not in present:
                    failures.append(f"{table}.{column} is missing")

        tasks = list(
            connection.execute(
                "SELECT name, priority, total_seconds, remaining_seconds, created_at, "
                "updated_at FROM tasks ORDER BY id"
            )
        )
        if tasks != V1_TASKS:
            failures.append(f"v1 tasks changed: {tasks}")
        history = list(
            connection.execute(
                "SELECT name, priority, total_seconds, remaining_seconds, event_type, "
                "completed_manually, event_at FROM history ORDER BY id"
            )
        )
        if history != V1_HISTORY:
            failures.append(f"v1 history changed: {history}")

        preferences = dict(connection.execute("SELECT pref_key, pref_value FROM preferences"))
        for key in EXACT_PREFERENCES:
            if preferences.get(key) != V1_PREFERENCES[key]:
                failures.append(
                    f"preference {key} is {preferences.get(key)!r}, "
                    f"expected {V1_PREFERENCES[key]!r}"
                )
        for key in GEOMETRY_PREFERENCES:
            value = preferences.get(key)
            try:
                float(value)
            except (TypeError, ValueError):
                failures.append(f"geometry preference {key} is not numeric: {value!r}")
        if "geo_width" in preferences and float(preferences["geo_width"]) < 300:
            failures.append(f"saved window width is invalid: {preferences['geo_width']}")
        if "geo_height" in preferences and float(preferences["geo_height"]) < 420:
            failures.append(f"saved window height is invalid: {preferences['geo_height']}")

        # Origin is unknown for rows written before provenance existed: never invent it.
        for table in ("tasks", "history"):
            labelled = connection.execute(
                f"SELECT COUNT(*) FROM {table} WHERE legacy_source_id IS NOT NULL "
                "OR legacy_signature IS NOT NULL"
            ).fetchone()[0]
            if labelled:
                failures.append(f"{table} rows were falsely labelled as legacy-imported")
        sources = connection.execute("SELECT COUNT(*) FROM legacy_sources").fetchone()[0]
        if sources:
            failures.append(f"unexpected legacy_sources rows: {sources}")
    finally:
        connection.close()

    for line in output.splitlines():
        if " ERROR " in line or line.startswith("ERROR") or "Exception in thread" in line:
            failures.append(f"error in application output: {line.strip()}")
    return failures


def tail(text: str | None, limit: int = 8_000) -> str:
    return (text or "<no output>")[-limit:]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, default=REPO_ROOT / "build" / "dist" / "TaskFlow")
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--keep", action="store_true", help="keep the disposable profile")
    args = parser.parse_args()

    if not SCHEMA_V1.is_file():
        print(f"schema fixture is missing: {SCHEMA_V1}", file=sys.stderr)
        return 2
    if not args.image.exists():
        print(f"packaged application image does not exist: {args.image}", file=sys.stderr)
        return 2

    root = Path(tempfile.mkdtemp(prefix="taskflow-upgrade-"))
    try:
        env, data_dir = profile_environment(root)
        database = data_dir / "taskflow.db"
        seed_version_one_database(database)
        print(f"seeded a v1 database at {database}")

        marker = root / "startup.marker"
        output = launch_packaged(args.image.resolve(), env, marker, args.timeout)
        print("packaged application started, upgraded and exited cleanly")

        failures = verify_upgrade(database, output)
        if failures:
            print("UPGRADE CHECK FAILED:", file=sys.stderr)
            for failure in failures:
                print(f"  - {failure}", file=sys.stderr)
            print("application output:\n" + tail(output), file=sys.stderr)
            return 1

        print("PASS  v1 -> v2 upgrade through the packaged startup path")
        print("PASS  tasks, history and preferences preserved")
        print("PASS  provenance structures added; pre-v2 rows keep NULL provenance")
        return 0
    except (ProfileError, UpgradeFailure) as error:
        print(f"UPGRADE CHECK FAILED: {error}", file=sys.stderr)
        return 1
    finally:
        if args.keep:
            print(f"kept the disposable profile at {root}")
        else:
            shutil.rmtree(root, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
