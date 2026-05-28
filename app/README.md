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

`lib/config/api_config.dart` — emulador Android: `http://10.0.2.2:8080/api/v1` (equivale al `localhost` de tu PC).
