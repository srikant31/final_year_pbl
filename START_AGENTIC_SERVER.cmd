@echo off
setlocal
cd /d "%~dp0"
set PYTHONDONTWRITEBYTECODE=1
set TF_CPP_MIN_LOG_LEVEL=2
set OMP_NUM_THREADS=2
if not exist .venv-agentic\Scripts\python.exe (
  echo Run SETUP_AGENTIC.cmd first.
  pause
  exit /b 1
)
echo Keep this laptop connected to the internet. USB forwarding is configured separately.
".venv-agentic\Scripts\python.exe" -B -m agentic_mobile.run_server
pause
