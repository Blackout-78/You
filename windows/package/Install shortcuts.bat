@echo off
rem Puts a You shortcut on your Desktop and in the Start menu.
rem Optional: You.exe also works by double-clicking it right here.
set "APPDIR=%~dp0"
set "APPDIR=%APPDIR:~0,-1%"
if not exist "%APPDIR%\You.exe" (
  echo  You.exe is not in this folder.
  pause
  exit /b 1
)
powershell -NoProfile -Command "$s=New-Object -ComObject WScript.Shell; foreach($p in @((Join-Path ([Environment]::GetFolderPath('Desktop')) 'You.lnk'),(Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\You.lnk'))){ $l=$s.CreateShortcut($p); $l.TargetPath='%APPDIR%\You.exe'; $l.WorkingDirectory='%APPDIR%'; $l.IconLocation='%APPDIR%\You.exe,0'; $l.Save() }"
if /i not "%~1"=="/quiet" (
  echo.
  echo  Done. You is now on your Desktop and in the Start menu.
  pause
)
