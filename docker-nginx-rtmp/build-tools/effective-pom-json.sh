#!/bin/bash

set -e

tmpfile="$(mktemp /tmp/tmp-effective-pom.XXXXXXXXXXXXXXXXXXXXX.txt)"
mvn -B -U -Doutput="$tmpfile" help:effective-pom 2>/dev/null >&2
# This creates a consistent result. All output will be [ { project details }, ... ]
# There will be no projects.project fields. Just an array of project entries without
# the "project" name.
CONTENTS="$(cat "$tmpfile" | xq 'if (.projects != null) then .projects.project else [.project] end')"
rm "$tmpfile"
echo "$CONTENTS"
