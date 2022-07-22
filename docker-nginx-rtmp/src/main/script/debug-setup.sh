#!/bin/bash

set -e

cleanup() {
    cd - >/dev/null 2>&1
    set +e
    rm -rf "$WDIR" >/dev/null 2>&1
    set -e
}    

usage() {
    echo "usage: $BASH_SOURCE -i cameraid [-host hostname|-r] [-c containerName]"
    echo "	-i	: supply the camera id to observe."
    echo "	-h	: supply the host name that will appear in the index.html file"
    echo "		  referencing the video feeds."
    echo "	-r	: make the host name that will appear in the index.html file"
    echo "		  relative to the index.html file."
    echo "	-c	: running container name [default is '$DEFAULT_CONTAINER_NAME']"
    echo "	-ts	: start a test source using gstreamer's \"videotestsrc\"."
    cleanup
    exit 1
}

##################################################
# preamble
set -e
cd "$(dirname "$BASH_SOURCE")"
SCRIPTDIR="$(pwd -P)"
##################################################

# This is removed at the end so don't set it to a directory
#    that you want to keep.
WDIR=/tmp/debug-feeds

DEFAULT_CONTAINER_NAME=kognition-nginx

VIDEO_FEED_SERVER_NAME=
RELATIVE=
CONTAINER=$DEFAULT_CONTAINER_NAME
CAMERA_ID="test"
TEST_SOURCE=

while [ "$1" != "" ]; do
    case $1 in
        "-h")
            if [ "$2" = "" ]; then
                usage
            fi
            VIDEO_FEED_SERVER_NAME="$2"
            shift;
            shift;
            ;;
        "-r")
            RELATIVE="true"
            shift;
            ;;
        "-c")
            if [ "$2" = "" ]; then
                usage
            fi
            CONTAINER=$2
            shift;
            shift;
            ;;
        "-i")
            if [ "$2" = "" ]; then
                usage
            fi
            CAMERA_ID=$2
            shift;
            shift;
            ;;
        "-ts")
            TEST_SOURCE=true
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

if [ "$CAMERA_ID" = "" ]; then
    echo "ERROR: You must supply the camera id."
    usage
fi

if [ "$VIDEO_FEED_SERVER_NAME" != "" -a "$RELATIVE" != "" ]; then
    echo "ERROR: You cannot select both -h and -r."
    usage
fi

if [ "$VIDEO_FEED_SERVER_NAME" = "" ]; then
    VIDEO_FEED_SERVER_NAME=localhost
fi

if [ "$RELATIVE" != "" ]; then
    VIDEO_FEED_HTTP_PREFIX="camera/view/"
else
    VIDEO_FEED_HTTP_PREFIX="http://$VIDEO_FEED_SERVER_NAME:8080/camera/view/"
fi

mkdir -p "$WDIR"

export VIDEO_FEED_HTTP_PREFIX
export VIDEO_FEED_SERVER_NAME
export CAMERA_ID

cat "$SCRIPTDIR"/index.html.tmpl | envsubst > "$WDIR"/index.html

cd "$WDIR"
npm install video.js videojs-flash videojs-contrib-hls videojs-contrib-dash >/dev/null 2>&1
tar cfz node_modules.tar.gz ./node_modules >/dev/null 2>&1

docker exec $CONTAINER mkdir -p /var/www/html
docker cp "$WDIR"/index.html $CONTAINER:/var/www/html/index.html
docker cp "$WDIR"/node_modules.tar.gz $CONTAINER:/var/www/html/node_modules.tar.gz
docker exec $CONTAINER tar xf /var/www/html/node_modules.tar.gz -C /var/www/html

cleanup

if [ "$TEST_SOURCE" ]; then
    # wont return
    GST_DEBUG=3 gst-launch-1.0 videotestsrc ! timeoverlay ! x264enc key-int-max=40 ! "video/x-h264,stream-format=avc" ! flvmux streamable=true ! rtmpsink location=rtmp://localhost:1935/live/test
fi
