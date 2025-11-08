@echo off
REM Run a student client for the Remote Classroom Polling System

if not exist "out\client\StudentClient.class" (
    echo [ERROR] Client not compiled. Run compile.bat first!
    pause
    exit /b 1
)

REM Check if name provided as argument
if "%~1"=="" (
    REM No argument, prompt for name
    echo ========================================
    echo  Student Client
    echo ========================================
    echo.
    set /p NAME="Enter your name: "
) else (
    REM Use provided name
    set NAME=%~1
)

echo.
echo Connecting as: %NAME%
echo.

java -cp out client.StudentClient "%NAME%"

pause