#!/bin/bash

# =============================================================================
# Preamble
# =============================================================================
set -e
MAIN_DIR="$(dirname "$BASH_SOURCE")"
cd "$MAIN_DIR"
SCRIPTDIR="$(pwd -P)"
# =============================================================================

source "$SCRIPTDIR"/env.sh

if [ "$1" = "" ]; then
    echo "ERROR: usage: $BASH_SOURCE eBus-version." >&2
    echo "       e.g.: $BASH_SOURCE 6.1.4" >&2
    return 1
fi

# need to install some extra tools for the dpkg call
cd /tmp
curl -s -X GET "$NEXUS_REPO_URL/repository/third-party-artifacts/eBus/v$1/eBus.deb" -o eBus.deb
if [ ! -f ./eBus.deb -o "$(sgrep DOCTYPE ./eBus.deb)" != "" ]; then
    echo "Seemed to have failed pulling down $NEXUS_REPO_URL/repository/third-party-artifacts/eBus/v$1/eBus.deb" >$2
    return 1
fi

dpkg -i ./eBus.deb

rm eBus.deb

# needs to match the path in preamble.sh:install_eBus()
ENV_FILE=/opt/pleora/ebus_sdk/Ubuntu-x86_64/env.sh

echo "export GENICAM_LOG_CONFIG_V3_1=/opt/pleora/ebus_sdk/Ubuntu-x86_64/lib/genicam/log/config/DefaultLogging.properties" > "$ENV_FILE"
echo "export GENICAM_LOG_CONFIG=/opt/pleora/ebus_sdk/Ubuntu-x86_64/lib/genicam/log/config/DefaultLogging.properties" >> "$ENV_FILE"
echo "export GENICAM_ROOT=/opt/pleora/ebus_sdk/Ubuntu-x86_64/lib/genicam" >> "$ENV_FILE"
echo "export GENICAM_ROOT_V3_1=/opt/pleora/ebus_sdk/Ubuntu-x86_64/lib/genicam" >> "$ENV_FILE"
echo "mkdir -p /tmp/genicam_cache_v3_1"
echo "chmod a+x,a+w /tmp/genicam_cache_v3_1"
echo "export GENICAM_CACHE=/tmp/genicam_cache_v3_1" >> "$ENV_FILE"
echo "export GENICAM_CACHE_V3_1=/tmp/genicam_cache_v3_1" >> "$ENV_FILE"

echo "#################################" >> ~/.bashrc
echo "# inserted from install-eBus.sh"   >> ~/.bashrc
echo "#################################" >> ~/.bashrc
echo ". $ENV_FILE" >> ~/.bashrc
echo "#################################" >> ~/.bashrc

mkdir -p /tmp/genicam_cache_v3_1
chmod a+x,a+w /tmp/genicam_cache_v3_1
. "$ENV_FILE"
