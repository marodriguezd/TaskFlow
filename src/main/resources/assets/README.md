# TaskFlow Application Assets

This directory contains icons and multimedia assets used for development, runtime resources, and native `jpackage` bundling.

## Assets Overview

- `TaskFlow.png`: Master high-resolution application icon (512x512 PNG).
- `taskflow.ico`: Windows multi-resolution icon for `.exe` and `.msi` installers.
- `taskflow.icns`: macOS multi-resolution icon bundle for `.dmg` packages.
- `bell.mp3`: Default timer completion bell sound chime.
- `generated/`: Multi-resolution PNG assets for Linux desktop integration and runtime scaling.

## Packaging Usage

The Gradle build automatically bundles these assets during `installDist` and native `jpackage` generation:
- On **Linux**: `TaskFlow.png` is used for window icons and application menus.
- On **Windows**: `taskflow.ico` is passed to `jpackage` for executable and taskbar icons.
- On **macOS**: `taskflow.icns` is passed to `jpackage` for dock and finder icons.
- `bell.mp3` is loaded from classpath resources (`/assets/bell.mp3`) and provisioned to the user's data directory on first run.
