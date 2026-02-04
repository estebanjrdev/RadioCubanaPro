# Guía de Entornos de Desarrollo y Producción

## 📋 Configuración de Entornos

Esta aplicación está configurada con **múltiples entornos** para facilitar el desarrollo y las pruebas.

## 🏗️ Variantes de Build Disponibles

### **1. DevDebug** (Desarrollo + Debug)
- **Package ID**: `com.ejrm.radiocubana.pro.dev.debug`
- **Nombre de App**: "Radio Cubana (Dev)"
- **Anuncios**: IDs de prueba de Google (NO generan ingresos)
- **Crashlytics**: ❌ Deshabilitado
- **Analytics**: ❌ Deshabilitado
- **Ofuscación**: ❌ No
- **Uso**: Desarrollo diario, pruebas locales

```bash
# Compilar e instalar
./gradlew installDevDebug

# Ejecutar
./gradlew assembleDevDebug
```

### **2. DevRelease** (Desarrollo + Release)
- **Package ID**: `com.ejrm.radiocubana.pro.dev`
- **Nombre de App**: "Radio Cubana (Dev)"
- **Anuncios**: IDs de prueba de Google
- **Crashlytics**: ✅ Habilitado
- **Analytics**: ✅ Habilitado
- **Ofuscación**: ✅ Sí (ProGuard)
- **Uso**: Pruebas finales antes de producción

```bash
./gradlew assembleDevRelease
```

### **3. ProdDebug** (Producción + Debug)
- **Package ID**: `com.ejrm.radiocubana.pro.debug`
- **Nombre de App**: "Radio Cubana (Debug)"
- **Anuncios**: ❌ Deshabilitados en debug
- **Crashlytics**: ❌ Deshabilitado
- **Analytics**: ❌ Deshabilitado
- **Ofuscación**: ❌ No
- **Uso**: Depuración con configuración de producción

```bash
./gradlew installProdDebug
```

### **4. ProdRelease** ⭐ (Producción Final)
- **Package ID**: `com.ejrm.radiocubana.pro`
- **Nombre de App**: "Radio Cubana"
- **Anuncios**: IDs reales (GENERAN INGRESOS)
- **Crashlytics**: ✅ Habilitado
- **Analytics**: ✅ Habilitado
- **Ofuscación**: ✅ Sí (ProGuard)
- **Firmado**: ✅ Con keystore
- **Uso**: Publicación en Play Store

```bash
./gradlew assembleProdRelease
```

## 🎯 IDs de Anuncios por Entorno

### **Dev (Desarrollo)**
```
Intersticial: ca-app-pub-3940256099942544/1033173712 (Prueba de Google)
Recompensado: ca-app-pub-3940256099942544/5224354917 (Prueba de Google)
```

### **Prod (Producción)**
```
Intersticial: ca-app-pub-3706009063515657/3663170922 (Tu ID real)
Recompensado: [PENDIENTE - Crear en AdMob]
```

## ⚙️ Configuración Inicial

### 1. **Configurar Keystore para Firma**

Edita `app/build.gradle` líneas 27-32:

```gradle
signingConfigs {
    release {
        storeFile file("/ruta/a/tu/keystore.jks")
        storePassword "TU_PASSWORD"
        keyAlias "TU_ALIAS"
        keyPassword "TU_PASSWORD"
    }
}
```

### 2. **Crear ID de Anuncio Recompensado**

