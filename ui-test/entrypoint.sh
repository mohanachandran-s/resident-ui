#!/bin/bash
set -e

java --version

JAR_PATH="$(ls target/*-jar-with-dependencies.jar 2>/dev/null | head -n1)"
if [ -z "$JAR_PATH" ]; then
    echo "No executable jar found under target/ — was the image built with 'mvn package'?" >&2
    exit 1
fi

# exec: inherits Kubernetes' signals directly. Exit code is always 0 regardless of scenario
# pass/fail (ResidentTestRunner.main()) — the Extent report is the result, not this exit code.
exec java -jar "$JAR_PATH"
