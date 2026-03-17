# Compilar TaskFlow Android (APK 14+)

## TL;DR (comando recomendado)

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

## ¿Por qué te salió `ModuleNotFoundError: No module named 'distutils'`?

Con Python 3.12, `distutils` ya no viene en la stdlib. Algunas versiones de Buildozer/python-for-android aún lo importan internamente.

El script `android/build_android.sh` prepara el entorno (pip/setuptools/buildozer/cython) y valida `import distutils` antes del build.

Si aun así falla en tu máquina, usa Python **3.11** para compilar el APK (recomendado para máxima compatibilidad hoy en día).

## Flujo manual (sin script)

```bash
source .venv/bin/activate
python -m pip install --upgrade pip setuptools wheel
python -m pip install --upgrade buildozer "cython==0.29.36"
cd android
buildozer android debug
```

APK generado (debug):

- `android/bin/taskflow-1.0.0-arm64-v8a_armeabi-v7a-debug.apk`

## Dependencias del sistema (Debian/Ubuntu/WSL)

```bash
sudo apt update
sudo apt install -y \
  git zip unzip openjdk-17-jdk python3-pip autoconf automake libtool pkg-config \
  zlib1g-dev libncurses6 libtinfo6 cmake libffi-dev libssl-dev
```

## Nota importante sobre `python android/main.py`

Ese comando intenta abrir la UI Kivy en Linux desktop y **no es necesario** para compilar APK.

Si quieres ejecutar la UI localmente (opcional), instala además:

```bash
sudo apt install -y libgl1 libmtdev1
```


## Error: `autoreconf: not found`

Si te aparece durante `libffi/autogen.sh`, te falta `autoconf` (que provee `autoreconf`) y normalmente `automake`.

Instala y relanza:

```bash
sudo apt update
sudo apt install -y autoconf automake
./android/build_android.sh debug
```
