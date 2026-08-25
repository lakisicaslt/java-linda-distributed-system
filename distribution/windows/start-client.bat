@echo off
setlocal
pushd "%~dp0"
java -cp "lib\*" rs.ac.bg.etf.kdp.client.ClientGuiMain
if errorlevel 1 pause
popd
