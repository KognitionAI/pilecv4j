#!/bin/bash

# =============================================================================
# Preamble
# =============================================================================
set -e
MAIN_DIR="$(dirname "$BASH_SOURCE")"
cd "$MAIN_DIR"
SCRIPTDIR="$(pwd -P)"
# =============================================================================

usage() {
    echo "$BASH_SOURCE [options]" >&2
    echo "" >&2
    echo "Options:" >&2
    echo "  --tensorflow-version tf-version:" >&2
    echo "  --version protobuf-version:" >&2
    echo "       Either --tensorflow-version or --version MUST be specified." >&2
    echo "       If --tensorflow-version is specified then the compliant protobuf" >&2
    echo "         version will be looked up in tensorflow's github repository." >&2
    if [ $# -gt 0 ]; then
        exit $1
    else
        exit 1
    fi
}

TF_VERSION=
PROTOBUF_VERSION=
while [ $# -gt 0 ]; do
    case "$1" in
        "--tensorflow-version")
            TF_VERSION="$2"
            shift
            shift
            ;;
        "-v"|"--version")
            PROTOBUF_VERSION="$2"
            shift
            shift
            ;;
        "-help"|"--help"|"-h"|"-?")
            shift
            usage 0
            ;;
        *)
            echo "ERROR: Unknown option \"$1\""
            usage
            ;;
    esac
done

# ==================================================================================
# We can figure out the exact version of protobuf's used by tensorflow.
if [ "$PROTOBUF_VERSION" != "" -a "$TF_VERSION" != "" ]; then
    echo "ERROR: Don't specify both --tensorflow-version and --version." >&2
    usage
fi

if [ "$PROTOBUF_VERSION" = "" -a "$TF_VERSION" = "" ]; then
    echo "ERROR: You must specify either --tensorflow-version or --version." >&2
    usage
fi

if [ "$PROTOBUF_VERSION" = "" ]; then
    PROTOBUF_VERSION="$(curl -s https://github.com/tensorflow/tensorflow/blob/v$TF_VERSION/tensorflow/workspace2.bzl | egrep -e "(PROTOBUF_STRIP_PREFIX|strip_prefix.*protobuf-)" | head -1 | sed -e "s/^.*protobuf-/protobuf-/1" | sed -e "s/<.*$//g" | sed -e "s/^protobuf-//1" | sed -e "s/.quot.//g")"
    if [ "$PROTOBUF_VERSION" = "" ]; then
        echo "ERROR: Failed to determine the version of Protobuf's being used by Tensorflow version $TF_VERSION." >&2
        exit 1
    fi
    echo "The protobuf version implied by tensorflow $TF_VERSION is \"$PROTOBUF_VERSION\"" >&2
fi

echo "$PROTOBUF_VERSION"
# ==================================================================================
