@echo off
rem Publica na internet o Dignitas que esta rodando neste computador (tunel Cloudflare).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\packaging\publicar.ps1" %*
