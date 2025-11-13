@echo off
REM Run the server for the Remote Classroom Polling System

echo ========================================
echo  Starting Server
echo ========================================
echo.

if not exist "out\server\MainServer.class" (
    echo [ERROR] Server not compiled. Run compile.bat first!
    pause
    exit /b 1
)

echo Server starting on:
echo   - TCP Port: 8088 (for students)
echo   - HTTP Port: 8090 (for dashboard)
echo.
echo Dashboard URL: http://localhost:8090/
echo.
echo Instructor commands:
echo   newpoll ^<question^> ^| ^<A;B;C;D^> ^| ^<correct^>
echo   startpoll
echo   endpoll
echo   reveal
echo   status
echo   exit
echo.

java -cp out server.MainServer

pause