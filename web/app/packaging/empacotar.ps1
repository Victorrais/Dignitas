# Gera o executável do Dignitas Web para Windows: pasta Dignitas\ com Dignitas.exe e Java embutido,
# e um ZIP pronto para distribuir. Não precisa de Java instalado no computador de destino.
# Uso: web\empacotar.cmd
$ErrorActionPreference = 'Stop'

$app = Split-Path -Parent $PSScriptRoot     # web\app
$web = Split-Path -Parent $app              # web
$alvo = Join-Path $app 'target'
$entrada = Join-Path $alvo 'jpackage-entrada'
$dist = Join-Path $alvo 'dist'

Write-Host '1/3 Compilando (core + app)...'
Push-Location $web
try {
    & (Join-Path $web 'mvnw.cmd') -B -q -DskipTests install
    if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação.' }
} finally {
    Pop-Location
}

$jar = Get-ChildItem $alvo -Filter 'dignitas-web-*.jar' | Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw "Jar não encontrado em $alvo" }
$versao = ($jar.BaseName -replace '^dignitas-web-', '')

Write-Host '2/3 Gerando o executável com jpackage...'
if (Test-Path $entrada) { Remove-Item $entrada -Recurse -Force }
New-Item -ItemType Directory -Path $entrada | Out-Null
Copy-Item $jar.FullName $entrada
if (Test-Path (Join-Path $dist 'Dignitas')) { Remove-Item (Join-Path $dist 'Dignitas') -Recurse -Force }

jpackage --type app-image `
    --name Dignitas `
    --app-version $versao `
    --vendor Dignitas `
    --description 'Dignitas - banco de questões' `
    --icon (Join-Path $PSScriptRoot 'dignitas.ico') `
    --input $entrada `
    --main-jar $jar.Name `
    --java-options '-Ddignitas.desktop=true' `
    --java-options '-Dfile.encoding=UTF-8' `
    --jlink-options '--strip-debug --no-man-pages --no-header-files' `
    --dest $dist
if ($LASTEXITCODE -ne 0) { throw 'Falha no jpackage.' }

Write-Host '3/3 Compactando...'
$zip = Join-Path $dist 'Dignitas-Web-Windows.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $dist 'Dignitas') -DestinationPath $zip

Write-Host ''
Write-Host "Executável: $(Join-Path $dist 'Dignitas\Dignitas.exe')"
Write-Host "ZIP:        $zip ($([math]::Round((Get-Item $zip).Length / 1MB)) MB)"
