param(
    [string]$Administrador = 'registro_auditor',
    [int]$Dias = 365
)
# RS19: aplica la política de retención del almacén de registros de seguridad.
# Borra solo los eventos con más de $Dias días (mínimo 365). Lo ejecuta el administrador del almacén;
# la aplicación no puede borrar eventos.
$ErrorActionPreference = 'Stop'
$backendDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../backend'))
$jarPath = Join-Path $backendDir 'target/xperience.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Primero ejecutá mvn package en backend.' }
$admin = Read-Host 'Contraseña del administrador del almacén (auditoría)' -AsSecureString
$adminPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($admin)
$keys = @('SECURITY_LOG_ADMIN_USER', 'SECURITY_LOG_ADMIN_PASSWORD')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    $env:SECURITY_LOG_ADMIN_USER = $Administrador
    $env:SECURITY_LOG_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPointer)
    Push-Location $backendDir
    try {
        & java -jar $jarPath '--spring.profiles.active=depurar-registro' '--spring.main.web-application-type=none' "--app.security-log.retention-days=$Dias"
        if ($LASTEXITCODE -ne 0) { throw 'No se depuró el almacén.' }
    } finally { Pop-Location }
} finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPointer)
    $admin.Dispose()
}
