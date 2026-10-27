param(
    [Parameter(Mandatory = $true)][string]$Analizador,
    [Parameter(Mandatory = $true)][string]$NvdApiKey,
    [switch]$SinCentral,
    [string]$Salida = ''
)

$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ejecutable = (Resolve-Path -LiteralPath $Analizador).Path
$jar = Join-Path $raiz 'backend/target/xperience.jar'
$lock = Join-Path $raiz 'frontend/package-lock.json'
if (!(Test-Path -LiteralPath $jar)) { throw 'Primero compila el backend con mvnw.cmd clean verify.' }
if (!(Test-Path -LiteralPath $lock) -or !(Test-Path (Join-Path $raiz 'frontend/node_modules'))) {
    throw 'Faltan el lockfile o las dependencias instaladas del frontend. Ejecuta npm.cmd ci en frontend.'
}
$revision = (& git -C $raiz rev-parse --short HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'No se pudo identificar el commit del proyecto.' }
$cambios = (& git -C $raiz status --porcelain | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar el estado de Git.' }
$etiqueta = if ($cambios) { "${revision}-trabajo" } else { $revision }
if (!$Salida) {
    $nombre = 'dependency-check-sprint2-' + $etiqueta + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    $Salida = Join-Path (Join-Path $env:USERPROFILE 'Downloads') $nombre
}
$destino = (New-Item -ItemType Directory -Path $Salida -Force).FullName

# Se analiza la aplicacion, con su JAR y los paquetes npm de desarrollo incluidos.
# La cache historica de herramientas no es el inventario del artefacto desplegable.
# El informe amplio original se conserva por separado: no se suprimen sus CVE.
$opciones = @(
    '--project', "XPerience - Sprint 2 - $etiqueta", '--scan', $raiz,
    '--format', 'HTML', '--format', 'JSON', '--out', $destino,
    '--exclude', '**/.git/**', '--exclude', '**/.data/**',
    '--exclude', '**/.tools/**', '--exclude', '**/.vite/**',
    '--exclude', '**/test-results/**', '--exclude', '**/playwright-report/**',
    '--log', (Join-Path $destino 'dependency-check.log'),
    '--disableAssembly', '--nvdApiKey', $NvdApiKey
)
if ($SinCentral) {
    Write-Warning 'Central Analyzer omitido por demoras de la consulta remota. Puede reducir la identificacion de bibliotecas; revisar el inventario y registrar esta limitacion. NVD, Jar, Node Audit y RetireJS siguen habilitados.'
    $opciones += '--disableCentral'
}
& $ejecutable @opciones
$codigo = $LASTEXITCODE
Write-Host "Codigo de salida: $codigo"
Write-Host "Informes en: $destino"
if ($codigo -ne 0) { throw "Dependency-Check termino con codigo $codigo. Revisa el log antes de dar el analisis por completo." }

$informe = Get-Content -LiteralPath (Join-Path $destino 'dependency-check-report.json') -Raw | ConvertFrom-Json
$contexto = [ordered]@{
    fecha = (Get-Date).ToString('o')
    commitBase = $revision
    arbolLimpio = !$cambios
    estadoGit = $cambios
    motor = $informe.scanInfo.engineVersion
    centralAnalyzerHabilitado = !$SinCentral
    jarSha256 = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    pomSha256 = (Get-FileHash -LiteralPath (Join-Path $raiz 'backend/pom.xml') -Algorithm SHA256).Hash
    packageLockSha256 = (Get-FileHash -LiteralPath $lock -Algorithm SHA256).Hash
    exclusiones = @('.git', '.data', '.tools', '.vite', 'test-results', 'playwright-report')
    alcance = 'Aplicacion empaquetada y frontend instalado, incluidos devDependencies; herramientas y caches historicas se revisan en el informe amplio original.'
}
$contexto | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destino 'contexto-analisis.json') -Encoding UTF8
$afectadas = @($informe.dependencies | Where-Object { $_.vulnerabilities })
$alertas = @($afectadas | ForEach-Object { $_.vulnerabilities } | Select-Object -ExpandProperty name -Unique)
Write-Host "Componentes con alertas: $($afectadas.Count)"
Write-Host "Identificadores de vulnerabilidad distintos: $($alertas.Count)"
Write-Host 'El resultado requiere revisar hallazgos y limitaciones; salida 0 no significa ausencia de vulnerabilidades.'
