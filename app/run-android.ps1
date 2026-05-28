# Ejecutar app en emulador Android (Small_Phone u otro)
# Uso: .\run-android.ps1
#      .\run-android.ps1 -Wipe   # borra datos del AVD y libera espacio

param([switch]$Wipe)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$emu = "$sdk\emulator\emulator.exe"
$avd = "Small_Phone"

if ($Wipe) {
    Write-Host "Cerrando emulador y borrando datos del AVD '$avd' (libera espacio)..." -ForegroundColor Yellow
    if (Test-Path $adb) {
        & $adb emu kill 2>$null
        Start-Sleep -Seconds 3
    }
    if (Test-Path $emu) {
        Start-Process -FilePath $emu -ArgumentList "-avd", $avd, "-wipe-data" -WindowStyle Normal
        Write-Host "Espera a que el emulador termine de arrancar, luego ejecuta de nuevo sin -Wipe."
        exit 0
    }
    Write-Host "No se encontró el emulador en $emu" -ForegroundColor Red
    exit 1
}

if (Test-Path $adb) {
    $df = & $adb shell df -h /data 2>$null | Select-String "dm-"
    if ($df) { Write-Host "Espacio en emulador: $df" }
    & $adb uninstall com.fiscalizacionhse.fiscalizacion_hse_mobile 2>$null | Out-Null
}

# Solo arquitectura del emulador x64 → APK más pequeño (~30-40 MB vs ~70 MB universal)
flutter run --target-platform android-x64
