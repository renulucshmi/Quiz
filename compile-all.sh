#!/usr/bin/env bash
# Compile server and client Java sources into `bin`

set -euo pipefail

pause() {
  read -n1 -s -r -p "Press any key to continue..."
  echo
}

echo "========================================"
echo " Compiling Remote Classroom Poll System"
echo "========================================"

echo
mkdir -p bin

echo
echo "[1/2] Compiling server files..."
javac -d bin src/server/*.java || {
  echo "ERROR: Server compilation failed!"
  pause
  exit 1
}
echo "[SUCCESS] Server compiled successfully!"

echo
echo "[2/2] Compiling client files..."
javac -d bin src/client/*.java || {
  echo "ERROR: Client compilation failed!"
  pause
  exit 1
}
echo "[SUCCESS] Client compiled successfully!"

echo

echo "========================================"
echo " Compilation Complete!"
echo "========================================"
echo
cat <<EOF
All classes compiled to 'bin' directory.
You can now run:
  - ./run-server.sh (to start the server)
  - ./run-client.sh (to start a student client)

EOF

pause

