@echo off
echo ========================================
echo  Compiling Remote Classroom Poll System
echo ========================================

if not exist "bin" mkdir bin

echo.
echo [1/2] Compiling server files...
javac -d bin -cp "src" src/server/*.java

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Server compilation failed!
    pause
    exit /b 1
)

echo [SUCCESS] Server compiled successfully!

echo.
echo [2/2] Compiling client files...
javac -d bin -cp "src" src/client/*.java

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Client compilation failed!
    pause
    exit /b 1
)

echo [SUCCESS] Client compiled successfully!

echo.
echo ========================================
echo  Compilation Complete!
echo ========================================
echo.
echo All classes compiled to 'bin' directory.
echo You can now run:
echo   - run-server.bat (to start the server)
echo   - run-client.bat (to start a student client)
echo.
pause
