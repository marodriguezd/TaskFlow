# TaskFlow - Windows - 1.4

TaskFlow Windows 1.4  
Esta release está enfocada exclusivamente en el cambio de icono de la aplicación.

## ✅ Qué incluye

- Nuevo logo oficial aplicado como imagen maestra del proyecto (`assets/TaskFlow.png`).
- Reemplazo del recurso base utilizado por el pipeline de iconos.
- Scripts de build y runtime actualizados para priorizar el nuevo logo.
- Plantilla/documentación de release ajustada para reflejar que la 1.4 es un cambio visual de icono.

## 🆕 Novedades de la 1.4

- El pipeline de generación de iconos usa por defecto `assets/TaskFlow.png`.
- El ejecutable compilado (`PyInstaller`) prioriza el nuevo logo al resolver el icono.
- La app en runtime prioriza el nuevo logo al cargar el icono de ventana.

## 📦 Formato de distribución

- `TaskFlow.exe` (standalone, sin instalación adicional de Python).

## 🖥️ Requisitos

- Windows 10/11 (64-bit recomendado).

## 🚀 Uso rápido

1. Ejecuta `TaskFlow.exe`.
2. Verifica que el icono de la app corresponda al nuevo logo.

## ℹ️ Notas

- Esta versión no introduce cambios funcionales: solo actualización de identidad visual (icono).
- Si Windows muestra un icono antiguo, puede ser caché del Explorador.