1. Ve a [AdMob Console](https://apps.admob.com)
2. Selecciona tu app "Radio Cubana"
3. Ve a **Unidades de anuncios**
4. Crea nueva unidad → **Recompensado**
5. Copia el ID generado
6. Edita `app/build.gradle` línea ~69:

```gradle
prod {
    resValue "string", "rewarded_ad_unit_id", "ca-app-pub-XXXXX/YYYYYYY"
}
```

## 🔄 Flujo de Trabajo Recomendado

### **Durante Desarrollo:**
```bash
# Usa DevDebug - Rápido, sin ofuscación
./gradlew installDevDebug
adb logcat | grep -E "Anuncios|MainActivity"
```

### **Antes de Publicar:**
```bash
# 1. Prueba con DevRelease
./gradlew installDevRelease

# 2. Verifica que todo funcione con anuncios de prueba
# 3. Si todo está bien, compila ProdRelease
./gradlew assembleProdRelease

# 4. El APK/AAB estará en:
# app/build/outputs/apk/prod/release/
# app/build/outputs/bundle/prodRelease/
```

### **Para Play Store:**
```bash
# Generar App Bundle firmado
./gradlew bundleProdRelease

# Archivo generado:
# app/build/outputs/bundle/prodRelease/app-prod-release.aab
```

## 📱 Instalar Múltiples Versiones

Puedes tener **3 versiones instaladas simultáneamente**:

```bash
# Instalar las 3 versiones en el mismo dispositivo
./gradlew installDevDebug    # Radio Cubana (Dev)
./gradlew installDevRelease   # Radio Cubana (Dev)
./gradlew installProdRelease  # Radio Cubana
```

Cada una tiene un **package ID diferente**, por lo que no se sobrescriben.

## 🐛 Debug y Logs

### Ver logs de anuncios:
```bash
adb logcat | grep "Anuncios"
```

### Ver logs del entorno:
```bash
adb logcat | grep "MainActivity"
```

### Ejemplo de logs:
```
D/MainActivity: Iniciando en modo: DEV
D/MainActivity: Anuncios habilitados: false
D/MainActivity: Crashlytics habilitado: false
D/Anuncios: Anuncios deshabilitados en DEV
```

## ✅ Checklist Pre-Publicación

Antes de publicar en Play Store:

- [ ] Compilar con `prodRelease`
- [ ] Verificar que `versionCode` y `versionName` estén actualizados
- [ ] Confirmar que los IDs de anuncios reales estén configurados
- [ ] Probar anuncios intersticiales en dispositivo real
- [ ] Probar anuncios recompensados
- [ ] Verificar que Crashlytics esté reportando
- [ ] Revisar ProGuard rules si hay crashes
- [ ] Firmar con keystore de producción

## 🔐 BuildConfig Fields Disponibles

En el código puedes acceder a:

```kotlin
BuildConfig.ENVIRONMENT          // "DEV" o "PROD"
BuildConfig.SHOW_ADS            // true/false
BuildConfig.ENABLE_CRASHLYTICS  // true/false
BuildConfig.ENABLE_ANALYTICS    // true/false
BuildConfig.USE_TEST_ADS        // true/false
BuildConfig.VERSION_NAME        // "1.3.0"
BuildConfig.VERSION_CODE        // 6
```

### Ejemplo de uso:

```kotlin
if (BuildConfig.SHOW_ADS) {
    precargarAnuncios()
}

if (BuildConfig.ENVIRONMENT == "DEV") {
    Log.d("Debug", "Modo desarrollo activo")
}
```

## 📊 Resumen Visual

| Variante | Package ID | Anuncios | Crashlytics | ProGuard | Uso |
|----------|-----------|----------|-------------|----------|-----|
| DevDebug | `.dev.debug` | ❌ | ❌ | ❌ | Desarrollo |
| DevRelease | `.dev` | Test | ✅ | ✅ | Pre-prod |
| ProdDebug | `.debug` | ❌ | ❌ | ❌ | Debug prod |
| ProdRelease | (base) | ✅ Real | ✅ | ✅ | **Play Store** |

## 🚀 Comandos Rápidos

```bash
# Limpiar proyecto
./gradlew clean

# Compilar todas las variantes
./gradlew assemble

# Listar todas las tareas disponibles
./gradlew tasks --all | grep -i radiocubana

# Ver dependencias
./gradlew :app:dependencies
```

## ⚠️ Notas Importantes

1. **Nunca commitees el keystore** al repositorio
2. **Los IDs de prueba NO generan ingresos** - Solo usar en dev
3. **DevDebug no muestra anuncios** - Desarrollo más rápido
4. **ProdRelease requiere keystore** - Configúralo antes de compilar
5. **Crashlytics en debug** puede afectar performance - Deshabilitado por defecto

---

**Última actualización**: Enero 2026
**Versión de la app**: 1.3.0
**Autor**: @estebanjrdev
