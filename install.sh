#!/bin/bash

. ./os.sh
. ./env.sh

set -e

if [ "$WINDOWS" = "true" ]; then
    mkdir -p $MAVEN_REPO/com/jiminger
    pwd -P
    cp -r ./required/native/windows-x86_64-jiminger $MAVEN_REPO/com/jiminger
else
    cd native
    mvn clean install
    cd -
    mvn -Dfile=native/linux-amd64-jiminger/target/linux-amd64-jiminger.jar -DgroupId=com.jiminger -DartifactId=linux-amd64-jiminger-jar -Dversion=1.0-SNAPSHOT -Dpackaging=jar install:install-file
fi




