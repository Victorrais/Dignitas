# Gera o executável da versão desktop (JavaFX) do Dignitas: pasta Dignitas\ com Dignitas.exe
# e um Java reduzido só com os módulos usados, mais um ZIP para distribuir.
# Uso: empacotar-desktop.cmd (na raiz do repositório)
$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $PSScriptRoot
$alvo = Join-Path $raiz 'target'
$entrada = Join-Path $alvo 'desktop-entrada'
$runtime = Join-Path $alvo 'desktop-runtime'
$dist = Join-Path $alvo 'dist-desktop'

# Módulos do Java: os detectados pelo jdeps mais os carregados por reflexão
# (XML do Hibernate, imagens/Swing do JavaFX, datas em português, charsets de PDF).
$modulos = @(
    'java.base', 'java.compiler', 'java.desktop', 'java.instrument', 'java.logging', 'java.management',
    'java.naming', 'java.net.http', 'java.rmi', 'java.scripting', 'java.security.sasl', 'java.sql',
    'java.xml', 'jdk.charsets', 'jdk.jfr', 'jdk.localedata', 'jdk.unsupported', 'jdk.unsupported.desktop',
    'jdk.zipfs'
) -join ','

Write-Host '1/4 Compilando a versão desktop...'
Push-Location $raiz
try {
    & (Join-Path $raiz 'mvnw.cmd') -B -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação.' }
} finally {
    Pop-Location
}
$jar = Get-ChildItem $alvo -Filter 'dignitas-*.jar' |
    Where-Object { $_.Name -notlike 'original-*' -and $_.Name -notlike '*-shaded.jar' } | Select-Object -First 1
if (-not $jar) { throw "Jar não encontrado em $alvo" }
$versao = ($jar.BaseName -replace '^dignitas-', '')

Write-Host '2/4 Montando o Java reduzido (jlink)...'
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
jlink --add-modules $modulos --include-locales 'en,pt' --strip-debug --no-header-files --no-man-pages `
    --compress zip-9 --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Falha no jlink.' }

Write-Host '3/4 Gerando o executável (jpackage)...'
if (Test-Path $entrada) { Remove-Item $entrada -Recurse -Force }
New-Item -ItemType Directory -Path $entrada | Out-Null
Copy-Item $jar.FullName $entrada
if (Test-Path (Join-Path $dist 'Dignitas')) { Remove-Item (Join-Path $dist 'Dignitas') -Recurse -Force }
jpackage --type app-image `
    --name Dignitas `
    --app-version $versao `
    --vendor Dignitas `
    --description 'Dignitas - banco de questões (desktop)' `
    --icon (Join-Path $PSScriptRoot 'dignitas.ico') `
    --runtime-image $runtime `
    --input $entrada `
    --main-jar $jar.Name `
    --main-class 'br.com.dignitas.app.Launcher' `
    --java-options '-Dfile.encoding=UTF-8' `
    --dest $dist
if ($LASTEXITCODE -ne 0) { throw 'Falha no jpackage.' }

Write-Host '4/4 Compactando...'
$zip = Join-Path $dist 'Dignitas-Desktop-Windows.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $dist 'Dignitas') -DestinationPath $zip

$pasta = (Get-ChildItem (Join-Path $dist 'Dignitas') -Recurse | Measure-Object Length -Sum).Sum
Write-Host ''
Write-Host "Executável: $(Join-Path $dist 'Dignitas\Dignitas.exe') (pasta com $([math]::Round($pasta / 1MB)) MB)"
Write-Host "ZIP:        $zip ($([math]::Round((Get-Item $zip).Length / 1MB)) MB)"
