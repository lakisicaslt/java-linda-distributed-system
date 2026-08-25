@echo off
setlocal
pushd "%~dp0"
java -cp "lib\*" rs.ac.bg.etf.kdp.worker.WorkerGuiMain
if errorlevel 1 pause
popd
