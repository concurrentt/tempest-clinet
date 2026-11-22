@echo off
setlocal
rem Deploys the Tempest Worker to Cloudflare (tests first).
cd /d "%~dp0"

if not exist src\mojang-keys.json (
  echo ==== Downloading Mojang's public certificate keys ====
  curl -sS -o src\mojang-keys.json https://api.minecraftservices.com/publickeys
  if errorlevel 1 ( echo DOWNLOAD FAILED & goto :fail )
)
findstr /c:"playerCertificateKeys" src\mojang-keys.json >nul
if errorlevel 1 ( echo src\mojang-keys.json does not look right - delete it and run again. & goto :fail )

echo ==== Testing ====
node test.mjs
if errorlevel 1 ( echo TESTS FAILED & goto :fail )

echo ==== Deploying ====
call npx wrangler deploy
if errorlevel 1 ( echo DEPLOY FAILED & goto :fail )

echo.
echo DONE: Worker deployed.
pause
exit /b 0

:fail
echo.
echo Stopped.
pause
exit /b 1
