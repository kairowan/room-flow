#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# 默认本地暂存；指定 RELEASE_REPOSITORY 时只消费已有远端制品，不执行发布。
verification_dir=$(mktemp -d "${TMPDIR:-/tmp}/roomflow-consumer.XXXXXX")
echo "本地产物验收目录：$verification_dir"
repository="${RELEASE_REPOSITORY:-$verification_dir/repository}"
# ${array[@]+"${array[@]}"} keeps optional arguments safe under macOS Bash 3 + nounset.
release_args=()
if [[ -n "${RELEASE_VERSION:-}" ]]; then release_args=("-PreleaseVersion=$RELEASE_VERSION"); fi
if [[ -n "${RELEASE_REPOSITORY:-}" && -z "${RELEASE_VERSION:-}" ]]; then
    echo "RELEASE_REPOSITORY 必须同时指定 RELEASE_VERSION" >&2
    exit 1
fi
room_versions=(2.6.1 2.8.4)
if [[ -n "${ROOM_VERSION:-}" ]]; then room_versions=("$ROOM_VERSION"); fi
for room_version in "${room_versions[@]}"; do
    if [[ -z "${RELEASE_REPOSITORY:-}" && ( -z "${RELEASE_VERSION:-}" || "$room_version" == "${room_versions[0]}" ) ]]; then
      publish_room="$room_version"
      if [[ -n "${RELEASE_VERSION:-}" ]]; then publish_room=2.6.1; fi
      ./gradlew :room-flow:publishVerificationPublicationToVerificationRepository :room-flow-debug:publishVerificationPublicationToVerificationRepository :room-flow-compiler:publishVerificationPublicationToVerificationRepository \
        -I scripts/verification.init.gradle "-PverificationBuildRoot=$verification_dir/publish-$room_version" \
        --project-cache-dir "$verification_dir/project-cache" \
        "-ProomVersion=$publish_room" "-PverificationRepository=$repository" ${release_args[@]+"${release_args[@]}"} "$@"
    fi
    for pom_only in false true; do
      for with_debug in false true; do
        ./gradlew -p verification/consumer checkConsumerDependencies \
            "-ProomVersion=$room_version" "-PverificationRepository=$repository" \
            "-PconsumerBuildDir=$verification_dir/consumer-$room_version-$pom_only-$with_debug" \
            "-PpomOnly=$pom_only" "-PwithDebugArtifact=$with_debug" ${release_args[@]+"${release_args[@]}"} "$@"
      done
    done
done
