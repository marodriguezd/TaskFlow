#!/usr/bin/env python3
"""Release provenance validation for TaskFlow.

Verifies that a release tag, the Gradle project version, the built commit, and
the packaged artifacts are mutually consistent. Designed to run in the GitHub
Actions release pipeline (validate job before builds, release job before
publishing), but works standalone.

Checks
------
1. Tag format is exactly vMAJOR.MINOR.PATCH (no leading zeros).
2. Tag version matches `version = "..."` in build.gradle.kts.
3. The tag points at the commit that was built (requires --commit and git).
4. All expected platform artifacts exist and every artifact carries a
   RELEASE_VERSION stamp equal to the release version (requires
   --artifacts-dir; --require-platform-artifacts additionally demands at least
   one .msi, .AppImage and .dmg).

With --strict, every applicable check gates the exit code. Without it, only
checks 1 and 2 do (3 and 4 are reported as warnings).
"""

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

TAG_RE = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
GRADLE_VERSION_RE = re.compile(r"""^version\s*=\s*["'](\d+\.\d+\.\d+)["']\s*$""", re.MULTILINE)
PACKAGE_EXTENSIONS = (".msi", ".AppImage", ".dmg")
PLATFORM_EXTENSIONS = {".msi": "Windows", ".AppImage": "Linux", ".dmg": "macOS"}


def error(msg: str) -> None:
    prefix = "::error::" if os.environ.get("GITHUB_ACTIONS") == "true" else "ERROR: "
    print(f"{prefix}{msg}", file=sys.stderr)


def warn(msg: str) -> None:
    prefix = "::warning::" if os.environ.get("GITHUB_ACTIONS") == "true" else "WARNING: "
    print(f"{prefix}{msg}", file=sys.stderr)


def check_tag_format(tag: str) -> bool:
    if not TAG_RE.match(tag):
        error(f"tag '{tag}' is not in strict vMAJOR.MINOR.PATCH format")
        return False
    print(f"OK  tag format: {tag}")
    return True


def check_gradle_version(gradle_file: Path, version: str) -> bool:
    try:
        content = gradle_file.read_text(encoding="utf-8")
    except OSError as e:
        error(f"cannot read {gradle_file}: {e}")
        return False
    match = GRADLE_VERSION_RE.search(content)
    if not match:
        error(f"no 'version = \"X.Y.Z\"' declaration found in {gradle_file}")
        return False
    project_version = match.group(1)
    if project_version != version:
        error(f"tag version {version} != project version {project_version} in {gradle_file}")
        return False
    print(f"OK  tag matches project version: {version}")
    return True


def check_tag_commit(tag: str, commit: str) -> bool:
    try:
        resolved = subprocess.run(
            ["git", "rev-parse", f"{tag}^{{commit}}"],
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError) as e:
        error(f"cannot resolve tag '{tag}' to a commit (run inside the repository): {e}")
        return False
    if resolved != commit:
        error(f"tag '{tag}' points at {resolved}, but the built commit is {commit}")
        return False
    print(f"OK  tag points at built commit: {commit[:12]}")
    return True


def check_artifacts(artifacts_dir: Path, version: str, require_platforms: bool) -> bool:
    ok = True
    packages = sorted(
        p for p in artifacts_dir.rglob("*") if p.is_file() and p.suffix in PACKAGE_EXTENSIONS
    )
    if not packages:
        error(f"no installer packages found under {artifacts_dir}")
        return False

    seen_exts = set()
    for pkg in packages:
        stamp_file = pkg.parent / "RELEASE_VERSION"
        if not stamp_file.is_file():
            error(f"missing RELEASE_VERSION stamp next to {pkg} (unverifiable provenance)")
            ok = False
            continue
        stamped = stamp_file.read_text(encoding="utf-8").strip()
        if stamped != version:
            error(f"{pkg}: stamp says {stamped!r}, expected {version!r}")
            ok = False
            continue
        seen_exts.add(pkg.suffix)
        print(f"OK  {pkg.name} (stamp: {stamped})")

    if require_platforms:
        for ext, platform in PLATFORM_EXTENSIONS.items():
            if ext not in seen_exts:
                error(f"no {ext} package found (required for {platform})")
                ok = False
            else:
                print(f"OK  platform package present: {platform} ({ext})")
    return ok


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tag", required=True, help="release tag, e.g. v1.0.0")
    parser.add_argument("--gradle", type=Path, default=Path("build.gradle.kts"), help="path to build.gradle.kts")
    parser.add_argument("--commit", help="commit SHA the release is built from")
    parser.add_argument("--artifacts-dir", type=Path, help="directory containing platform artifacts")
    parser.add_argument(
        "--require-platform-artifacts",
        action="store_true",
        help="fail unless at least one .msi, .AppImage and .dmg artifact is present",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="gate the exit code on every applicable check, not only tag format and version",
    )
    args = parser.parse_args()

    results = {
        "format": check_tag_format(args.tag),
        "version": check_gradle_version(args.gradle, args.tag[1:]),
    }
    if args.commit:
        results["commit"] = check_tag_commit(args.tag, args.commit)
    if args.artifacts_dir:
        results["artifacts"] = check_artifacts(
            args.artifacts_dir, args.tag[1:], args.require_platform_artifacts
        )

    gating = results if args.strict else {"format": results["format"], "version": results["version"]}
    failed = [name for name, passed in gating.items() if not passed]
    if failed:
        error(f"release validation failed: {', '.join(sorted(failed))}")
        return 1
    print("Release validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
