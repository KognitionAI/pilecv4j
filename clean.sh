#!/bin/bash

set -e

usage() {
    echo "Usage: $0 [--nuke]"
    echo "  --nuke will delete gradle caches and the local maven repository."
    exit 1
}

cleanHere() {
    set -e
    if [ -d .git ]; then
        git add -A .
        STASH_WORKED=`git stash | sed -e "s/No local changes to save/FALSE/"`
        git clean -dxf
        if [ "$STASH_WORKED" != "FALSE" ]; then
            git stash pop
        fi
        git reset HEAD
    else
        echo "No .git. defaulting to find"
        find . -name ".classpath" -exec rm -rf {} \;
        find . -name ".project" -exec rm -rf {} \;
        find . -name ".settings" -exec rm -rf {} \;
        find . -name ".wtpmodules" -exec rm -rf {} \;
        find . -name ".cache*" -exec rm -rf {} \;
        mvn clean 2>/dev/null
    fi
}

NUKE=
if [ $# -ne 0 ]; then
    if [ "$1" = "--nuke" ]; then
        NUKE="TRUE"
    else
        usage
    fi
fi

MODS=`grep "    <module>" pom.xml  | sed -e "s/.*<module>//g" | sed -e "s/<.*$//g"`
for mod in $MODS; do
    echo "===================="
    echo "Cleaning $mod"
    echo "===================="
    cd $mod
    cleanHere
    cd - >/dev/null
done

if [ "$NUKE" = "TRUE" ]; then
    rm -rf ~/.m2/repository
    rm -rf ~/.gradle/caches
fi
