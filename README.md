# Liam 📸 — App de Fotos del Bautizo

App Android para subir y ver fotos del bautizo de Liam (14 de junio de 2026).

Las fotos se suben a **Cloudinary** y las URLs se guardan en **Firebase Firestore**, compartidas con la versión web.

## Ejecutar en local

**Requisitos:** [Android Studio](https://developer.android.com/studio)

1. Abre Android Studio
2. Selecciona **Open** y elige la carpeta `bautizoApk`
3. Deja que Android Studio sincronice el proyecto
4. Conecta tu móvil por USB (con depuración USB activada) o usa un emulador
5. Click en ▶️ **Run**

## Generar APK

1. En Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. El APK estará en `app/build/outputs/apk/debug/app-debug.apk`
3. Pásalo al móvil e instálalo (necesitas activar "Instalar apps de fuentes desconocidas")

## Servicios conectados

| Servicio | Uso |
|----------|-----|
| Cloudinary (`dzirz4hyk`) | Almacenamiento de fotos |
| Firebase Firestore (`liambautizo-d4d42`) | Base de datos compartida con la web |

## Características

- Subir fotos desde cámara o galería
- Subida en segundo plano con notificación de progreso
- Compresión automática (1200px, 75% JPEG)
- Galería con swipe en pantalla completa
- Bilingüe: Español y Rumano
- Sincronización en tiempo real con la web
