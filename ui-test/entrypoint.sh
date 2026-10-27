#!/bin/bash
set -e

java --version

JAR_PATH="$(ls target/*-jar-with-dependencies.jar 2>/dev/null | head -n1)"
if [ -z "$JAR_PATH" ]; then
    echo "No executable jar found under target/ — was the image built with 'mvn package'?" >&2
    exit 1
fi

# A mounted config file (RESIDENT_CONFIG_PATH, default /home/mosip/config/resident.properties —
# unused by Rancher in production, which configures this module entirely via env vars, but
# documented for local/Docker runs) is read directly by ResidentConfigManager, not copied here,
# since that classpath resource is frozen inside the jar at build time.
#
# exec replaces this shell with the java process, so it receives Kubernetes' signals directly.
# Exit code propagates MainRunner's own (non-zero on any test failure), so Rancher sees a failed
# Job.
exec java -jar "$JAR_PATH"
