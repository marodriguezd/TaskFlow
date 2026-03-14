"""Genera iconos multi-resolución para TaskFlow a partir de un PNG maestro.

Uso típico:
    python generate_icons.py

Entrada por defecto:
    assets/TaskFlow.png

Salida:
- assets/taskflow.ico            (multi-size, ideal para Windows/PyInstaller)
- assets/taskflow.icns           (si Pillow tiene soporte ICNS)
- assets/generated/taskflow-*.png
"""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Iterable


PNG_SIZES: tuple[int, ...] = (16, 20, 24, 32, 40, 48, 64, 72, 96, 128, 256, 512)
ICO_SIZES: tuple[int, ...] = (16, 20, 24, 32, 40, 48, 64, 128, 256)


def _find_source(assets_dir: Path) -> Path:
    preferred = assets_dir / "TaskFlow.png"
    if preferred.exists():
        return preferred

    fallback_candidates = [
        assets_dir / "taskflow.png",
        assets_dir / "icon.png",
        assets_dir / "app.png",
    ]
    for candidate in fallback_candidates:
        if candidate.exists():
            return candidate

    matches = sorted(assets_dir.glob("*.png"))
    if matches:
        return matches[0]

    raise FileNotFoundError(
        "No se encontró PNG base en assets. Añade assets/TaskFlow.png"
    )


def _resize_and_save_pngs(img, output_dir: Path, sizes: Iterable[int]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for size in sizes:
        out = output_dir / f"taskflow-{size}.png"
        resized = img.resize((size, size), resample=img.Resampling.LANCZOS)
        resized.save(out, format="PNG")


def _save_ico(img, output_file: Path, sizes: Iterable[int]) -> None:
    output_file.parent.mkdir(parents=True, exist_ok=True)
    ico_sizes = [(s, s) for s in sizes]
    img.save(output_file, format="ICO", sizes=ico_sizes)


def _save_icns(img, output_file: Path) -> bool:
    """Guarda .icns si el entorno lo soporta. Devuelve True/False."""
    try:
        output_file.parent.mkdir(parents=True, exist_ok=True)
        img.save(output_file, format="ICNS")
        return True
    except Exception:
        return False


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--assets-dir",
        default="assets",
        help="Directorio de assets (default: assets)",
    )
    args = parser.parse_args()

    try:
        from PIL import Image
    except ImportError as exc:
        raise SystemExit(
            "Falta Pillow. Instala con: pip install pillow"
        ) from exc

    assets_dir = Path(args.assets_dir).resolve()
    source = _find_source(assets_dir)

    with Image.open(source) as raw:
        img = raw.convert("RGBA")

    generated_dir = assets_dir / "generated"
    _resize_and_save_pngs(img, generated_dir, PNG_SIZES)

    ico_file = assets_dir / "taskflow.ico"
    _save_ico(img, ico_file, ICO_SIZES)

    icns_file = assets_dir / "taskflow.icns"
    icns_created = _save_icns(img, icns_file)

    print(f"Fuente: {source}")
    print(f"ICO generado: {ico_file}")
    print(f"PNGs generados en: {generated_dir}")
    if icns_created:
        print(f"ICNS generado: {icns_file}")
    else:
        print("ICNS no generado (soporte ICNS no disponible en este entorno).")


if __name__ == "__main__":
    main()
