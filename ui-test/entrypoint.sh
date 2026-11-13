#!/bin/bash
set -e

java --version

JAR_PATH="$(ls target/*-jar-with-dependencies.jar 2>/dev/null | head -n1)"
if [ -z "$JAR_PATH" ]; then
    echo "No executable jar found under target/ — was the image built with 'mvn package'?" >&2
    exit 1
fi

# RESIDENT_CONFIG_PATH (local/Docker only) is read directly by ResidentConfigManager, not copied
# here — that classpath resource is frozen inside the jar at build time.
# exec: this process inherits Kubernetes' signals directly, and its exit code is MainRunner's own.
exec java -jar "$JAR_PATH"
