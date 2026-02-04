# Changelog

Todos los cambios notables de este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es/1.0.0/),
y este proyecto se adhiere a [Versionado Semántico](https://semver.org/lang/es/).

## [1.5.0] - 2026-01-26

### Añadido
- **Anuncios Recompensados**: Nueva funcionalidad que permite al usuario ver un anuncio voluntariamente para obtener 30 minutos sin anuncios intersticiales
- Opción en el menú "Escuchar sin anuncios" con diálogo explicativo
- Sistema multi-entorno con 4 variantes de build (devDebug, devRelease, prodDebug, prodRelease)
- Configuración dinámica de IDs de anuncios por entorno
- BuildConfig fields para control de features por entorno:
  - `SHOW_ADS`: Control de anuncios
  - `ENABLE_CRASHLYTICS`: Control de Crashlytics
  - `ENABLE_ANALYTICS`: Control de Analytics
  - `ENVIRONMENT`: Identificador de entorno (DEV/PROD)
  - `USE_TEST_ADS`: Uso de anuncios de prueba
- Precarga inteligente de anuncios en background
- Script interactivo `build-variants.sh` para gestión de builds
- Documentación completa del sistema de entornos (`ENTORNOS.md`)
- Resumen técnico de implementación (`RESUMEN_IMPLEMENTACION.md`)

### Cambiado
- Sistema de anuncios completamente rediseñado:
  - Anuncios intersticiales se muestran al iniciar cada emisora
  - Precarga automática después de mostrar anuncios
  - Los anuncios respetan el período sin anuncios (recompensa)
- Manejo de estado de reproducción mejorado con verificaciones null-safe
- Firebase y Crashlytics ahora solo se inicializan en entorno de producción
- Configuración de ProGuard mejorada para AdMob y Firebase
- Version code: 6 → 7
- Version name: 1.3.0 → 1.5.0

### Corregido
- **Bug crítico**: NullPointerException en `RadioService.isPlaying()` cuando mediaPlayer es null
- Crash al intentar verificar reproducción activa antes de inicializar el servicio
- Error de inicialización de Firebase en flavor dev
- Tareas de Google Services y Crashlytics ahora se deshabilitan correctamente en flavor dev
- Manejo robusto de errores con try-catch en todas las llamadas a Firebase
- Protección contra crashes cuando no hay anuncios disponibles ("No fill")

### Seguridad
- Actualizado `.gitignore` para prevenir commit de archivos sensibles:
  - Keystores (*.jks, *.keystore)
  - Archivos de configuración de firma
  - APKs y AABs generados
  - Archivos de log

### Técnico
- Refactorización de arquitectura de anuncios:
  - Separación de lógica de precarga y visualización
  - Callbacks dedicados para cada tipo de anuncio
  - Logging detallado para debugging
- Implementación de verificaciones de reproducción activa
- Sistema de conteo de tiempo para período sin anuncios
- Configuración condicional de plugins de Gradle por flavor

### Documentación
- Guía completa de uso de entornos de desarrollo
- Documentación de flujo de anuncios y comportamiento esperado
- Checklist pre-publicación
- Comandos rápidos para compilación
- Notas de versión para Play Store

## [1.3.0] - 2025-XX-XX

### Versión anterior
- Sistema de reproducción básico
- Anuncios intersticiales simples
- UI de reproducción con controles básicos
- Sistema de favoritos
- Notificaciones de reproducción

---

## Tipos de cambios

- `Añadido` para funcionalidades nuevas.
- `Cambiado` para cambios en funcionalidades existentes.
- `Obsoleto` para funcionalidades que pronto se eliminarán.
- `Eliminado` para funcionalidades eliminadas.
- `Corregido` para corrección de errores.
- `Seguridad` en caso de vulnerabilidades.

## Enlaces

- [1.5.0]: https://github.com/tuusuario/RadioCubanaPro/compare/v1.3.0...v1.5.0
- [1.3.0]: https://github.com/tuusuario/RadioCubanaPro/releases/tag/v1.3.0
