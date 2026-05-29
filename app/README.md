# App móvil — Fiscalización HSE (Flutter)

Proyecto Flutter en esta carpeta (`pubspec.yaml` en la raíz de `app/`).

## Ejecutar (tu emulador Small_Phone)

```powershell
cd "D:\PROGRAMAS 2025 CONTRUCCION\Fiscalizacion HSE\app"

# 1) Abrir el emulador que usas
flutter emulators --launch Small_Phone

# 2) Cuando arranque, ver el id exacto (cambia según el AVD)
flutter devices

# 3) Correr (APK solo x64 = más liviano para el emulador)
flutter run --target-platform android-x64
```

O con el script incluido:

```powershell
.\run-android.ps1
```

Si tienes varios dispositivos: `flutter run --target-platform android-x64 -d <id>`.

## Si falla: `INSTALL_FAILED_INSUFFICIENT_STORAGE`

La app **sí compila** (`√ Built app-debug.apk`). El emulador **no tiene espacio** (Small_Phone suele quedar al ~93%).

**Opción A — Borrar datos del AVD (recomendado):**

```powershell
.\run-android.ps1 -Wipe
# Espera a que abra el emulador limpio, luego:
.\run-android.ps1
```

O en Android Studio → Device Manager → Small_Phone → ⋮ → **Wipe Data**.

**Opción B — Probar sin emulador (inmediato):**

```powershell
flutter run -d windows
```

## Si salen muchas líneas rojas de Kotlin al compilar

Suele ser caché de Gradle/Kotlin corrupta (más frecuente con rutas con espacios). Prueba:

```powershell
cd "D:\PROGRAMAS 2025 CONTRUCCION\Fiscalizacion HSE\app"
flutter clean
Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
cd android
.\gradlew --stop
cd ..
flutter pub get
flutter run
```

Si al final ves `√ Built ... app-debug.apk`, **la compilación sí funcionó**; el problema puede ser solo al instalar en el emulador (espacio).

## Flujo de la app

1. **Splash** → con sesión: Home · sin sesión: **Landing**
2. **Landing** → Acceder / Ingresa al sistema → **Login**
3. **Login** → Home

## API

Configuración en `lib/config/api_config.dart`.

| Entorno | URL base | Cuándo usar |
|---------|----------|-------------|
| `development` | `http://10.0.2.2:8080/api/v1` (Android emulator) | Backend local en tu PC (`mvn spring-boot:run`) |
| `production` | `https://hse.improvement-solution.com/api/v1` | Servidor VPS (usuarios reales) |

Cambia el entorno editando la constante `_env`:

```dart
static const Enviroment _env = Enviroment.development;  // local
static const Enviroment _env = Enviroment.production;   // servidor
```

> La app **no se conecta directo a PostgreSQL**. Siempre habla con el **backend Spring Boot**; la base de datos vive solo en el servidor.

---

## Producción — APK para instalar en el teléfono

### 1. Apuntar al servidor

En `lib/config/api_config.dart`:

```dart
static const Enviroment _env = Enviroment.production;
```

URL usada: `https://hse.improvement-solution.com/api/v1`

### 2. Generar el APK

```powershell
cd "D:\PROGRAMAS 2025 CONTRUCCION\Fiscalizacion HSE\app"
flutter pub get
flutter build apk --release
```

El archivo queda en:

```
app\build\app\outputs\flutter-apk\app-release.apk
```

### 3. Instalar en Android

1. Copia `app-release.apk` al teléfono (USB, Drive, WhatsApp, etc.).
2. En el teléfono: **Ajustes → Seguridad → Instalar apps desconocidas** (activar para el gestor de archivos o navegador que uses).
3. Abre el APK e instala **Fiscalización HSE**.
4. Inicia sesión con el **mismo usuario y contraseña** que en la web.

### 4. Requisitos

- El teléfono debe tener **internet**.
- El dominio debe responder en HTTPS: abre `https://hse.improvement-solution.com` en el navegador del móvil antes de probar la app.

### 5. Volver a desarrollo local

```dart
static const Enviroment _env = Enviroment.development;
```

Luego `flutter run` contra el backend local (`localhost:8080` / emulador `10.0.2.2:8080`).

---

## Referencia rápida de endpoints

Los paths en `api_config.dart` siguen la convención del backend: context-path `/api/v1` + controllers en `/api/...` (auth es la excepción en `/auth/...`).
