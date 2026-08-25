@echo off
setlocal
pushd "%~dp0"

set "SERVER_HOST=%~1"
if not defined SERVER_HOST set "SERVER_HOST=127.0.0.1"

set "SERVER_PORT=%~2"
if not defined SERVER_PORT set "SERVER_PORT=5050"

set "WORKER_CAPACITY=%~3"
if not defined WORKER_CAPACITY set "WORKER_CAPACITY=%NUMBER_OF_PROCESSORS%"

java -cp "lib\*" rs.ac.bg.etf.kdp.worker.WorkerMain "%SERVER_HOST%" "%SERVER_PORT%" "%WORKER_CAPACITY%" "%CD%\kdp-linda.jar"
if errorlevel 1 pause
popd
