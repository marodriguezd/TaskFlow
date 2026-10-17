#!/usr/bin/env python3
"""Verify a packaged TaskFlow application image and run a launch/exit smoke test.

Two independent checks:

* ``--mode verify`` inspects the jpackage layout and the packaged JARs for the main
  class, i18n bundles, themes, audio/image assets and JavaFX components.
* ``--mode launch`` starts the packaged application with the explicit
  ``--smoke-test=<marker>`` argument. The application writes ``started`` only
  after the primary stage is showing and then performs a normal JavaFX shutdown.
  A missing marker, a non-zero exit or a timeout is a failure; "still running
  after N seconds" is never treated as success.

A CI runner has no physical audio device, so this harness proves resource
presence and startup wiring, not audible output.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

REQUIRED_JAR_ENTRIES = [
    # English is the base bundle; the other five languages are locale bundles.
    "i18n/messages.properties",
    "i18n/messages_es.properties",
    "i18n/messages_de.properties",
    "i18n/messages_fr.properties",
    "i18n/messages_it.properties",
    "i18n/messages_zh_Hans.properties",
    "css/base.css",
    "css/light.css",
    "css/dark.css",
    "assets/bell.mp3",
    "assets/TaskFlow.png",
    "io/github/marodriguezd/taskflow/TaskFlowLauncher.class",
    "io/github/marodriguezd/taskflow/TaskFlowApp.class",
]


class SmokeFailure(RuntimeError):
    pass


def platform_layout(image: Path) -> tuple[Path, Path, Path, Path]:
    """Return (app_dir, runtime_dir, launcher, config_file) for a jpackage image."""
    if sys.platform == "darwin":
        app_dir = image / "Contents" / "app"
        runtime_dir = image / "Contents" / "runtime" / "Contents" / "Home"
        launcher = image / "Contents" / "MacOS" / "TaskFlow"
        config = app_dir / "TaskFlow.cfg"
    elif sys.platform.startswith("win"):
        app_dir = image / "app"
        runtime_dir = image / "runtime"
        launcher = image / "TaskFlow.exe"
        config = app_dir / "TaskFlow.cfg"
    else:
        app_dir = image / "lib" / "app"
        runtime_dir = image / "lib" / "runtime"
        launcher = image / "bin" / "TaskFlow"
        config = app_dir / "TaskFlow.cfg"
    return app_dir, runtime_dir, launcher, config


def verify_image(image: Path) -> None:
    if not image.exists():
        raise SmokeFailure(f"application image does not exist: {image}")
    if not image.is_dir():
        raise SmokeFailure(f"application image is not a directory: {image}")

    app_dir, runtime_dir, launcher, config = platform_layout(image)

    if not launcher.is_file():
        raise SmokeFailure(f"missing packaged launcher: {launcher}")
    if not config.is_file():
        raise SmokeFailure(f"missing jpackage configuration: {config}")

    # jpackage strips the developer tools from the bundled runtime, so it has no
    # bin/java. The image is complete when the module image and the JVM library
    # that the native launcher loads are both present.
    if not list(runtime_dir.rglob("modules")):
        raise SmokeFailure(f"bundled Java runtime image is missing modules: {runtime_dir}")
    if not list(runtime_dir.rglob("libjvm.*")) and not list(runtime_dir.rglob("jvm.dll")):
        raise SmokeFailure(f"bundled Java runtime is missing the JVM library: {runtime_dir}")

    jars = sorted(app_dir.glob("*.jar"))
    if not jars:
        raise SmokeFailure(f"no packaged JARs found in {app_dir}")
    main_jars = [jar for jar in jars if jar.name.startswith("taskflow-")]
    if len(main_jars) != 1:
        raise SmokeFailure(f"expected exactly one taskflow JAR, found {len(main_jars)}")

    config_text = config.read_text(encoding="utf-8", errors="replace")
    if "io.github.marodriguezd.taskflow.TaskFlowLauncher" not in config_text:
        raise SmokeFailure("jpackage configuration does not declare TaskFlowLauncher")

    javafx_jars = {jar.name.split("-")[1] for jar in jars if jar.name.startswith("javafx-")}
    for module in ("base", "controls", "graphics", "media"):
        if module not in javafx_jars:
            raise SmokeFailure(f"packaged JavaFX module is missing: javafx.{module}")

    with zipfile.ZipFile(main_jars[0]) as archive:
        names = set(archive.namelist())
        missing = [entry for entry in REQUIRED_JAR_ENTRIES if entry not in names]
        if missing:
            raise SmokeFailure(
                "packaged application JAR is missing resources: " + ", ".join(missing)
            )
        bell = archive.getinfo("assets/bell.mp3")
        if bell.file_size <= 1_000:
            raise SmokeFailure("packaged bell.mp3 is empty or truncated")

    print(f"Resource verification passed: {image}")
    print(f"  launcher: {launcher}")
    print(f"  runtime:  {runtime_dir}")
    print(f"  jars:     {len(jars)} ({main_jars[0].name})")


def launch_image(image: Path, timeout: int) -> None:
    _, _, launcher, _ = platform_layout(image)
    launch_and_wait(launcher, timeout)


def launch_and_wait(command: Path, timeout: int, extra_env: dict[str, str] | None = None) -> None:
    with tempfile.TemporaryDirectory(prefix="taskflow-smoke-") as temp:
        temp_dir = Path(temp)
        marker = temp_dir / "startup.marker"
        home = temp_dir / "home"
        home.mkdir()

        env = os.environ.copy()
        env.update(
            {
                # Keep the smoke test away from any real user data directory.
                "HOME": str(home),
                "USERPROFILE": str(home),
                "XDG_DATA_HOME": str(temp_dir / "xdg-data"),
                "APPDATA": str(temp_dir / "appdata"),
                "JAVA_TOOL_OPTIONS": f"-Duser.home={home}",
            }
        )
        if extra_env:
            env.update(extra_env)

        process = subprocess.Popen(
            [str(command), f"--smoke-test={marker}"],
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            errors="replace",
        )
        try:
            output, _ = process.communicate(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            output, _ = process.communicate()
            raise SmokeFailure(
                f"packaged application did not exit within {timeout}s; last output:\n"
                + tail(output)
            )

        if process.returncode != 0:
            raise SmokeFailure(
                f"packaged application exited with code {process.returncode}; output:\n"
                + tail(output)
            )
        if not marker.is_file():
            raise SmokeFailure(
                "packaged application exited without writing the startup marker; output:\n"
                + tail(output)
            )
        content = marker.read_text(encoding="utf-8").strip()
        if content != "started":
            raise SmokeFailure(f"unexpected startup marker content: {content!r}")
        print("Launch/exit smoke test passed")
        print(f"  marker: {content}")
        print(tail(output))


def extract_appimage(appimage: Path, workdir: Path) -> Path:
    env = os.environ.copy()
    env["APPIMAGE_EXTRACT_AND_RUN"] = "1"
    result = subprocess.run(
        [str(appimage), "--appimage-extract"],
        cwd=workdir,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
        timeout=180,
    )
    if result.returncode != 0:
        raise SmokeFailure("could not extract the AppImage:\n" + tail(result.stdout))
    return workdir / "squashfs-root" / "usr" / "bin"


def tail(text: str | None, limit: int = 8_000) -> str:
    if not text:
        return "<no output>"
    return text[-limit:]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--image", type=Path, help="jpackage app image (dir or .app)")
    source.add_argument("--appimage", type=Path, help="Linux .AppImage file")
    parser.add_argument(
        "--mode",
        choices=("verify", "launch", "all"),
        default="all",
        help="which checks to run (default: all)",
    )
    parser.add_argument("--timeout", type=int, default=120, help="launch timeout in seconds")
    args = parser.parse_args()

    try:
        if args.appimage:
            appimage = args.appimage.resolve()
            if not appimage.is_file():
                raise SmokeFailure(f"AppImage does not exist: {args.appimage}")
            with tempfile.TemporaryDirectory(prefix="taskflow-appimage-") as workdir:
                image = extract_appimage(appimage, Path(workdir))
                if args.mode in ("verify", "all"):
                    verify_image(image)
                if args.mode in ("launch", "all"):
                    extra_env = {"APPIMAGE_EXTRACT_AND_RUN": "1"}
                    launch_and_wait(appimage, args.timeout, extra_env)
        else:
            image = args.image.resolve()
            if args.mode in ("verify", "all"):
                verify_image(image)
            if args.mode in ("launch", "all"):
                launch_image(image, args.timeout)
    except SmokeFailure as error:
        print(f"SMOKE TEST FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
