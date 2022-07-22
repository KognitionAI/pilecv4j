#!/bin/bash

# =============================================================================
# Preamble
# =============================================================================
set -e
MAIN_DIR="$(dirname "$BASH_SOURCE")"
cd "$MAIN_DIR"
SCRIPTDIR="$(pwd -P)"
# =============================================================================

usage() {
    echo "$BASH_SOURCE -d prefix-directory -n product-name -a group:arifact:version[:zip] [options]"
    echo ""
    echo "  -d directory to install the zip file: must be specified"
    echo "  -n: name of the product being installed. This will be appended"
    echo "     to the prefix-directory name to form the location where the "
    echo "     zip file will be unzipped."
    echo ""
    echo "  WARNING: if name is provided and the directory exists it will"
    echo "     be deleted before unzipping."
    echo ""
    echo "Options:"
    echo "  --repo repo-dir: specify the location for the local maven repo."
    echo "  --leave-repo: will prevent the deletion of the maven repository."
    echo "  --no-ldconfig: By default the script will run ldconfig using"
    echo "     'sudo' if script detemines the user isn't root. This option"
    echo "     will prevent the step from running."
    echo "  --sudo sudo-command: Will use this sudo command for ldconfig."
    echo "     this option is incompatible with --no-ldconfig. If --sudo"
    echo "     is specificed then it will be used regardless of the user."
    if [ $# -gt 0 ]; then
        exit $1
    else
        exit 1
    fi
}

echo "command: $BASH_SOURCE $*" >&2

INSTALL_PREFIX=
NO_LDCONFIG=
SUDO=
PROD_NAME=
FULL_ARTIFACT=
LEAVE_REPO=
LOCAL_MVN_REPO=
LIB_HINT=
while [ $# -gt 0 ]; do
    case "$1" in
        "-a"|"--artifact")
            FULL_ARTIFACT="$2"
            shift
            shift
            ;;
        "-n"|"--name")
            PROD_NAME="$2"
            shift
            shift
            ;;
        "-d"|"--directory")
            INSTALL_PREFIX="$2"
            shift
            shift
            ;;
        "--repo")
            LOCAL_MVN_REPO="$2"
            shift
            shift
            ;;
        "--no-ldconfig")
            NO_LDCONFIG="true"
            shift
            ;;
        "--leave-repo")
            LEAVE_REPO="true"
            shift
            ;;
        "--sudo")
            SUDO="$2"
            shift
            shift
            ;;
        "-help"|"--help"|"-h"|"-?")
            shift
            usage 0
            ;;
        *)
            echo "ERROR: Unknown option \"$1\""
            usage
            ;;
    esac
done

if [ "$INSTALL_PREFIX" = "" ]; then
    echo "ERROR: You must supply the directory with the install prefix."
    usage
fi

if [ "$FULL_ARTIFACT" = "" ]; then
    echo "ERROR: You must supply the full maven artifact descriptor."
    usage
fi

if [ "$PROD_NAME" = "" ]; then
    echo "ERROR: You must supply the product name."
    usage
fi

if [ "$NO_LDCONFIG" != "true" -a "$SUDO" = "" ]; then
    if [ $EUID -ne 0 ]; then
        SUDO=sudo
        echo "Warning: Will need to call 'sudo' to perform ldconfig so this may run interactively if sudo requires a password."
    fi
fi

UNZIP_LOCATION=
if [ "$PROD_NAME" != "" ]; then
    UNZIP_LOCATION="$INSTALL_PREFIX"/"$PROD_NAME"
    if [ -e "$UNZIP_LOCATION" ]; then
        rm -rf "$UNZIP_LOCATION"
    fi
fi

mkdir -p "$UNZIP_LOCATION"
# ==================================================================================

if [ "$LOCAL_MVN_REPO" = "" ]; then
    TMP_REPO="$INSTALL_PREFIX"/repository
else
    TMP_REPO="$LOCAL_MVN_REPO"
    mkdir -p $TMP_REPO
fi

if [ -d "$TMP_REPO" -a "$LEAVE_REPO" != "true" ]; then
    rm -rf "$TMP_REPO"
fi


GROUP="$(echo "$FULL_ARTIFACT" | awk '{split($0,a,":"); print a[1]}')"
ARTIFACT="$(echo "$FULL_ARTIFACT" | awk '{split($0,a,":"); print a[2]}')"
VERSION="$(echo "$FULL_ARTIFACT" | awk '{split($0,a,":"); print a[3]}')"
TYPE="$(echo "$FULL_ARTIFACT" | awk '{split($0,a,":"); print a[4]}')"

if [ "$GROUP" = "" -o "$ARTIFACT" = "" -o "$VERSION" = "" ]; then
    echo "ERROR: Malformed maven artifact descriptor."
    usage
fi

if [ "$TYPE" = "" ]; then
    FULL_ARTIFACT=$FULL_ARTIFACT:zip
    TYPE=zip
fi

mvn -B -Dmaven.repo.local="$TMP_REPO" org.apache.maven.plugins:maven-dependency-plugin:get -Dartifact=$FULL_ARTIFACT

REPO_LOCATION="$TMP_REPO"/"$(echo "$GROUP" | sed -e "s|\.|/|g")"/"$(echo "$ARTIFACT" | sed -e "s|\.|/|g")"/$VERSION/$ARTIFACT-$VERSION.$TYPE

echo "location: $REPO_LOCATION"

cd "$UNZIP_LOCATION"
unzip "$REPO_LOCATION"
if [ "$LEAVE_REPO" != "true" ]; then
    rm -rf "$TMP_REPO"
fi
cd -

if [ "$NO_LDCONFIG" != "true" ]; then
    # if there's a separate ldconfig subdirectory then that's the only one we care about.
    LIB_DIR=$(find "$UNZIP_LOCATION" -name ldconfig -type d)
    # otherwise we need to search for any libs
    if [ "$LIB_DIR" = "" ]; then
        # find the directory with the .so files
        LIB_DIR=$(find "$UNZIP_LOCATION" -name "*.so" | xargs -n1 dirname | sort | uniq)
    fi
    
    if [ "$LIB_DIR" = "" ]; then
        echo "ERROR: Couldn't find directories with .so files."
        exit 1
    fi

    for dir in $LIB_DIR; do
        if [ ! -d "$dir" ]; then
            echo "ERROR: There seems to be a problem with the lib directory detected at $dir"
            exit 1
        fi
    done
    
    cat <<'EOF' > /tmp/ldconfig-lib.sh
#!/bin/bash

CONF=/etc/ld.so.conf.d/"$1"
LIB_PATH="$2"

if [ -f "$CONF" ]; then
    rm "$CONF"
fi

echo "# $1 installed lib location" > "$CONF"
echo "$LIB_PATH" >> "$CONF"

ldconfig
EOF
    chmod +x /tmp/ldconfig-lib.sh
    $SUDO /tmp/ldconfig-lib.sh $PROD_NAME.conf "$LIB_DIR"
    rm /tmp/ldconfig-lib.sh
fi
