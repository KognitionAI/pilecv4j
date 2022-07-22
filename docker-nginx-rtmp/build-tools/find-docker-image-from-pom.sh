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

EFFECTIVE_POM="$($SCRIPTDIR/effective-pom-json.sh)"

IMAGE_NAME="$(echo "$EFFECTIVE_POM" | jq -r -M -j '.. | .images?.image | select(. != null)' | jq -rM -s add | jq -rM '.name')"
IMAGE_NAME="$(echo "$IMAGE_NAME" | sed -e "s/^\"//1" | sed -e "s/\"$//1")"

PUSH_REG="$(echo "$EFFECTIVE_POM" | jq -rM -j '.. | .configuration? | select(. != null) | select( .pushRegistry != null)' | jq -rM -s add | jq -rM '.pushRegistry')"
REG="$(echo "$EFFECTIVE_POM" | jq -rM -j '.. | .configuration? | select(. != null) | select(.registry != null)' | jq -rM -s add | jq -rM '.registry')"

TOUSE="$([ "$PUSH_REG" = "" -o "$PUSH_REG" = "null" ] && echo "$REG" || echo "$PUSH_REG")"
TOUSE="$(echo "$TOUSE" | sed -e "s/^\"//1" | sed -e "s/\"$//1")"

if [ "$TOUSE" = "" -o "$IMAGE_NAME" = "" ]; then
    echo "ERROR: Couldn't determine the docker image from the maven project."
    exit 1
fi

echo "$TOUSE/$IMAGE_NAME"

