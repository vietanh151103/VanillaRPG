@echo off
setlocal
cd /d "%~dp0plugin"

echo Building VanillaRPG-Core 0.7.1 for Paper 26.2 / Java 25...
gradle clean build
if errorlevel 1 (
  echo.
  echo [ERROR] Build failed. Copy the output and send it back for troubleshooting.
  pause
  exit /b 1
)

echo.
echo Build complete:
echo %CD%\build\libs\VanillaRPG-Core-0.7.1.jar
pause
