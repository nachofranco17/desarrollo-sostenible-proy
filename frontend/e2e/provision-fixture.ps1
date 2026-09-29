$ErrorActionPreference = 'Stop'
# Test-only replacement for the secure interactive prompt; the real provisioning script runs unchanged.
function global:Read-Host {
    param([string]$Prompt, [switch]$AsSecureString)
    ConvertTo-SecureString $env:E2E_ADMIN_PASSWORD -AsPlainText -Force
}
& (Join-Path $PSScriptRoot '../../scripts/crear-empresa.ps1') `
    -Empresa 'Empresa E2E' -Correo $env:E2E_ADMIN_EMAIL -Nombre 'Admin' -Apellido 'Prueba'
