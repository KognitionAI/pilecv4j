#!/bin/bash

usage() {
    echo "USAGE: $0 -s source-dir -d dest-dir -se src-extention -de desti-extention"
    exit 1
}

OPENCV_HOME=/home/jim/utils/opencv
MAVEN_REPO=/home/jim/.m2/repository

SRCDIR=
DSTDIR=
SE=
DE=

while [ $# -gt 0 ]; do
    case $1 in
        "-s")
            SRCDIR=$2
            shift
            shift
            ;;
        "-d")
            DSTDIR=$2
            shift
            shift
            ;;
        "-se")
            SE=$2
            shift
            shift
            ;;
        "-de")
            DE=$2
            shift
            shift
            ;;
        *)
            usage
            shift
            ;;
    esac
done

if [ "$SRCDIR" = "" -o "$DSTDIR" = "" ]; then
    usage
fi

if [ ! -d "$DSTDIR" ]; then
    echo "\"$DSTDIR\" doesn't appear to be an existing directory."
    usage
fi

if [ ! -d "$SRCDIR" ]; then
    echo "\"$SRCDIR\" doesn't appear to be an existing directory."
    usage
fi

cd "$DSTDIR"
if [ $? -ne 0 ]; then
    echo "ERROR: couldn't cd to \"$DSTDIR\""
fi
DSTDIR=`pwd -P`
cd -

cd "$SRCDIR"
if [ $? -ne 0 ]; then
    echo "ERROR: couldn't cd to \"$SRCDIR\""
fi

export LD_LIBRARY_PATH=/usr/local/lib:$OPENCV_HOME/lib

FILES=`find . -name "*.$SE"`
TMPIFS="$IFS"
IFS=$(echo -en "\n\b")
for entry in $FILES; do
    echo "converting: \"$entry\""
    BASE=${entry%.*}
    RELDIR=`dirname $entry`
    mkdir -p "$DSTDIR/$RELDIR"
    if [ $? -ne 0 ]; then
        echo "Failed to create directory \"$DSTDIR/$RELDIR\""
        exit 1
    fi
    if [ -f "$DSTDIR/$BASE.$DE" ]; then
        echo "File \"$DSTDIR/$BASE.DE\" already exists. Skipping"
    else
        RESULTS=`java -cp $MAVEN_REPO/com/jiminger/lib-image/1.0-SNAPSHOT/lib-image-1.0-SNAPSHOT.jar:$MAVEN_REPO/com/jiminger/lib-util/1.0-SNAPSHOT/lib-util-1.0-SNAPSHOT.jar:$MAVEN_REPO/com/jiminger/opencv-lib-jar/3.1.0/opencv-lib-jar-3.1.0-withlib.jar:$MAVEN_REPO/com/jiminger/opencv-lib-jar/3.1.0/opencv-lib-jar-3.1.0.jar:$MAVEN_REPO/opencv/opencv/3.1.0/opencv-3.1.0.jar:$MAVEN_REPO/commons-io/commons-io/2.0.1/commons-io-2.0.1.jar com.jiminger.image.ImageFile -i "$SRCDIR/$entry" -o "$DSTDIR/$BASE.$DE"`
        if [ $? -ne 0 ]; then
            echo "FAILED to run the image convert. See the above error."
            echo "$RESULTS"
        fi
    fi
done
IFS="$TMPIFS"
