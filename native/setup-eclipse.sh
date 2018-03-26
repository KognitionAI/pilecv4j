#!/bin/bash

##################################################
# preamble
set -e
cd "$(dirname "$0")"
SCRIPTDIR="$(pwd -P)"
##################################################

cd "$SCRIPTDIR"

set +e
rm -rf build 2>/dev/null
set -e

mkdir build

cd build
cmake ../src/main/cpp


