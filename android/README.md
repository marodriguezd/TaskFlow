# Compilar TaskFlow Android (APK 14+)

> Si solo quieres compilar APK: **no ejecutes `python android/main.py` en Linux de escritorio**. Ese comando intenta abrir ventana Kivy local y requiere librerías gráficas del host (`libGL`, `mtdev`, etc.).

## Comandos exactos (desde la raíz del repo)

```bash
# 1) activar tu entorno
source .venv/bin/activate

# 2) instalar build tools python
pip install --upgrade pip
pip install buildozer cython==0.29.36

# 3) entrar al proyecto android
cd android

# 4) generar APK debug (primera vez tarda bastante)
buildozer android debug
```

APK generado:

- `android/bin/taskflow-1.0.0-arm64-v8a_armeabi-v7a-debug.apk`

## Release

```bash
cd android
buildozer android release
```

Luego hay que firmar el APK con tu keystore para poder distribuirlo.

## Dependencias del sistema (Debian/Ubuntu/WSL)

```bash
sudo apt update
sudo apt install -y \
  git zip unzip openjdk-17-jdk python3-pip autoconf libtool pkg-config \
  zlib1g-dev libncurses6 libtinfo6 cmake libffi-dev libssl-dev
```

## Sobre tu error actual (`libGL.so.1`, `libmtdev.so.1`)

Ese error aparece al ejecutar `python android/main.py` en desktop. Para compilar APK **no es necesario** correr ese comando.

Si aun así quieres probar UI Kivy en Linux local (opcional), instala:

```bash
sudo apt install -y libgl1 libmtdev1
```

Pero para tu objetivo (crear APK), céntrate en `buildozer android debug`.
