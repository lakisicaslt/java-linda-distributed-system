@echo off
setlocal
pushd "%~dp0"
java -cp "lib\*" rs.ac.bg.etf.kdp.server.ServerGuiMain
if errorlevel 1 pause
popd
