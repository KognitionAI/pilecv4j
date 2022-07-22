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
    echo "ERROR: usage: $BASH_SOURCE spinnaker-version." >&2
    echo "       e.g.: $BASH_SOURCE 2.0.0.109" >&2
    return 1
fi

# need to install some extra tools for the dpkg call
apt update
apt install -y lsb-release
apt install -y wget

cd /tmp
curl -s -X GET "$NEXUS_REPO_URL/repository/third-party-artifacts/spinnaker/v$1/spinnaker.tar.gz" -o spinnaker.tar.gz
if [ ! -f ./spinnaker.tar.gz -o "$(sgrep DOCTYPE ./spinnaker.tar.gz)" != "" ]; then
    echo "Seemed to have failed pulling down $NEXUS_REPO_URL/repository/third-party-artifacts/spinnaker/v$1/spinnaker.tar.gz" >$2
    return 1
fi
if [ -d /tmp/spinnaker ]; then
    rm -rf /tmp/spinnaker
fi
mkdir -p /tmp/spinnaker
cd -
cd /tmp/spinnaker
tar xf ../spinnaker.tar.gz

# accept the EULA the hacked way
echo debconf libspinnaker/accepted-flir-eula select true | debconf-set-selections
echo debconf libspinnaker/accepted-flir-eula seen true | debconf-set-selections

# remove the calls to sudo
cat install_spinnaker.sh | sed -e "s/sudo //g" > ./install_spinnaker_nosudo.sh 
chmod +x ./install_spinnaker_nosudo.sh 

cat <<'EOF' | ./install_spinnaker_nosudo.sh 
y
n
n
n
EOF
cd -
rm -rf /tmp/spinnaker
rm /tmp/spinnaker.tar.gz
