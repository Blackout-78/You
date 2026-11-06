@echo off
set PY=python
py -3.13 --version >nul 2>&1 && set PY=py -3.13
%PY% main.py %*
if errorlevel 1 pause
