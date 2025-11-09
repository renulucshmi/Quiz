#!/usr/bin/env bash
# Run the server for the Remote Classroom Polling System

pause() {
  read -n1 -s -r -p "Press any key to continue..."
  echo
}

echo "========================================"
echo " Starting Server"
echo "========================================"
echo

if [ ! -f out/server/MainServer.class ]; then
  echo "[ERROR] Server not compiled. Run compile.sh first!"
  pause
  exit 1
fi

echo "Server starting on:"
echo "  - TCP Port: 8088 (for students)"
echo "  - HTTP Port: 8090 (for dashboard)"
echo
echo "Dashboard URL: http://localhost:8090/"
echo
echo "Instructor commands:"
echo "  newpoll <question> | <A;B;C;D> | <correct>"
echo "  startpoll"
echo "  endpoll"
echo "  reveal"
echo "  status"
echo "  exit"
echo

java -cp out server.MainServer

pause

