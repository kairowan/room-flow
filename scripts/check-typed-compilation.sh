#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
reports=$(mktemp -d "${TMPDIR:-/tmp}/roomflow-typed-negative.XXXXXX")
echo "Negative compile reports: $reports"
versions=(2.6.1 2.8.4)
if [[ -n "${ROOM_VERSION:-}" ]]; then versions=("$ROOM_VERSION"); fi
for version in "${versions[@]}"; do
    for failure in foreign value ignored embedded custom generic specforeign specvalue projectionforeign projectionvalue aggregatevalue cursorforeign; do
        log="$reports/$version-$failure.log"
        if ./gradlew :typed-failures:compileDebugKotlin "-ProomVersion=$version" "-PtypedFailure=$failure" \
            -I scripts/verification.init.gradle "-PverificationBuildRoot=$reports/build" --project-cache-dir "$reports/cache" \
            --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process "$@" > "$log" 2>&1; then
            echo "Unexpected compile success: $version/$failure"
            exit 1
        fi
        case "$failure" in
            foreign) pattern='ForeignColumn.kt.*(type mismatch|Type mismatch)' ;;
            value) pattern='WrongValue.kt.*(type mismatch|Type mismatch|does not conform)' ;;
            ignored) pattern='IgnoredColumn.kt.*Unresolved reference.*temporary' ;;
            specforeign) pattern='QuerySpecForeign.kt.*(type mismatch|Type mismatch)' ;;
            specvalue) pattern='QuerySpecValue.kt.*(type mismatch|Type mismatch|does not conform)' ;;
            projectionforeign) pattern='ProjectionForeign.kt.*(type mismatch|Type mismatch)' ;;
            projectionvalue) pattern='ProjectionValue.kt.*(type mismatch|Type mismatch|does not conform)' ;;
            aggregatevalue) pattern='AggregateValue.kt.*(Unresolved reference|receiver type mismatch|type mismatch|Type mismatch)' ;;
            cursorforeign) pattern='CursorForeign.kt.*(type mismatch|Type mismatch)' ;;
            embedded|custom) pattern='RF002:' ;;
            generic) pattern='RF001:' ;;
        esac
        rg -q "$pattern" "$log" || { tail -n 70 "$log"; exit 1; }
        echo "$version/$failure: expected compilation rejection"
    done
done
