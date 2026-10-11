@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "WORLD_NAME=vanillarpg_terraform_test"
set "PACK_SOURCE=%SCRIPT_DIR%worldgen-datapacks"
set "PACK_TARGET=%SCRIPT_DIR%%WORLD_NAME%\datapacks"

if not exist "%PACK_SOURCE%\Amplified_Nether_v1.2.15.zip" (
  echo ERROR: Amplified Nether source pack is missing.
  exit /b 1
)

if not exist "%PACK_SOURCE%\Nullscape_26.2_v1.2.20.zip" (
  echo ERROR: Nullscape source pack is missing.
  exit /b 1
)

if not exist "%PACK_TARGET%" mkdir "%PACK_TARGET%"

copy /Y "%PACK_SOURCE%\Amplified_Nether_v1.2.15.zip" "%PACK_TARGET%\Amplified_Nether_v1.2.15.zip" >nul
copy /Y "%PACK_SOURCE%\Nullscape_26.2_v1.2.20.zip" "%PACK_TARGET%\Nullscape_26.2_v1.2.20.zip" >nul

echo Installed Amplified Nether and Nullscape for %WORLD_NAME%.
echo Start the server only after this script finishes.
