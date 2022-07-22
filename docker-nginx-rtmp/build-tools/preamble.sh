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

source "$SCRIPTDIR"/env.sh
# this is to test if we're running this inside a docker build container
# because we will sometime source this from bash
if [ -f /root/install-env.sh ]; then
    source /root/.bashrc

    if [ -f /root/install-env.sh ]; then
        source /root/install-env.sh
    fi

    echo "127.0.0.1 $HOSTNAME" >> /etc/hosts
fi

# =============================================================================
# More functions
# =============================================================================

lookup_cuda_version() {
    local_CUDA_VERSION=
    if [ -f /usr/local/cuda/version.txt ]; then
        local_CUDA_VERSION="$(cat /usr/local/cuda/version.txt | sgrep -i cuda | sgrep -i version | tail -1 | sed -e "s/^.*[Vv]ersion //1" | segrep -o -e "^[0-9][0-9]*[0-9]*\.[0-9][0-9]*[0-9]*[0-9]*")"
    else
        NVCC_EXE=
        if [ "$(type nvcc 2>&1 | sgrep "not found")" != "" -a -x /usr/local/cuda/bin/nvcc ]; then
            NVCC_EXE=/usr/local/cuda/bin/nvcc
        else
            NVCC_EXE=nvcc
        fi
        if [ "$NVCC_EXE" != "" ]; then
            local_CUDA_VERSION="$($NVCC_EXE --version | segrep -e ", V[0-9][0-9]*[0-9]*[0-9]*\." | sed -e "s/^.*, V//1" | sed -E "s/\.[0-9][0-9]*[0-9]*[0-9]*[0-9]*[0-9]*[0-9]*$//1")"
        fi
    fi
    echo "$local_CUDA_VERSION"
}

lookup_parallel_build_option() {
    echo "-j$(grep -c ^processor /proc/cpuinfo)"
}

lookup_protobuf_version() {
    "$SCRIPTDIR"/lookup-protobuf-version.sh "$@"
}

sgrep() {
    set +e
    grep "$@"
    set -e
}

segrep() {
    set +e
    egrep "$@"
    set -e
}

git_subdir() {
    echo "$1" | sed -e 's/^.*\///1' | sed -e 's/\.git$//1'
}

git_project() {
    set -e
    [ "$GIT_REPO_HOST" = "" ] && GIT_REPO_HOST=$DEFAULT_GIT_REPO_HOST
    CUR_REPO=$(git remote -v | head -1 | awk '{ print $2 }')
    GP_PROJECT=
    if [ "$(echo "$CUR_REPO" | segrep -e '^git@')" != "" ]; then
        GP_PROJECT="$(echo "$CUR_REPO" | sed -e 's/^.*\://' | sed -e "s/\.git$//1")"
    elif [ "$(echo "$CUR_REPO" | segrep -e '^https:')" != "" ]; then
        GP_PROJECT="$(echo "$CUR_REPO" | sed -e 's|^https://||1' | sed -e "s/\.git$//1" | sed -e "s|/| |g" | awk '{ for (i=2; i <= NF; i++) printf FS$i; print NL }' | sed -e "s| |/|g")"
    elif [ "$(echo "$CUR_REPO" | segrep -e '^http:')" != "" ]; then
        GP_PROJECT="$(echo "$CUR_REPO" | sed -e 's|^http://||1' | sed -e "s/\.git$//1" | sed -e "s|/| |g" | awk '{ for (i=2; i <= NF; i++) printf FS$i; print NL }' | sed -e "s| |/|g")"
    fi

    if [ "$GP_PROJECT" = "" ]; then
        echo "ERROR: Couldn't figure out the project from the git remote command." >&2
        return 1
    fi

    # strip off a potential starting slash
    GP_PROJECT="$(echo "$GP_PROJECT" | sed -e "s|^/||g")"

    echo "$GP_PROJECT"
    set +x
}

