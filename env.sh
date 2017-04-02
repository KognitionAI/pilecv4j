# This file can set:
#
# 1) MAVEN_REPO to the repository with the jar files. This will assume it's ~/.m2/repository if not set
# 2) If necessary, LD_LIBRARY_PATH (linux) or PATH (windows) to point to the OpenCV libraries.

if [ "$WINDOWS" = "true" ]; then
    cd ~/projects/opencv-3.2.0
    OPENCV_HOME="`pwd -P`"
    export PATH=$PATH:$OPENCV_HOME/build/x64/vc14/bin
    cd - >/dev/null
else
    OPENCV_HOME=/home/jim/utils/opencv
    export LD_LIBRARY_PATH=/usr/local/lib:$OPENCV_HOME/lib
fi

