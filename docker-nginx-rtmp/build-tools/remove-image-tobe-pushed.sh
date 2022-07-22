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

segrep() {
    set +e
    egrep "$@"
    set -e
}

if [ "$1" = "" ]; then
    echo "usage: $BASH_SOURCE default/image/to/be/removed/if/lookup/fails" >&2
    exit 1
fi

DEFAULT_IMAGE_TO_REMOVE="$1"

if [ ! -f ./pom.xml ]; then
    echo "You need to be in a maven project to run this." >&2
    exit 1
fi

IMAGE_TO_REMOVE="$($SCRIPTDIR/find-docker-image-from-pom.sh)"
if [ "$IMAGE_TO_REMOVE" = "" ]; then
    IMAGE_TO_REMOVE=$DEFAULT_IMAGE_TO_REMOVE
fi

IMAGE_TO_REMOVE="$IMAGE_TO_REMOVE:latest"

# check to see if the image actually exists.
EXISTS="$(docker images | awk '{ print $1, $2 }' | sed -e 's/ /:/1' | segrep "^$IMAGE_TO_REMOVE$")"
# see if it exists without the version
if [ "$EXISTS" = "" ]; then
    EXISTS="$(docker images | awk '{ print $1 }' | segrep "^$IMAGE_TO_REMOVE$")"
fi

if [ "$EXISTS" = "" ]; then
    echo "It appears the image $IMAGE_TO_REMOVE isn't already local. Skipping the image remove." >&2
else
    echo "Attempting to remove $IMAGE_TO_REMOVE" >&2
    set +e
    # Attempt to untag, at least, this image
    docker rmi "$IMAGE_TO_REMOVE" >&2
    if [ $? -ne 0 ]; then
        echo "There was an error trying to remove $IMAGE_TO_REMOVE. Will continue the build anyway but it may fail to create the final image." >&2
    fi
    set -e
fi

