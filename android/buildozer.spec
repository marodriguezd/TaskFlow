[app]
title = TaskFlow
package.name = taskflow
package.domain = com.taskflow
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,json,mp3
version = 1.0.0
requirements = python3,kivy
orientation = portrait
fullscreen = 0

# Android 14+
osx.kivy_version = 2.3.0
android.api = 34
android.minapi = 26
android.ndk = 25b
android.ndk_api = 26
android.archs = arm64-v8a, armeabi-v7a
android.accept_sdk_license = True

# Scoped storage: usar directorio privado de la app (no permisos externos)
android.permissions = 

# Archivos principales
presplash.filename = ../assets/generated/taskflow-512.png
icon.filename = ../assets/generated/taskflow-96.png

[buildozer]
log_level = 2
warn_on_root = 1
