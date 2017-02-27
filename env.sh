
. ./os.sh

cd ~/.m2
MAVEN_REPO="`pwd -P`"/repository
cd - >/dev/null

if [ "$WINDOWS" = "true" ]; then
    alias mvn=/c/utils/apache-maven-3.3.9/bin/mvn
    cd ~/projects/opencv-3.2.0
    OPENCV_HOME="`pwd -P`"
    export PATH=$PATH:$OPENCV_HOME/build/x64/vc14/bin
    cd - >/dev/null
else
    OPENCV_HOME=/home/jim/utils/opencv
    export LD_LIBRARY_PATH=/usr/local/lib:$OPENCV_HOME/lib
fi

