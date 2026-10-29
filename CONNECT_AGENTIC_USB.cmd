@echo off
setlocal
if exist D:\Android\Sdk\platform-tools\adb.exe (
  D:\Android\Sdk\platform-tools\adb.exe -d reverse tcp:8001 tcp:8001
) else (
  adb -d reverse tcp:8001 tcp:8001
)
if errorlevel 1 (
  echo Unlock the phone and approve USB debugging. Only one physical phone should be connected.
) else (
  echo Phone connected to the laptop Agentic server. Keep the USB cable connected.
)
pause
