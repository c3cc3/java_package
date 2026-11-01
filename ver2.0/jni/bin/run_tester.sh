#!/bin/bash
set -euo pipefail
APP_HOME="/ums24/wiseu/fq/project_src/jni"
JAVA="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
CP="${APP_HOME}:${APP_HOME}/java:${APP_HOME}/lib/*"
[ "$#" -le 1 ] || { echo "Usage: $0 [config-file]" >&2; exit 2; }
if [ "$#" -eq 1 ]; then [ -r "$1" ] || { echo "Cannot read $1" >&2; exit 1; }; fi
exec "$JAVA" -Djava.library.path="$APP_HOME/lib" -cp "$CP" FQueueTester "$@"
