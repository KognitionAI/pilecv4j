#!/bin/bash -x

# =============================================================================
# Preamble
# =============================================================================
set -e
MAIN_DIR="$(dirname "$BASH_SOURCE")"
SCRIPT_NAME="$(basename "$BASH_SOURCE")"
cd "$MAIN_DIR"
SCRIPTDIR="$(pwd -P)"
cd - >/dev/null
# =============================================================================

VERSIONS=$(cat <<EOF
4-4.12-1.4.2
5-5.6.2-1.4.2
EOF
           )

usage() {
    echo "usage: cp ./$SCRIPT_NAME ./run.sh && ./run.sh [--deploy]"
    exit 1
}

# =============================================================================
# Some helper functions
# =============================================================================
# Safe grep
sgrep() {
    set +e
    grep "$@"
    set -e
}
# Safe egrep
segrep() {
    set +e
    egrep "$@"
    set -e
}
# =============================================================================

# the .gitlab-ci.yml file does more work than just running maven so
# to build this by hand you can use this script

cd "$SCRIPTDIR"

MVN_TARGET="install"
if [ "$1" != "" ]; then
    if [ "$1" = "--deploy" ]; then
        MVN_TARGET=deploy
    else
        if [ "$1" != "--help" -o "$1" != "-h" ]; then
            echo "ERROR: unknown option \"$1\""
        fi
        usage
    fi
fi

for version in $VERSIONS; do
    echo "Build junit deps version $version"
    git checkout $version
    mvn clean $MVN_TARGET
done

git checkout master
