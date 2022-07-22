#!/bin/bash
# =============================================================================
# Preamble
# =============================================================================
set -e
MAIN_DIR="$(dirname "$BASH_SOURCE")"
cd "$MAIN_DIR"
SCRIPTDIR="$(pwd -P)"
cd - >/dev/null
# =============================================================================

EFFECTIVE_POM="$($SCRIPTDIR/effective-pom-json.sh)" 2>/dev/null

echo "$EFFECTIVE_POM" | jq -c -rM -j '.[].version'
