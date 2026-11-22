@echo off
rem Shows live what the Tempest Worker is doing. Close this window to stop.
cd /d "%~dp0"
call npx wrangler tail
pause
