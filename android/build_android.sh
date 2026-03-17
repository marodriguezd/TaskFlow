#!/usr/bin/env bash
set -euo pipefail

# Ejecutar desde la raíz del repo: ./android/build_android.sh [debug|release]
MODE="${1:-debug}"
if [[ "$MODE" != "debug" && "$MODE" != "release" ]]; then
  echo "Uso: ./android/build_android.sh [debug|release]"
  exit 1
fi

if [[ ! -f "android/buildozer.spec" ]]; then
  echo "Error: ejecuta este script desde la raíz del repo (donde existe ./android/buildozer.spec)."
  exit 1
fi

if [[ -z "${VIRTUAL_ENV:-}" ]]; then
  echo "Aviso: no detecto entorno virtual activo. Recomendado: source .venv/bin/activate"
fi

python -m pip install --upgrade pip setuptools wheel
python -m pip install --upgrade buildozer "cython==0.29.36"

python - <<'PY'
import sys
try:
    import distutils  # noqa: F401
except Exception as exc:
    raise SystemExit(
        "ERROR: no se pudo importar 'distutils'. "
        "Usa Python 3.11 para compilar APK o revisa setuptools en tu venv. "
        f"Detalle: {exc}"
    )
print("OK: distutils disponible en", sys.executable)
PY

cd android
buildozer android "$MODE"
