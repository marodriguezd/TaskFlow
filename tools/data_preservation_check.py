#!/usr/bin/env python3
"""Prove that package installation/removal never deletes user data.

The check runs in two phases so the packaging step (install/uninstall, mount/
copy/remove, AppImage) happens in between:

``--phase prepare``
    Launches the packaged application with ``--smoke-test`` using a disposable
    home/data directory, asserts the real data directory contains a v2 database
    and the provisioned ``bell.mp3``, inserts a sentinel task, then records a
    state file with SHA-256 hashes of the database and sound.

``--phase verify``
    Re-reads the state file after the application artifact was removed and
    asserts the data directory is untouched: identical hashes, intact SQLite
    integrity, the sentinel task and the persisted preferences still present.

Callers (CI) must point ``--workdir`` at a disposable directory and remove only
their own copy of the package between phases. No real user profile or data
directory is ever used.

    python3 tools/data_preservation_check.py prepare \\
        --image <packaged app image> --workdir <temp dir>
    ... uninstall / delete the package artifact ...
    python3 tools/data_preservation_check.py verify --workdir <temp dir>
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from app_profile import ProfileError, profile_environment  # noqa: E402
from app_smoke_test import SmokeFailure, platform_layout, run_packaged_smoke  # noqa: E402

SENTINEL_TASK = "Preservation sentinel"
STATE_FILE = "state.json"


class PreservationFailure(RuntimeError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def prepare(image: Path, workdir: Path, timeout: int) -> None:
    env, data_directory = profile_environment(workdir)
    _, _, launcher, _ = platform_layout(image)
    if not launcher.is_file():
        raise PreservationFailure(f"packaged launcher is missing: {launcher}")

    marker = workdir / "startup.marker"
    run_packaged_smoke(launcher, env, marker, timeout)

    database = data_directory / "taskflow.db"
    bell = data_directory / "bell.mp3"
    for path in (database, bell):
        if not path.is_file():
            raise PreservationFailure(f"the application did not create {path}")

    connection = sqlite3.connect(database)
    try:
        version = connection.execute("PRAGMA user_version").fetchone()[0]
        if version != 2:
            raise PreservationFailure(f"unexpected schema version {version}")
        connection.execute(
            "INSERT INTO tasks(name, priority, total_seconds, remaining_seconds, "
            "created_at, updated_at) VALUES (?, 'MEDIUM', 600, 600, ?, ?)",
            (SENTINEL_TASK, "2026-01-05T12:00:00Z", "2026-01-05T12:00:00Z"),
        )
        connection.commit()
        connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    finally:
        connection.close()

    state = {
        "data_directory": str(data_directory),
        "database": str(database),
        "bell": str(bell),
        "database_sha256": sha256(database),
        "bell_sha256": sha256(bell),
        "sentinel_task": SENTINEL_TASK,
        "launcher": str(launcher),
    }
    (workdir / STATE_FILE).write_text(json.dumps(state, indent=2), encoding="utf-8")
    print(f"data directory: {data_directory}")
    print("PASS  installed application created its database and bell.mp3")
    print("PASS  recorded hashes and a sentinel task for post-removal verification")


def verify(workdir: Path) -> None:
    state_path = workdir / STATE_FILE
    if not state_path.is_file():
        raise PreservationFailure(f"missing preparation state: {state_path}")
    state = json.loads(state_path.read_text(encoding="utf-8"))
    database = Path(state["database"])
    bell = Path(state["bell"])

    failures = []
    for path, label in ((database, "database"), (bell, "bell.mp3")):
        if not path.is_file():
            failures.append(f"{label} disappeared after removing the application: {path}")
    if failures:
        raise PreservationFailure("; ".join(failures))

    if sha256(bell) != state["bell_sha256"]:
        failures.append("bell.mp3 changed after removing the application")
    if sha256(database) != state["database_sha256"]:
        failures.append("the database file changed after removing the application")

    connection = sqlite3.connect(database)
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            failures.append(f"database integrity check failed: {integrity}")
        sentinel = connection.execute(
            "SELECT COUNT(*) FROM tasks WHERE name = ?", (state["sentinel_task"],)
        ).fetchone()[0]
        if sentinel != 1:
            failures.append(f"the sentinel task is missing ({sentinel} rows)")
        preferences = connection.execute("SELECT COUNT(*) FROM preferences").fetchone()[0]
        if preferences == 0:
            failures.append("preferences disappeared after removing the application")
    except sqlite3.DatabaseError as error:
        failures.append(f"the database could not be reopened: {error}")
    finally:
        connection.close()

    if failures:
        raise PreservationFailure("; ".join(failures))
    print(f"data directory: {state['data_directory']}")
    print("PASS  database and bell.mp3 survived the application removal unchanged")
    print("PASS  sentinel task, preferences and SQLite integrity are intact")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("prepare", "verify"))
    parser.add_argument("--workdir", type=Path, required=True, help="disposable profile dir")
    parser.add_argument("--image", type=Path, help="packaged application image (prepare only)")
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()

    args.workdir.mkdir(parents=True, exist_ok=True)
    try:
        if args.phase == "prepare":
            if args.image is None:
                raise PreservationFailure("prepare requires --image")
            if not args.image.exists():
                raise PreservationFailure(f"image does not exist: {args.image}")
            prepare(args.image.resolve(), args.workdir, args.timeout)
        else:
            verify(args.workdir)
        return 0
    except (PreservationFailure, ProfileError, SmokeFailure) as error:
        print(f"DATA PRESERVATION FAILED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
