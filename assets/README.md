Coloca aquí el icono maestro y genera los iconos de build/runtime.

## Flujo recomendado
1. Coloca tu PNG base en:
   - `assets/TaskFlow.png`
2. Genera variantes:
   - `python generate_icons.py`
3. Compila:
   - `python compiler.py`

## Archivos generados
- `assets/taskflow.ico` (prioridad para Windows/PyInstaller)
- `assets/taskflow.icns` (si Pillow soporta ICNS)
- `assets/generated/taskflow-*.png`

## Nota
Si no tienes Pillow:
- `pip install pillow`

También se aceptan variantes de nombre en mayúsculas/minúsculas (por ejemplo `TaskFlow.png` o `taskflow.png`).
