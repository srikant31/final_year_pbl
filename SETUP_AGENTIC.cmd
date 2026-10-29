@echo off
setlocal
cd /d "%~dp0"
if not exist .venv-agentic\Scripts\python.exe py -3.12 -m venv .venv-agentic
if errorlevel 1 exit /b 1
.venv-agentic\Scripts\python.exe -m pip install -r agentic_mobile\requirements.txt
pause
