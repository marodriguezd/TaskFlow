# TaskFlow - Windows - 1.3

TaskFlow Windows 1.3  
Esta versión consolida mejoras de productividad y añade personalización del sonido de fin de temporizador.

## ✅ Qué incluye

- Ventana nativa de Windows (minimizar, maximizar y snap layout).
- Gestión de tareas con prioridad (`Alta`, `Media`, `Baja`), temporizador y barra de progreso.
- Edición rápida (✎), eliminación (✕) y completado manual (✓) por tarjeta.
- Historial unificado (completadas por tiempo, completadas manualmente y eliminadas) con opción de **Restaurar**.
- Persistencia de tareas, historial, geometría de ventana, tema y estado de chincheta.

## 🆕 Novedades de la 1.3

- Sonido de finalización configurable mediante `bell.mp3`.
- Ruta de personalización para usuario:
  - `C:\Users\<usuario>\.TaskFlow\bell.mp3`
- En primera ejecución, si el archivo no existe, TaskFlow intenta copiar el sonido por defecto incluido con la app.
- Para cambiar la campana, basta con reemplazar ese archivo por otro MP3 con el mismo nombre.

## 📦 Formato de distribución

- `TaskFlow.exe` (standalone, sin instalación adicional de Python).

## 🖥️ Requisitos

- Windows 10/11 (64-bit recomendado).

## 🚀 Uso rápido

1. Ejecuta `TaskFlow.exe`.
2. Crea tareas y usa Play/Pausa para el temporizador.
3. (Opcional) Personaliza la campana reemplazando `C:\Users\<usuario>\.TaskFlow\bell.mp3`.

## ℹ️ Notas

- Los datos de la app se guardan en `C:\Users\<usuario>\.TaskFlow`.
- Si existen archivos legacy (`.taskflow_*.json`), se migran automáticamente.
- Si Windows muestra un icono antiguo, puede ser caché del Explorador.
