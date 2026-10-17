#!/usr/bin/env python3
"""Disposable per-platform profile shared by the packaged-app checkers.

Both ``tools/upgrade_check.py`` and ``tools/data_preservation_check.py`` must
launch the packaged application with the same environment the real product would
see, but pointed at a throw-away home/data directory. Keeping the resolution in
one place means the expected data directory cannot drift from ``PlatformService``
without every check failing loudly.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path


class ProfileError(RuntimeError):
    pass


def data_directory(env: dict[str, str]) -> Path:
    """The directory ``PlatformService`` resolves for this isolated environment."""
    if sys.platform == "darwin":
        return Path(env["HOME"]) / "Library" / "Application Support" / "TaskFlow"
    if sys.platform.startswith("win"):
        return Path(env["APPDATA"]) / "TaskFlow"
    return Path(env["XDG_DATA_HOME"]) / "TaskFlow"


def profile_environment(root: Path) -> tuple[dict[str, str], Path]:
    """Return ``(environment, data directory)`` for a disposable profile."""
    home = root / "home"
    home.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env.update(
        {
            "HOME": str(home),
            "USERPROFILE": str(home),
            "JAVA_TOOL_OPTIONS": f"-Duser.home={home}",
        }
    )
    if sys.platform.startswith("win"):
        env["APPDATA"] = str(root / "appdata")
    elif sys.platform.startswith("linux"):
        env["XDG_DATA_HOME"] = str(root / "xdg")
    if (home / ".TaskFlow").exists():
        raise ProfileError("the disposable home unexpectedly contains a legacy ~/.TaskFlow")
    data = data_directory(env)
    data.mkdir(parents=True, exist_ok=True)
    return env, data
