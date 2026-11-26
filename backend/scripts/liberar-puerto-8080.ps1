# Solo cuando el arranque es "mvn spring-boot:run": cierra el backend anterior en 8080.
$maven = Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
$parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($maven.ParentProcessId)"
$linea = "$($parent.CommandLine)"
if ($linea -notmatch 'spring-boot:run') {
    exit 0
}

$ocupados = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique
foreach ($id in $ocupados) {
    if ($id -and $id -ne $PID -and $id -ne $parent.ProcessId) {
        Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    }
}
