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

# Preflight de herramientas nativas que Buildozer/python-for-android usa durante recetas como libffi.
missing=()
for tool in git zip unzip javac autoconf automake libtool pkg-config cmake; do
  command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
done
command -v autoreconf >/dev/null 2>&1 || missing+=("autoreconf (paquete autoconf)")

if (( ${#missing[@]} > 0 )); then
  echo "ERROR: faltan dependencias del sistema para compilar APK:"
  printf '  - %s\n' "${missing[@]}"
  echo
  echo "Instala en Debian/Ubuntu/WSL:"
  echo "  sudo apt update && sudo apt install -y git zip unzip openjdk-17-jdk python3-pip autoconf automake libtool pkg-config zlib1g-dev libncurses6 libtinfo6 cmake libffi-dev libssl-dev"
  exit 1
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
