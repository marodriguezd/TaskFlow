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


append_colon_path_unique() {
  # append_colon_path_unique VAR_NAME VALUE
  local var_name="$1"
  local value="$2"
  local current="${!var_name-}"

  [[ -n "$value" && -d "$value" ]] || return 0

  if [[ -z "$current" ]]; then
    printf -v "$var_name" '%s' "$value"
    export "$var_name"
    return 0
  fi

  case ":$current:" in
    *":$value:"*) ;;
    *)
      printf -v "$var_name" '%s:%s' "$current" "$value"
      export "$var_name"
      ;;
  esac
}

collect_aclocal_paths() {
  local -a paths=()
  local acdir=""

  if acdir="$(aclocal --print-ac-dir 2>/dev/null)" && [[ -d "$acdir" ]]; then
    paths+=("$acdir")
  fi

  local -a binaries=(aclocal automake autoreconf libtoolize)
  local bin real root
  for bin in "${binaries[@]}"; do
    real="$(readlink -f "$(command -v "$bin")")"
    root="$(dirname "$(dirname "$real")")"

    [[ -d "$root/share/aclocal" ]] && paths+=("$root/share/aclocal")

    while IFS= read -r -d '' p; do
      paths+=("$p")
    done < <(find "$root/share" -maxdepth 2 -type d -name 'aclocal*' -print0 2>/dev/null || true)
  done

  printf '%s\n' "${paths[@]}"
}

# Preflight de herramientas nativas que Buildozer/python-for-android utiliza.
missing=()
for tool in git zip unzip javac make autoconf automake autoreconf aclocal libtool libtoolize pkg-config cmake find readlink; do
  command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
done

if (( ${#missing[@]} > 0 )); then
  echo "ERROR: faltan dependencias del sistema para compilar APK:"
  printf '  - %s\n' "${missing[@]}"
  echo
  echo "En NixOS añade esos paquetes a tu entorno (configuration.nix, nix-shell o nix develop) y reintenta."
  exit 1
fi

# JAVA_HOME robusto, derivado dinámicamente de javac.
JAVAC_REAL="$(readlink -f "$(command -v javac)")"
JAVA_HOME_CANDIDATE="$(dirname "$(dirname "$JAVAC_REAL")")"
if [[ ! -x "$JAVA_HOME_CANDIDATE/bin/java" ]]; then
  echo "ERROR: no se pudo derivar JAVA_HOME válido desde javac: $JAVAC_REAL"
  exit 1
fi
export JAVA_HOME="$JAVA_HOME_CANDIDATE"
append_colon_path_unique PATH "$JAVA_HOME/bin"

# NixOS: asegurar macros M4 visibles para aclocal/autoreconf (libtool, automake, etc.).
# Esto evita errores tipo AC_PROG_LIBTOOL / AC_PROG_LD undefined durante libffi/autogen.sh.
if [[ -n "${ACLOCAL_PATH:-}" ]]; then
  # Reexport explícito para heredar en subprocesses lanzados por Buildozer/p4a.
  export ACLOCAL_PATH
fi

while IFS= read -r m4dir; do
  append_colon_path_unique ACLOCAL_PATH "$m4dir"
done < <(collect_aclocal_paths)

if [[ -z "${ACLOCAL_PATH:-}" ]]; then
  echo "ERROR: no se pudo construir ACLOCAL_PATH (rutas de macros M4 no detectadas)."
  exit 1
fi

# Sanity-check rápido: verificar que las macros críticas existan en ACLOCAL_PATH.
if ! find ${ACLOCAL_PATH//:/ } -maxdepth 1 -type f -name '*.m4' -print 2>/dev/null | xargs -r grep -E 'AC_PROG_LIBTOOL|LT_INIT|AC_PROG_LD' -q; then
  echo "ERROR: no encuentro macros libtool en ACLOCAL_PATH."
  echo "ACLOCAL_PATH=$ACLOCAL_PATH"
  echo "Revisa que 'libtool' esté en tu entorno Nix y que incluya share/aclocal."
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

echo "[build_android] JAVA_HOME=$JAVA_HOME"
echo "[build_android] ACLOCAL_PATH=$ACLOCAL_PATH"

echo "[build_android] Ejecutando buildozer android $MODE"
cd android
buildozer android "$MODE"
