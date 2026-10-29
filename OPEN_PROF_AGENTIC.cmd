@echo off
setlocal
cd /d "%~dp0"
set PYTHONDONTWRITEBYTECODE=1
.venv-agentic\Scripts\python.exe -B -m agentic_mobile.run_desktop
pause
