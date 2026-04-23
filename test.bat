@echo off
setlocal enabledelayedexpansion
set JCC_HOME=%~dp0
if "%JCC_HOME:~-1%"=="\" set JCC_HOME=%JCC_HOME:~0,-1%
set PASS=0
set FAIL=0

call :test "HelloWorld" "Hello, World!"

echo.
echo Results: !PASS! passed, !FAIL! failed
if !FAIL! GTR 0 (exit /b 1) else (exit /b 0)

:: run_test <name> <expected-output>
:test
set _N=%~1
set _E=%~2
call "%JCC_HOME%\jcc.bat" "%JCC_HOME%\examples\%_N%.c" -o "%TEMP%\jcc_%_N%" >nul 2>&1
if errorlevel 1 (
    echo FAIL  %_N%  [compile error]
    set /a FAIL+=1
    goto :eof
)
"%TEMP%\jcc_%_N%.exe" | findstr /c:"%_E%" >nul 2>&1
if errorlevel 1 (
    echo FAIL  %_N%
    set /a FAIL+=1
) else (
    echo PASS  %_N%
    set /a PASS+=1
)
goto :eof
