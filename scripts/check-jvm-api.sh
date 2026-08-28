#!/usr/bin/env bash
set -euo pipefail

aar=${1:?Usage: check-jvm-api.sh library.aar baseline.txt [--update]}
baseline=${2:?Missing baseline}
mode=${3:---check}
[[ "$mode" == --check || "$mode" == --update ]] || exit 2
work=$(mktemp -d "${TMPDIR:-/tmp}/roomflow-api.XXXXXX")
trap 'rm -rf "$work"' EXIT
unzip -p "$aar" classes.jar > "$work/classes.jar"
classes=()
while IFS= read -r entry; do
    classes+=("${entry//\//.}")
done < <(unzip -Z1 "$work/classes.jar" | LC_ALL=C sort | sed -n 's/\.class$//p')
[[ ${#classes[@]} -gt 0 ]]
# ponytail: JDK 工具记录整个 JVM 可见面（包含 Kotlin internal/生成类），不推断 Kotlin metadata 的源码兼容性。
"${JAVA_HOME:?Use JDK 17}/bin/javap" -classpath "$work/classes.jar" -protected -s -constants "${classes[@]}" \
    | sed '/^Compiled from /d' \
    | awk '/^[^[:space:]]/ { visible = ($1 == "public" || $1 == "protected" || ($0 == "}" && visible)) } visible' > "$work/api.txt"
if [[ "$mode" == --update ]]; then
    mkdir -p "$(dirname "$baseline")"
    cp "$work/api.txt" "$baseline"
    echo "Updated JVM baseline: $baseline (review the diff before release)"
else
    diff -u "$baseline" "$work/api.txt"
    echo "JVM baseline matches: $baseline"
fi