version_from_pom() {
    if [ "$1" = "" -o "$2" = "" ]; then
        echo "ERROR: Must supply a maven group id and an artifact id to extract a version from the pom file." >&2
        return 1
    fi
    VFP_GROUPID=$1
    VFP_ARTIFACTID=$2
    EFFECTIVE_POM="$($SCRIPTDIR/effective-pom-json.sh)"
    EFFPOM_VERSION="$(echo "$EFFECTIVE_POM" | jq -rM '.[].dependencies' | jq -rM "select(. != null) | .dependency[] | select(.groupId==\"$VFP_GROUPID\") | select(.artifactId==\"$VFP_ARTIFACTID\") | .version" | sed -e "s/^\"//1" | sed -e "s/\"$//1" | head -1)"
    if [ "$EFFPOM_VERSION" = "" ]; then
        # try to get it from the dependency tree
        DEPVER="$(mvn -B -U dependency:tree | grep "$VFP_GROUPID" | grep "$VFP_ARTIFACTID" | sed -e "s/^.* //g" | head -1 | sed -e "s/:/ /g" | awk '{ print $4 }' 2>/dev/null)"
        echo "$DEPVER"
    else
        echo "$EFFPOM_VERSION"
    fi
}

downstream() {
    set -e
    [ "$GIT_REPO_HOST" = "" ] && GIT_REPO_HOST=$DEFAULT_GIT_REPO_HOST
    if [ "$CHAIN_ID" != "" ]; then
        echo "Skipping downstream since this build appears to have been triggered by chain $CHAIN_ID" >&2
    else
        # use git to get the current project
        echo "Triggering with BUILT=$THIS_PROJECT" >&2
        RESULT=$(curl -s -X POST -F "variables[BUILT]=$THIS_PROJECT" -F token=96da7dad4935b8281aedd4cd5a3b0c -F ref=master https://$GIT_REPO_HOST/api/v4/projects/59/trigger/pipeline)

        set +e
        type jq
        if [ $? -ne 0 ]; then
            echo "$RESULT"
        else
            echo "$RESULT" | jq '.'
        fi
        set -e
    fi
}


install_protobuf() {
    set -e
    mkdir -p /opt
    PB_VER=$("$SCRIPTDIR"/lookup-protobuf-version.sh "$@")
    PB_DIR=/opt/protobuf-$PB_VER
    $SCRIPTDIR/install-from-zip.sh -d /opt --name protobuf-$PB_VER --artifact ai.kognition:protobuf-build:$PB_VER --repo /tmp/maven-local-repo --leave-repo
    export CMAKE_PREFIX_PATH="$PB_DIR"
}

install_darknet() {
    set -e
    if [ "$1" = "" ]; then
        echo "ERROR: Must supply a version of darknet to deploy." >&2
        return 1
    fi
    mkdir -p /opt
    $SCRIPTDIR/install-from-zip.sh -d /opt --name darknet-$1 --artifact ai.kognition:darknet-build:$1 --repo /tmp/maven-local-repo --leave-repo --no-ldconfig
    export DARKNET_ROOT=/opt/darknet-$1
}

visionlabs_version_pom() {
    version_from_pom "ai.kognition" "visionlabs-build"
}

install_visionlabs_license() {
    set -e
    # check if the service is running
    HASP_RUNNING="$(ps -ef | sgrep aksusbd | sgrep -v grep)"
    if [ "$HASP_RUNNING" = "" ]; then
        service aksusbd start
        sleep 3
    fi
    hasp_update u $SCRIPTDIR/files/visionlabs/Unlocked_90d_Kognition.v2c
    service aksusbd stop
}

install_visionlabs() {
    set -e
    if [ "$1" = "" ]; then
        # attempt to extract the version from the pom file
        IV_VERSION="$(visionlabs_version_pom)"
    else
        IV_VERSION=$1
    fi
    mkdir -p /opt
    $SCRIPTDIR/install-from-zip.sh -d /opt --name visionlabs-$IV_VERSION --artifact ai.kognition:visionlabs-build:$IV_VERSION --repo /tmp/maven-local-repo --leave-repo
    export FSDKDIR=/opt/visionlabs-$IV_VERSION
    # install hasp
    ################################
    # these are the hasp prereqs. They should be in the container already.
    #apt-get update
    #dpkg --add-architecture i386
    #apt-get update
    #apt-get install -y apt apt-utils
    #apt-get install -y libc6:i386 libncurses5:i386 libstdc++6:i386
    ################################
    dpkg -i $SCRIPTDIR/files/visionlabs/aksusbd_7.60-1_vlabs_i386.deb
    service aksusbd stop
}

openpose_version_pom() {
    version_from_pom "ai.kognition" "openpose-zip-install"
}

opencv_version_pom() {
    version_from_pom "ai.kognition.pilecv4j" "opencv-lib-jar"
}

install_opencv() {
    set -e
    mkdir -p /opt
    CUDA_VERSION="$(lookup_cuda_version)"
    if [ "$1" = "" ]; then
        echo "No opencv version supplied. Attempting to retreive it from the maven project." >&2
        OPENCV_DEPLOY_VERSION="$(opencv_version_pom)"
        if [ "$OPENCV_DEPLOY_VERSION" = "" ]; then
            echo "ERROR: Couldn't determin the OpenCV version from the project and you didn't supply it to install_opencv." >&2
            return 1
        fi
        DEPENDS_ON_CORRECTCUDA="$(echo "$OPENCV_DEPLOY_VERSION" | segrep -e "-cuda$CUDA_VERSION$")"
        if [ "$DEPENDS_ON_CORRECTCUDA" = "" ]; then
            echo "The version of opencv depended on by this project is $OPENCV_DEPLOY_VERSION but the GPU version extention is $CUDA_VERSION. These need to be consistent." >&2
            return 1
        fi
        OPENCV_VERSION="$(echo "$OPENCV_DEPLOY_VERSION" | sed -e "s/-cuda$CUDA_VERSION$//1")"
    else
        OPENCV_VERSION="$1"
        OPENCV_DEPLOY_VERSION=$OPENCV_VERSION-cuda$CUDA_VERSION
    fi
    $SCRIPTDIR/install-from-zip.sh -d /opt --name opencv$OPENCV_DEPLOY_VERSION --artifact ai.kognition:opencv-build:$OPENCV_DEPLOY_VERSION  --repo /tmp/maven-local-repo --leave-repo
    if [ "$ALTERNATE_OPENCV_EXPORT" != "" ]; then
        OPENCV_SHORT_VERSION="$(echo "$OPENCV_VERSION" | sed -e "s/\.//g" | sed -e "s/\-.*$//g")"
        export OpenCV_INCLUDE_DIRS=/opt/opencv$OPENCV_DEPLOY_VERSION/include/opencv4
        export OpenCV_LIBS=/opt/opencv$OPENCV_DEPLOY_VERSION/share/java/opencv4/libopencv_java$OPENCV_SHORT_VERSION.so
    else
        export OpenCV_DIR=/opt/opencv$OPENCV_DEPLOY_VERSION/lib/cmake/opencv4
    fi
}

install_openpose() {
    set -e
    mkdir -p /opt
    if [ "$1" = "" ]; then
        echo "No openpose version supplied. Attempting to retreive it from the maven project." >&2
        OPENPOSE_VERSION="$(openpose_version_pom)"
        if [ "$OPENPOSE_VERSION" = "" ]; then
            echo "ERROR: Couldn't determin the OpenPose version from the project and you didn't supply it to install_opencv." >&2
            return 1
        fi
    else
        OPENPOSE_VERSION="$1"
    fi
    $SCRIPTDIR/install-from-zip.sh -d /opt --name openpose-$OPENPOSE_VERSION --artifact ai.kognition:openpose-build:$OPENPOSE_VERSION  --repo /tmp/maven-local-repo --leave-repo
    export OpenPose_DIR=/opt/openpose-$OPENPOSE_VERSION/lib
    export Caffe_DIR=/opt/openpose-$OPENPOSE_VERSION/share
}

install_spinnaker() {
    set -e

    if [ "$1" = "" ]; then
        echo "ERROR: usage: install_spinnaker spinnaker-version." >&2
        echo "       e.g.: install_spinnaker 2.0.0.109" >&2
        return 1
    fi

    $SCRIPTDIR/install-spinnaker.sh "$1"
}

install_eBus() {
    set -e

    if [ "$1" = "" ]; then
        echo "ERROR: usage: install_eBus eBus-version." >&2
        echo "       e.g.: install_eBus 6.1.4" >&2
        return 1
    fi

    $SCRIPTDIR/install-eBus.sh "$1"

    # we're running this now so the subeequent tests work
    . /opt/pleora/ebus_sdk/Ubuntu-x86_64/env.sh
}

source "$SCRIPTDIR"/fixup-hosts-file.sh

# =============================================================================

export THIS_PROJECT=$(git_project)

if [ "$THIS_PROJECT" = "" ]; then
    echo "ERROR: Couldn't figure out what project this is."
    exit 1
fi

