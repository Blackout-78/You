@echo off
rem You uninstaller.
rem Removes the You program folder and its shortcuts. It never touches your music, movies or shows.
if /i "%~1"=="--run" goto run

rem Run from a temporary copy so this folder is free to be deleted.
set "APPDIR=%~dp0"
set "APPDIR=%APPDIR:~0,-1%"
set "TMPBAT=%TEMP%\you_uninstall_%RANDOM%.bat"
copy /y "%~f0" "%TMPBAT%" >nul
call "%TMPBAT%" --run "%APPDIR%" %1 & exit /b

:run
set "APPDIR=%~2"
set "QUIET=%~3"
set "KEEP=1"
title Uninstall You
if /i "%QUIET%"=="/quiet" echo run started for %APPDIR% > "%TEMP%\you_uninstall.log"
if /i not "%QUIET%"=="/quiet" (
  echo.
  echo  This will remove You from this PC.
  echo  Your music, movies and TV shows are NOT touched.
  echo.
  choice /c YN /m " Remove You"
  if errorlevel 2 exit /b
  echo.
  echo  The program also saved your library list, favorites, playlists, descriptions and settings.
  choice /c YN /m " Delete those too"
  if not errorlevel 2 set "KEEP=0"
)

taskkill /im You.exe /f >nul 2>&1
ping -n 3 127.0.0.1 >nul

for /f "delims=" %%D in ('powershell -NoProfile -Command "[Environment]::GetFolderPath('Desktop')"') do set "DESK=%%D"
if exist "%DESK%\You.lnk" del /q "%DESK%\You.lnk"
if exist "%APPDATA%\Microsoft\Windows\Start Menu\Programs\You.lnk" del /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\You.lnk"

if "%KEEP%"=="0" if exist "%APPDATA%\You" rmdir /s /q "%APPDATA%\You"

cd /d "%TEMP%"
if exist "%APPDIR%" rmdir /s /q "%APPDIR%"

if /i not "%QUIET%"=="/quiet" (
  echo.
  echo  You has been removed.
  echo.
  pause
)
rem Delete this temporary copy of the uninstaller.
(goto) 2>nul & del /q "%~f0"
