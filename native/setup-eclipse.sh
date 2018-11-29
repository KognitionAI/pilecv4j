#!/bin/bash

##################################################
# preamble
set -e
cd "$(dirname "$BASH_SOURCE")"
SCRIPTDIR="$(pwd -P)"
##################################################

usage() {
    echo "usage: OPENCV_INSTALL=/path/to/opencv/install $BASH_SOURCE opencv-version"
    exit 1
}


cd "$SCRIPTDIR"

# need the opencv version we're building
if [ "$1" = "" ]; then
    usage
fi

if [ "$OPENCV_INSTALL" = "" ]; then
    usage
fi

export DEP_OPENCV_VERSION=$1

set +e
rm -rf build 2>/dev/null
set -e

mkdir build

cd build
cmake ../src/main/cpp


