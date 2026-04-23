@echo off
setlocal
set JCC_HOME=%~dp0
if "%JCC_HOME:~-1%"=="\" set JCC_HOME=%JCC_HOME:~0,-1%
java -Djcc.home="%JCC_HOME%" -cp "%JCC_HOME%\out" com.jcc.Main %*
endlocal
