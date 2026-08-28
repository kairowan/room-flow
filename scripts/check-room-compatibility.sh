#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

for room_version in 2.6.1 2.8.4; do
    ./gradlew checkKotlinTypes checkModuleBoundaries checkJvmApi :room-flow:assembleRelease :room-flow-debug:assembleRelease :app:assembleRelease :app:assembleDebug \
        "-ProomVersion=$room_version" "$@"
    echo "Room $room_version: 构建、类型/API 与模块边界检查完成；测试目录已删除，未执行单元或设备测试。"
done
