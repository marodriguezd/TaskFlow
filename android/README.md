# Compilar TaskFlow Android (APK 14+)

## TL;DR (recomendado)

Desde la raíz del repo:

```bash
source .venv/bin/activate
./android/build_android.sh debug
```

Para release:

```bash
source .venv/bin/activate
./android/build_android.sh release
```

## NixOS: paquetes mínimos recomendados

En tu entorno de Nix (`configuration.nix`, `nix-shell`, `nix develop`) incluye al menos:

- jdk17
- gnumake
- git
- zip
- unzip
- autoconf
- automake
- libtool
- pkg-config
- cmake
- findutils
- coreutils

El script `android/build_android.sh` valida estas herramientas y corta con error claro si falta alguna.

## Error `AC_PROG_LIBTOOL` / `AC_PROG_LD` en libffi/autoreconf

Ese error suele ser de macros M4 no visibles para `aclocal/autoreconf` en NixOS.

`android/build_android.sh` ya aplica fix robusto:

- deriva `JAVA_HOME` desde `javac`
- construye `ACLOCAL_PATH` dinámicamente
- fuerza `autoreconf/aclocal` con includes `-I` vía wrappers temporales

Si cambiaste paquetes del entorno Nix o ya había cache vieja de Buildozer, limpia y recompila:

```bash
rm -rf android/.buildozer/android/platform/build-*/build/other_builds/libffi
./android/build_android.sh debug
```

## Nota sobre `python android/main.py`

Ese comando intenta abrir la UI Kivy en desktop y **no es necesario** para compilar APK.
