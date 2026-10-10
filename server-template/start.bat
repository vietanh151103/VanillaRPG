@echo off
setlocal
cd /d "%~dp0"

set "PAPER_VERSION=26.2"
set "PAPER_BUILD=132"
set "PAPER_JAR=paper.jar"
set "PAPER_URL=https://api.papermc.io/v2/projects/paper/versions/%PAPER_VERSION%/builds/%PAPER_BUILD%/downloads/paper-%PAPER_VERSION%-%PAPER_BUILD%.jar"

set "JAVA_CMD=java"
if exist "runtime\bin\java.exe" set "JAVA_CMD=%CD%\runtime\bin\java.exe"

if not exist "%PAPER_JAR%" (
  echo Downloading Paper %PAPER_VERSION% build %PAPER_BUILD% for the first start...
  where curl.exe >nul 2>nul
  if not errorlevel 1 (
    curl.exe -fL --retry 3 --output "%PAPER_JAR%.part" "%PAPER_URL%"
  ) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%PAPER_URL%' -OutFile '%PAPER_JAR%.part'"
  )
  if errorlevel 1 (
    echo [ERROR] Paper download failed. Check your Internet connection and try again.
    if exist "%PAPER_JAR%.part" del /q "%PAPER_JAR%.part"
    pause
    exit /b 1
  )
  move /y "%PAPER_JAR%.part" "%PAPER_JAR%" >nul
)

"%JAVA_CMD%" -Xms2G -Xmx6G -jar "%PAPER_JAR%" --nogui
pause
