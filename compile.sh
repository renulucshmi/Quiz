#!/usr/bin/env bash
# Compile all Java source files for the Remote Classroom Polling System

set -euo pipefail

pause() {
  read -n1 -s -r -p "Press any key to continue..."
  echo
}

echo "========================================"
echo " Compiling Remote Classroom Polling System"
echo "========================================"
echo

mkdir -p out

echo "Compiling server classes..."
javac -d out src/server/*.java || {
  echo
  echo "[ERROR] Server compilation failed!"
  pause
  exit 1
}

echo "Compiling client classes..."
javac -d out src/client/*.java || {
  echo
  echo "[ERROR] Client compilation failed!"
  pause
  exit 1
}

echo

echo "========================================"
echo " Compilation successful!"
echo "========================================"
echo
cat <<EOF
Next steps:
  1. Run server: ./run-server.sh
  2. Run clients: ./run-client.sh [name]
  3. Open dashboard: http://localhost:8090/

EOF

pause

