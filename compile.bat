@echo off
REM Compile all Java source files for the Remote Classroom Polling System

echo ========================================
echo  Compiling Remote Classroom Polling System
echo ========================================
echo.

REM Create output directory
if not exist "out" mkdir out

echo Compiling server classes...
javac -d out src\server\*.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Server compilation failed!
    pause
    exit /b 1
)

echo Compiling client classes...
javac -d out src\client\*.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Client compilation failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Compilation successful!
echo ========================================
echo.
echo Next steps:
echo   1. Run server: run-server.bat
echo   2. Run clients: run-client.bat [name]
echo   3. Open dashboard: http://localhost:8090/
echo.
pause