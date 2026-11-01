#!/bin/bash
set -euo pipefail
APP_HOME="/ums24/wiseu/fq/project_src/jni"
JAVAC="${JAVA_HOME:+${JAVA_HOME}/bin/}javac"
CP="${APP_HOME}:${APP_HOME}/java:${APP_HOME}/lib/*"
for src in "$APP_HOME/java/FQueue.java" "$APP_HOME/java/FQueueTester.java" "$APP_HOME/java/com/clang/socket/FileQueueSocket.java"; do
  [ -r "$src" ] || { echo "Missing: $src" >&2; exit 1; }
done
"$JAVAC" -encoding UTF-8 -cp "$CP" -d "$APP_HOME/java" \
  "$APP_HOME/java/FQueue.java" \
  "$APP_HOME/java/com/clang/socket/FileQueueSocket.java" \
  "$APP_HOME/java/FQueueTester.java"
echo 'FQueueTester Step 2 build complete.'
