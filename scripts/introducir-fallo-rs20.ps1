# Experimento RS20. Solo modifica una rama de prueba limpia; no hace commit ni push.
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $raiz
try {
    $rama = ((& git branch --show-current) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or !$rama.StartsWith('experimento/rs20-')) {
        throw 'Ejecutar solo en una rama nueva cuyo nombre comience con experimento/rs20-.'
    }
    $estado = @(& git status --porcelain)
    if ($LASTEXITCODE -ne 0 -or $estado.Count -ne 0) {
        throw 'La rama de prueba debe estar limpia antes del experimento.'
    }
    $ruta = Join-Path $raiz 'backend/src/main/java/uy/edu/um/xperience/security/AuthorizationService.java'
    $texto = [IO.File]::ReadAllText($ruta)
    $original = 'case "ORG" -> a != null && s.empresaId() != null && s.empresaId().equals(a.companyId());'
    $fallo = 'case "ORG" -> a != null && s.empresaId() != null; // RS20: fallo intencional'
    if ([regex]::Matches($texto, [regex]::Escape($original)).Count -ne 1) {
        throw 'No se encontro exactamente una regla ORG conocida; no se modifico el archivo.'
    }
    [IO.File]::WriteAllText($ruta, $texto.Replace($original, $fallo), [Text.UTF8Encoding]::new($false))
    Write-Host "Fallo intencional introducido en $rama."
    Write-Host 'Se retiro la comparacion entre la empresa de la sesion y la del recurso.'
    Write-Host 'La suite debe rechazar este cambio mediante fallos de asercion.'
} finally {
    Pop-Location
}
