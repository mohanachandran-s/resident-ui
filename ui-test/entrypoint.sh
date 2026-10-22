#!/bin/bash
set -e

java --version
mvn --version

# The image only ever bakes in resident.properties' tracked, blank/default values (never real
# secrets/URLs — see that file's own header). Rancher mounts the real one (URLs, secrets, UIN/VID
# test data) as a ConfigMap/Secret file instead; drop it where Maven's own resource handling
# already expects it, so `mvn test` picks it up exactly like a local run reading
# src/test/resources/config/resident.properties.
RESIDENT_CONFIG_PATH="${RESIDENT_CONFIG_PATH:-/home/mosip/config/resident.properties}"
if [ -f "$RESIDENT_CONFIG_PATH" ]; then
    cp "$RESIDENT_CONFIG_PATH" src/test/resources/config/resident.properties
    echo "Mounted config applied from $RESIDENT_CONFIG_PATH"
else
    echo "No mounted config found at $RESIDENT_CONFIG_PATH — falling back to whatever's baked into the image (if anything)."
fi

# -o: dependencies were already resolved into this image's .m2 at build time.
# Exit code propagates mvn's own (non-zero on any test failure), so Rancher sees a failed Job.
mvn -o test -Dgpg.skip=true
