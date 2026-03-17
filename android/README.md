# TaskFlow Android (APK)

Esta carpeta contiene un port móvil en **Python + Kivy** para compilar APK Android 14+ con Buildozer.

## Requisitos de build (Linux recomendado)

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install buildozer cython==0.29.36
sudo apt update
sudo apt install -y git zip unzip openjdk-17-jdk python3-pip autoconf libtool pkg-config zlib1g-dev libncurses6 libtinfo6 cmake libffi-dev libssl-dev
```

> En Windows, lo más estable es compilar APK usando WSL2 o Docker Linux.

## Generar APK (debug)

Desde la raíz del repo:

```bash
cd android
buildozer android debug
```

APK esperado:

- `android/bin/taskflow-1.0.0-arm64-v8a_armeabi-v7a-debug.apk`

## Generar APK (release)

```bash
cd android
buildozer android release
```

Después firma/alinea el APK con tu keystore para distribución.

## Notas técnicas

- `android.api = 34` en `buildozer.spec` (compatibilidad Android 14+).
- Persistencia en `App.user_data_dir` (sandbox interno de la app).
- La app móvil mantiene funciones clave:
  - crear tarea
  - iniciar/pausar temporizador
  - completar y registrar historial
  - guardar/cargar tareas en JSON
