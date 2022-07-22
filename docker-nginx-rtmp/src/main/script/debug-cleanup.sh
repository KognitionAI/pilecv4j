#!/bin/bash

set -e

usage() {
    echo "usage: $BASH_SOURCE [-c containerName]"
    echo "	-c	: running container name [default is 'docker-nginx-rtmp']"
    exit 1
}

##################################################
# preamble
set -e
cd "$(dirname "$BASH_SOURCE")"
SCRIPTDIR="$(pwd -P)"
##################################################

WDIR=/tmp/debug-feeds

CONTAINER=kognition-nginx

while [ "$1" != "" ]; do
    case $1 in
        "-c")
            if [ "$2" = "" ]; then
                usage
            fi
            CONTAINER=$2
            shift;
            shift;
            ;;
        "--help")
            usage
            ;;
        *)
            echo "ERROR: Unknown option: \"$1\""
            usage
            ;;
    esac
done

docker exec $CONTAINER rm -rf /var/www/html
