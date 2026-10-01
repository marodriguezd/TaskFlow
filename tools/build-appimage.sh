#!/usr/bin/env bash
# Assemble a portable .AppImage from a jpackage app image (Linux only).
#
# Usage: build-appimage.sh <path-to-TaskFlow-app-image> <output-TaskFlow-x86_64.AppImage>
#
# Expects APPIMAGETOOL_SHA256 in the environment when APPIMAGETOOL_BIN is not
# already provided: the tool is downloaded once into .gradle/appimagetool/ and
# its SHA-256 is verified against the pinned digest before use.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <app-image-dir> <output.AppImage>" >&2
  exit 2
fi

APP_IMAGE_DIR="$1"
OUTPUT_APPIMAGE="$2"

if [ ! -d "${APP_IMAGE_DIR}" ]; then
  echo "ERROR: app image directory not found: ${APP_IMAGE_DIR}" >&2
  exit 1
fi

if [ ! -x "${APP_IMAGE_DIR}/bin/TaskFlow" ]; then
  echo "ERROR: not a jpackage app image (missing bin/TaskFlow): ${APP_IMAGE_DIR}" >&2
  exit 1
fi

# 1. Resolve appimagetool (download + checksum-verify, or use a provided binary).
TOOL_DIR="${APPIMAGETOOL_DIR:-$HOME/.cache/taskflow/appimagetool}"
mkdir -p "${TOOL_DIR}"

if [ -n "${APPIMAGETOOL_BIN:-}" ]; then
  APPIMAGETOOL="${APPIMAGETOOL_BIN}"
else
  APPIMAGETOOL_SHA256="${APPIMAGETOOL_SHA256:?APPIMAGETOOL_SHA256 must be set (or provide APPIMAGETOOL_BIN)}"
  APPIMAGETOOL_URL="${APPIMAGETOOL_URL:-https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage}"
  APPIMAGETOOL="${TOOL_DIR}/appimagetool"

  if [ ! -x "${APPIMAGETOOL}" ]; then
    echo "Downloading appimagetool from ${APPIMAGETOOL_URL}"
    curl -fsSL --retry 3 -o "${APPIMAGETOOL}.download" "${APPIMAGETOOL_URL}"
    echo "${APPIMAGETOOL_SHA256}  ${APPIMAGETOOL}.download" | sha256sum -c - >/dev/null
    mv "${APPIMAGETOOL}.download" "${APPIMAGETOOL}"
    chmod +x "${APPIMAGETOOL}"
  fi
fi

# 2. Build the AppDir layout on top of the jpackage app image.
APPDIR="$(mktemp -d)/TaskFlow.AppDir"
mkdir -p "${APPDIR}/usr/bin" "${APPDIR}/usr/share/icons/hicolor/256x256/apps" "${APPDIR}/usr/share/applications"
cp -a "${APP_IMAGE_DIR}/." "${APPDIR}/usr/bin/"

mv "${APPDIR}/usr/bin/TaskFlow.png" "${APPDIR}/usr/share/icons/hicolor/256x256/apps/taskflow.png" 2>/dev/null ||
  cp "${APPDIR}/usr/bin/lib/TaskFlow.png" "${APPDIR}/usr/share/icons/hicolor/256x256/apps/taskflow.png" 2>/dev/null ||
  cp "assets/TaskFlow.png" "${APPDIR}/usr/share/icons/hicolor/256x256/apps/taskflow.png"
mv "${APPDIR}/usr/bin/TaskFlow.desktop" "${APPDIR}/usr/share/applications/taskflow.desktop" 2>/dev/null || true

# AppRun: keep the JDK-bundled launcher happy no matter where the AppImage is mounted.
cat > "${APPDIR}/AppRun" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
HERE="$(dirname "$(readlink -f "$0")")"
exec "${HERE}/usr/bin/bin/TaskFlow" "$@"
EOF
chmod +x "${APPDIR}/AppRun"

# Top-level .desktop + icon so desktop environments recognize the mounted AppImage.
cp "${APPDIR}/usr/share/applications/taskflow.desktop" "${APPDIR}/taskflow.desktop" 2>/dev/null || true
[ -f "${APPDIR}/taskflow.desktop" ] || cat > "${APPDIR}/taskflow.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=TaskFlow
Comment=Lightweight desktop task manager with Pomodoro timers
Exec=TaskFlow
Icon=taskflow
Categories=Utility;
EOF
cp "${APPDIR}/usr/share/icons/hicolor/256x256/apps/taskflow.png" "${APPDIR}/.DirIcon"
cp "${APPDIR}/usr/share/icons/hicolor/256x256/apps/taskflow.png" "${APPDIR}/taskflow.png"

# 3. Point the desktop entry at the canonical AppImage exec name and pack it.
sed -i 's|^Exec=.*|Exec=AppRun|' "${APPDIR}/taskflow.desktop" 2>/dev/null || true

# FUSE may be unavailable on minimal runners/CI: fall back to the extraction mode.
if ! "${APPIMAGETOOL}" --appimage-extract-and-run "${APPDIR}" "${OUTPUT_APPIMAGE}"; then
  echo "appimagetool failed with FUSE; retrying with APPIMAGE_EXTRACT_AND_RUN=1" >&2
  APPIMAGE_EXTRACT_AND_RUN=1 "${APPIMAGETOOL}" --appimage-extract-and-run "${APPDIR}" "${OUTPUT_APPIMAGE}"
fi

echo "AppImage written: ${OUTPUT_APPIMAGE}"
