#!/usr/bin/env python3
"""Extracts only the release notes section for a given tag from RELEASE_NOTES.md."""

import re
import sys
from pathlib import Path


def extract_release_notes(notes_path: Path, tag: str) -> str:
    content = notes_path.read_text(encoding="utf-8")
    clean_tag = tag if tag.startswith("v") else f"v{tag}"
    version_pattern = re.escape(clean_tag)

    header_re = re.compile(rf"^#\s+TaskFlow\s+{version_pattern}\b.*$", re.MULTILINE)
    match = header_re.search(content)
    if not match:
        raise ValueError(f"Header for {clean_tag} not found in {notes_path}")

    start_idx = match.start()
    delimiter_re = re.compile(r"^(?:---|\#\s+TaskFlow\s+v\d+)", re.MULTILINE)
    next_match = delimiter_re.search(content, pos=match.end())
    if next_match:
        section = content[start_idx : next_match.start()]
    else:
        section = content[start_idx:]

    return section.strip()


def main():
    if len(sys.argv) < 3:
        print(
            f"Usage: {sys.argv[0]} <RELEASE_NOTES.md> <tag> [checksums_file]",
            file=sys.stderr,
        )
        sys.exit(1)

    notes_path = Path(sys.argv[1])
    tag = sys.argv[2]
    try:
        notes = extract_release_notes(notes_path, tag)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    print(notes)

    if len(sys.argv) >= 4:
        checksums_path = Path(sys.argv[3])
        if checksums_path.is_file():
            checksums_text = checksums_path.read_text(encoding="utf-8").strip()
            print("\n## Checksums\n")
            print("```")
            print(checksums_text)
            print("```")


if __name__ == "__main__":
    main()
