@echo off
setlocal
set JCC_HOME=%~dp0
if "%JCC_HOME:~-1%"=="\" set JCC_HOME=%JCC_HOME:~0,-1%

if not exist "%JCC_HOME%\out" mkdir "%JCC_HOME%\out"

javac -source 8 -target 8 ^
    -sourcepath "%JCC_HOME%\src" ^
    -d "%JCC_HOME%\out" ^
    "%JCC_HOME%\src\com\jcc\Main.java"

if %ERRORLEVEL% EQU 0 (
    echo Build successful.
) else (
    echo Build FAILED.
    exit /b %ERRORLEVEL%
)
endlocal
