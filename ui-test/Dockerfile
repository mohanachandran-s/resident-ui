# Microsoft's official Playwright Java image — ships the matching browser binaries and all
# OS-level dependencies for the Playwright version uitest-commons depends on (playwright.version
# in uitestrig-platform/pom.xml). Keep this tag in sync with that property when it changes.
FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy

USER root

ARG TARGETARCH
# kubectl version pinned for reproducible builds — compatible with any server within the
# standard +/-1 minor version skew; bump alongside the cluster's own upgrades.
ARG KUBECTL_VERSION=v1.29.9

# kubectl and jq are required because the uitestrig Helm chart (mosip-functional-tests) overrides
# this container's entrypoint to run fetch_docker_image_hash_ids.sh before ever invoking
# entrypoint.sh below — that script shells out to kubectl to record this pod's own image hash
# (exiting 1 if missing, so the suite never starts) and pipes a cluster-wide image inventory
# through jq. No Maven here: the jar is built once in CI (build-maven-uitest-resident) and handed
# to this build already-packaged — see COPY below.
RUN apt-get update && \
    apt-get install -y curl ca-certificates jq && \
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

# Self-contained executable jar (MainRunner, driving the JUnit Platform Launcher directly). See
# MainRunner for why this runs as a single `java -jar` process instead of through Maven/Surefire,
# and entrypoint.sh for how it locates this jar.
COPY --chown=${container_user_uid}:${container_user_group} target/*-jar-with-dependencies.jar ${work_dir}target/
COPY --chown=${container_user_uid}:${container_user_group} entrypoint.sh ${work_dir}

ENTRYPOINT ["/bin/bash", "./entrypoint.sh"]
