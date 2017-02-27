# ---------------------------------------------
# Find which OS and set the WINDOWS predicate
# ---------------------------------------------
WINDOWS=
OS=`uname`
if [ "$OS" = "Linux" ]; then
    WINDOWS=
    cpath() {
        echo "$*"
    }
    CSEP=":"
elif [ "`echo "$OS" | grep MINGW`" != "" ]; then
    WINDOWS=true
    cpath() {
        if [ "$1" != "" ]; then
            cygpath -w "$1"
        fi
    }
    CSEP=";"
else
    echo "Cannot determin which OS"
    exit 1
fi
# ---------------------------------------------
