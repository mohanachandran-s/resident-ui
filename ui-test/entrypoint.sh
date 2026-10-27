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

# Maven/Surefire's forked test JVM doesn't reliably inherit hyphenated environment variable names
# (confirmed in Rancher: keycloak-external-url is present in the container's own env, but
# System.getenv() inside the forked JVM reports it unset, while every underscore/camelCase key
# survives fine — a known plexus-utils/Commandline quirk reconstructing the forked env via `env` +
# a C-identifier-style KEY= regex that hyphenated names don't match). Bash can't reference it as
# $keycloak-external-url either (hyphens aren't valid in a bash variable name), so read it via
# printenv and hand it to the JVM as a system property instead, which travels via the command-line
# args array rather than that env-reconstruction path. ResidentConfigManager.resolveWithEnvOverride()
# reads it back via System.getProperty().
KEYCLOAK_EXTERNAL_URL_ARG=()
keycloak_external_url_value="$(printenv keycloak-external-url || true)"
if [ -n "$keycloak_external_url_value" ]; then
    KEYCLOAK_EXTERNAL_URL_ARG=("-Dkeycloak-external-url=$keycloak_external_url_value")
fi

# -o: dependencies were already resolved into this image's .m2 at build time.
# Exit code propagates mvn's own (non-zero on any test failure), so Rancher sees a failed Job.
mvn -o test -Dgpg.skip=true "${KEYCLOAK_EXTERNAL_URL_ARG[@]}"
