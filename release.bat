@echo off
setlocal
rem Tempest release: build, commit, push, then tag v<mod_version> so GitHub publishes it.
rem Usage: double-click, or  release.bat "what changed"
cd /d "%~dp0"
set "PATH=%PATH%;C:\Gradle\gradle-9.2.0\bin"
for /f "tokens=2 delims==" %%v in ('findstr /b "mod_version=" gradle.properties') do set "VER=%%v"
if "%VER%"=="" ( echo Could not read mod_version from gradle.properties & goto :fail )
set "MSG=%~1"
if "%MSG%"=="" set "MSG=Tempest %VER%"

git rev-parse -q --verify "refs/tags/v%VER%" >nul 2>&1
if not errorlevel 1 ( echo Tag v%VER% already exists. Bump mod_version in gradle.properties first. & goto :fail )

echo ==== Building Tempest %VER% ====
call gradle build
if errorlevel 1 ( echo BUILD FAILED & goto :fail )

echo ==== Committing ====
git add .
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "%MSG%"
  if errorlevel 1 ( echo COMMIT FAILED & goto :fail )
) else (
  echo Nothing new to commit.
)
rem Bring in anything changed on GitHub (e.g. a README edited on the website) before pushing.
git pull --rebase --autostash origin main
if errorlevel 1 (
  echo.
  echo PULL FAILED: GitHub has changes that clash with yours.
  echo Run  git status  to see the files, or  git rebase --abort  to undo the pull.
  goto :fail
)
git push
if errorlevel 1 ( echo PUSH FAILED & goto :fail )

echo ==== Tagging v%VER% ====
git tag "v%VER%"
if errorlevel 1 ( echo TAG FAILED & goto :fail )
git push origin "v%VER%"
if errorlevel 1 ( echo TAG PUSH FAILED & goto :fail )

echo.
echo DONE: v%VER% pushed. GitHub builds the release in about 3-5 minutes.
pause
exit /b 0

:fail
echo.
echo Stopped. Nothing was tagged, so no release went out.
pause
exit /b 1
