@echo off
setlocal
cd /d "%~dp0"

set "JAVA_CMD=java"
if exist "runtime\bin\java.exe" set "JAVA_CMD=%CD%\runtime\bin\java.exe"

if not exist "paper.jar" (
  echo [ERROR] paper.jar not found.
  echo Download the latest stable Paper 26.2 build and rename it to paper.jar.
  pause
  exit /b 1
)

"%JAVA_CMD%" -Xms2G -Xmx6G -jar paper.jar --nogui
pause
