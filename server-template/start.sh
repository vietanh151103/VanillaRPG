#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
JAVA_CMD="java"
if [ -x "./runtime/bin/java" ]; then
  JAVA_CMD="./runtime/bin/java"
fi
if [ ! -f "paper.jar" ]; then
  echo "[ERROR] paper.jar not found. Download the latest stable Paper 26.2 build and rename it to paper.jar."
  exit 1
fi
exec "$JAVA_CMD" -Xms2G -Xmx6G -jar paper.jar --nogui
