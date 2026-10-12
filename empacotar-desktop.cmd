@echo off
rem Gera o executavel da versao desktop (JavaFX) do Dignitas em target\dist-desktop.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0packaging\empacotar-desktop.ps1" %*
