#!/usr/bin/env bash
# Run a student client for the Remote Classroom Polling System

pause() {
  read -n1 -s -r -p "Press any key to continue..."
  echo
}

if [ ! -f out/client/StudentClient.class ]; then
  echo "[ERROR] Client not compiled. Run compile.sh first!"
  pause
  exit 1
fi

NAME="$1"
if [ -z "$NAME" ]; then
  echo "========================================"
  echo " Student Client"
  echo "========================================"
  echo
  read -p "Enter your name: " NAME
fi

echo
echo "Connecting as: $NAME"
echo

java -cp out client.StudentClient "$NAME"

pause

