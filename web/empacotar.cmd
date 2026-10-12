@echo off
rem Gera o executavel do Dignitas Web para Windows (app\target\dist).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\packaging\empacotar.ps1" %*
