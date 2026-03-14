# TaskFlow - Windows - 1.2

TaskFlow Windows 1.2  
Nueva versión para Windows con mejoras centradas en personalización visual, control manual de tareas e historial más completo.

✅ Qué incluye

- Ventana nativa de Windows (minimizar, maximizar y snap layout).
- Gestión de tareas con prioridad (Alta, Media, Baja).
- Temporizador por tarea con barra de progreso.
- Historial unificado de tareas **completadas y eliminadas**.
- Persistencia automática de tareas, historial y tamaño/posición de la ventana.
- Botón de chincheta para activar/desactivar “siempre en primer plano”.
- Icono de aplicación integrado para ejecutable y ventana.
- Edición de tareas existentes desde cada tarjeta (icono 🖊️):
  - editar nombre
  - editar tiempo
  - editar prioridad
- Almacenamiento en carpeta dedicada `~/.TaskFlow` (en Windows: `C:\Users\<usuario>\.TaskFlow`).
- Migración automática de archivos legacy (`.taskflow_*.json`) al nuevo directorio en el primer arranque.

🆕 Novedades de la 1.2

- Tema **claro/oscuro** con botón de cambio en cabecera.
- Persistencia del tema seleccionado entre reinicios.
- Persistencia del estado de chincheta (always on top) entre reinicios.
- Marcado manual de tarea completada (botón `✓` en tarjeta), sin agotar el temporizador.
- Registro de tipo de evento en historial:
  - completada por temporizador
  - completada manualmente
  - eliminada
- Botón **Restaurar** en historial para devolver tareas al listado activo.
- Ajuste visual del diálogo de historial (más ancho y sin scroll horizontal).
- Consistencia visual de hover en botones del historial según tema.

📦 Formato de distribución

- `TaskFlow.exe` (standalone, sin instalación adicional de Python).

🖥️ Requisitos

- Windows 10/11 (64-bit recomendado).

🚀 Uso

1. Descarga `TaskFlow.exe`.
2. Ejecuta el archivo.
3. Crea tareas y usa Play/Pausa para iniciar temporizadores.
4. Para editar una tarea, pulsa el icono 🖊️ en su tarjeta y guarda cambios.
5. Para marcar una tarea manualmente como hecha, pulsa `✓` en su tarjeta.
6. Para cambiar el tema, usa el botón de tema en la cabecera.
7. Para recuperar tareas completadas/eliminadas, abre Historial y pulsa **Restaurar**.

ℹ️ Notas

- Si Windows muestra un icono antiguo, puede ser caché del Explorador; cerrar sesión o reiniciar Explorer suele actualizarlo.
- Esta release está centrada exclusivamente en Windows.
- Si existen archivos de datos antiguos en la raíz del usuario, se migran automáticamente a `.TaskFlow`.

🛠️ Feedback

- Si detectas algún bug o mejora de UX, abre un issue con pasos para reproducirlo y captura de pantalla si aplica.
