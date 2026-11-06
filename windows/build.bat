@echo off
cd /d "%~dp0"
set PY=python
py -3.13 --version >nul 2>&1 && set PY=py -3.13

echo Step 1 of 2: installing the build tool...
%PY% -m pip install pyinstaller PySide6 mutagen
if errorlevel 1 goto failed

echo.
echo Step 2 of 2: building You.exe (this takes a few minutes)...
%PY% -m PyInstaller --noconfirm --clean --noconsole --onefile --name You --icon assets\icon.ico --add-data "assets;assets" main.py
if errorlevel 1 goto failed

echo.
echo Done. Your program is here: dist\You.exe
pause
exit /b 0

:failed
echo.
echo Something went wrong. Copy the text above and send it to Claude.
pause
exit /b 1
