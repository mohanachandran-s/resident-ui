# Microsoft's official Playwright Java image — ships the matching browser binaries and all
# OS-level dependencies for the Playwright version uitest-commons depends on (playwright.version
# in uitestrig-platform/pom.xml). Keep this tag in sync with that property when it changes.
FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy

USER root

ARG TARGETARCH
# Pinned for reproducible builds; bump alongside the cluster's own upgrades.
ARG KUBECTL_VERSION=v1.29.9

# kubectl and jq: the uitestrig Helm chart overrides this container's entrypoint to run
# fetch_docker_image_hash_ids.sh (records this pod's image hash via kubectl, piped through jq)
# before entrypoint.sh ever runs.
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

# Self-contained executable jar (see MainRunner) — built once in CI, not here.
COPY --chown=${container_user_uid}:${container_user_group} target/*-jar-with-dependencies.jar ${work_dir}target/
COPY --chown=${container_user_uid}:${container_user_group} entrypoint.sh ${work_dir}

ENTRYPOINT ["/bin/bash", "./entrypoint.sh"]
