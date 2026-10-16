param(
    [string]$Administrador = 'registro_auditor'
)
# RS19: crea el almacén de registros de seguridad fuera del perfil dev.
# El administrador (auditoría) queda sólo en manos de quien ejecuta este script; la aplicación recibe
# únicamente SECURITY_LOG_WRITER_PASSWORD, que sólo permite agregar eventos.
$ErrorActionPreference = 'Stop'
$backendDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../backend'))
$jarPath = Join-Path $backendDir 'target/xperience.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Primero ejecutá mvn package en backend.' }
$admin = Read-Host 'Contraseña del administrador del almacén (auditoría)' -AsSecureString
$writer = Read-Host 'Contraseña del escritor (la que recibirá la aplicación)' -AsSecureString
$adminPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($admin)
$writerPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($writer)
$keys = @('SECURITY_LOG_ADMIN_USER', 'SECURITY_LOG_ADMIN_PASSWORD', 'SECURITY_LOG_WRITER_PASSWORD')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    $env:SECURITY_LOG_ADMIN_USER = $Administrador
    $env:SECURITY_LOG_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPointer)
    $env:SECURITY_LOG_WRITER_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($writerPointer)
    Push-Location $backendDir
    try {
        & java -jar $jarPath '--spring.profiles.active=provision-registro' '--spring.main.web-application-type=none'
        if ($LASTEXITCODE -ne 0) { throw 'El almacén no quedó preparado.' }
    } finally { Pop-Location }
    Write-Host 'Iniciá el backend con SECURITY_LOG_WRITER_PASSWORD (y nunca con la contraseña del administrador).'
} finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPointer)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($writerPointer)
    $admin.Dispose()
    $writer.Dispose()
}
