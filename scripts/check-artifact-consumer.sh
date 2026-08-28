#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# 本地暂存目录保留以便审阅 AAR/POM/报告；不调用 publishToMavenLocal 或远端发布。
verification_dir=$(mktemp -d "${TMPDIR:-/tmp}/roomflow-consumer.XXXXXX")
echo "本地产物验收目录：$verification_dir"
room_versions=(2.6.1 2.8.4)
if [[ -n "${ROOM_VERSION:-}" ]]; then room_versions=("$ROOM_VERSION"); fi
for room_version in "${room_versions[@]}"; do
    ./gradlew :room-flow:publishVerificationPublicationToVerificationRepository :room-flow-debug:publishVerificationPublicationToVerificationRepository :room-flow-compiler:publishVerificationPublicationToVerificationRepository \
        -I scripts/verification.init.gradle "-PverificationBuildRoot=$verification_dir/publish-$room_version" \
        --project-cache-dir "$verification_dir/project-cache" \
        "-ProomVersion=$room_version" "-PverificationRepository=$verification_dir/repository" "$@"
    for pom_only in false true; do
      for with_debug in false true; do
        ./gradlew -p verification/consumer checkConsumerDependencies \
            "-ProomVersion=$room_version" "-PverificationRepository=$verification_dir/repository" \
            "-PconsumerBuildDir=$verification_dir/consumer-$room_version-$pom_only-$with_debug" \
            "-PpomOnly=$pom_only" "-PwithDebugArtifact=$with_debug" "$@"
      done
    done
done
