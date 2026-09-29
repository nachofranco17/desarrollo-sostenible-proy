param(
    [Parameter(Mandatory = $true)][string]$Empresa,
    [Parameter(Mandatory = $true)][string]$Correo,
    [Parameter(Mandatory = $true)][string]$Nombre,
    [Parameter(Mandatory = $true)][string]$Apellido,
    [ValidateSet('dev', 'postgres')][string]$Perfil = 'dev'
)
$ErrorActionPreference = 'Stop'
$backendDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../backend'))
$jarPath = Join-Path $backendDir 'target/xperience.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Primero ejecutá mvn package en backend.' }
$secret = Read-Host 'Contraseña inicial del Administrador (12 a 128 caracteres)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
$keys = @('COMPANY_NAME', 'ADMIN_EMAIL', 'ADMIN_NAME', 'ADMIN_SURNAME', 'ADMIN_PASSWORD', 'PROVISION_ACTOR')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    $env:COMPANY_NAME = $Empresa
    $env:ADMIN_EMAIL = $Correo
    $env:ADMIN_NAME = $Nombre
    $env:ADMIN_SURNAME = $Apellido
    $env:ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:PROVISION_ACTOR = [Environment]::UserName
    $profiles = if ($Perfil -eq 'dev') { 'dev,provision' } else { 'provision' }
    Push-Location $backendDir
    try {
        & java -jar $jarPath "--spring.profiles.active=$profiles" '--spring.main.web-application-type=none'
        if ($LASTEXITCODE -ne 0) { throw 'El alta no se completó.' }
    } finally { Pop-Location }
} finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secret.Dispose()
}
