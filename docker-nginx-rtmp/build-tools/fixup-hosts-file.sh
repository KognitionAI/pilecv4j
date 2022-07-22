# meant to be sourced from preamble plus a few start scripts

sgrep() {
    set +e
    grep "$@"
    set -e
}

fixup_hosts_file() {
    if [ "$HOSTNAME" = "" ]; then
        echo "WARNING: HOSTNAME not set."
    else
        if [ "$(sgrep "$HOSTNAME" /etc/hosts)" = "" ]; then
            echo "" >> /etc/hosts
            echo "127.0.1.1 $HOSTNAME" >> /etc/hosts
        fi
    fi
}    

