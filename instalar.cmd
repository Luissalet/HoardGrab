@echo off
rem Builds Hoard Grab and installs it on the phone plugged in by USB (USB debugging on).
setlocal
cd /d "%~dp0"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
if not exist local.properties (echo sdk.dir=%SDK:\=/%)> local.properties

echo Compilando Hoard Grab...
call gradlew.bat assembleRelease --console=plain -q
if errorlevel 1 goto fail

set "APK=%~dp0app\build\outputs\apk\release\app-arm64-v8a-release.apk"
echo Esperando al movil por USB...
"%ADB%" wait-for-usb-device
echo Instalando...
"%ADB%" -d install -r "%APK%"
if errorlevel 1 goto fail
"%ADB%" -d shell am start -n com.luissalet.hoardgrab/.MainActivity >nul
echo Hoard Grab instalado y abierto en el movil.
timeout /t 5 >nul
exit /b 0

:fail
echo.
echo No se ha podido instalar. Comprueba que el movil esta desbloqueado, con la depuracion USB activada y que aceptaste el aviso de este PC.
pause
exit /b 1
