@echo off
echo ========================================
echo  Starting Poll Server
echo ========================================
echo.
echo TCP Server: Port 8088
echo HTTP Server: Port 8090
echo Web UI: http://localhost:8090/index.html
echo.
java -cp bin server.MainServer
pause
