# Microsoft's official Playwright Java image — ships the matching browser binaries and all
# OS-level dependencies for the Playwright version uitest-commons depends on (playwright.version
# in uitestrig-platform/pom.xml). Keep this tag in sync with that property when it changes.
FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy

USER root

ARG TARGETARCH
# kubectl version pinned for reproducible builds — compatible with any server within the
# standard +/-1 minor version skew; bump alongside the cluster's own upgrades.
ARG KUBECTL_VERSION=v1.29.9

# Maven isn't bundled in the Playwright base image. kubectl is required because the uitestrig
# Helm chart (mosip-functional-tests) overrides this container's entrypoint to run
# fetch_docker_image_hash_ids.sh before ever invoking entrypoint.sh below — that script shells
# out to kubectl to record this pod's own image hash, and exits 1 if kubectl is missing, so the
# suite never starts without it.
RUN apt-get update && \
    apt-get install -y maven curl ca-certificates && \
    curl -fLO "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl" && \
    install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl && \
    rm kubectl && \
    rm -rf /var/lib/apt/lists/*

# Define build-time arguments and labels
ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

ENV work_dir=/home/${container_user}/

RUN groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/bash -m ${container_user} -d ${work_dir} \
    && chown -R ${container_user}:${container_user} /home/${container_user}/

USER ${container_user_uid}:${container_user_gid}
WORKDIR ${work_dir}

# Source only — no fat jar. The suite runs via `mvn test` (Surefire → JUnit5 Platform →
# ResidentTestRunner → Cucumber), same as a local run; there's no standalone executable jar
# for this suite the way the old Selenium TestRunner.main() used to provide.
COPY --chown=${container_user_uid}:${container_user_group} pom.xml ${work_dir}
COPY --chown=${container_user_uid}:${container_user_group} src/ ${work_dir}src/
COPY --chown=${container_user_uid}:${container_user_group} entrypoint.sh ${work_dir}

# Resolves and caches all dependencies (including uitest-commons/apitest-commons) into this
# image's own .m2, and compiles the test sources, so a container start only has to run the
# suite, not resolve the internet first. Requires uitest-commons to actually be published and
# reachable at build time — see uitestrig-platform/pom.xml's distributionManagement.
RUN mvn -B test-compile -Dgpg.skip=true

ENTRYPOINT ["/bin/bash", "./entrypoint.sh"]
