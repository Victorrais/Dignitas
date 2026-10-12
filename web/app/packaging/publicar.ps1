# Publica na internet o Dignitas Web que está rodando neste computador, com um túnel rápido do
# Cloudflare. Gera um endereço https://....trycloudflare.com, que funciona enquanto esta janela
# estiver aberta. Quem usa o Dignitas.exe pode publicar pelo ícone da bandeja, sem este script.
# Uso: web\publicar.cmd   (com o Dignitas já aberto)
$ErrorActionPreference = 'Stop'
$porta = if ($env:DIGNITAS_PORTA) { $env:DIGNITAS_PORTA } else { 8080 }

$cloudflared = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cloudflared) {
    $candidatos = @(
        "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe",
        "$env:ProgramFiles\cloudflared\cloudflared.exe",
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links\cloudflared.exe",
        "$env:USERPROFILE\dignitas\cloudflared.exe")
    $cloudflared = $candidatos | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $cloudflared) {
    Write-Host 'O cloudflared não está instalado. Instale com:'
    Write-Host '    winget install --id Cloudflare.cloudflared'
    Write-Host 'e execute este script de novo.'
    exit 1
}

try {
    $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://127.0.0.1:$porta/login.html"
    if ($r.Content -notmatch 'Dignitas') { throw 'resposta inesperada' }
} catch {
    Write-Host "O Dignitas não está respondendo em http://127.0.0.1:$porta."
    Write-Host 'Abra o Dignitas (Dignitas.exe ou mvnw.cmd -pl app spring-boot:run) e tente de novo.'
    exit 1
}

Write-Host 'Publicando... o endereço público aparece abaixo (linha com trycloudflare.com).'
Write-Host 'Para encerrar a publicação, feche esta janela ou pressione Ctrl+C.'
Write-Host ''
& $cloudflared tunnel --no-autoupdate --url "http://127.0.0.1:$porta"
