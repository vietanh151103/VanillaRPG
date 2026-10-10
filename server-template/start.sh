#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
PAPER_VERSION="26.2"
PAPER_BUILD="132"
PAPER_JAR="paper.jar"
PAPER_URL="https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds/${PAPER_BUILD}/downloads/paper-${PAPER_VERSION}-${PAPER_BUILD}.jar"
JAVA_CMD="java"
if [ -x "./runtime/bin/java" ]; then
  JAVA_CMD="./runtime/bin/java"
fi
if [ ! -f "$PAPER_JAR" ]; then
  echo "Downloading Paper ${PAPER_VERSION} build ${PAPER_BUILD} for the first start..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "${PAPER_JAR}.part" "$PAPER_URL"
  else
    echo "[ERROR] curl is required to download Paper automatically. Install curl and retry."
    exit 1
  fi
  mv "${PAPER_JAR}.part" "$PAPER_JAR"
fi
exec "$JAVA_CMD" -Xms2G -Xmx6G -jar "$PAPER_JAR" --nogui
