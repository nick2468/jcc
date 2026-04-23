#!/bin/sh
JCC_HOME="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$JCC_HOME/out"
javac -source 8 -target 8 \
    -sourcepath "$JCC_HOME/src" \
    -d "$JCC_HOME/out" \
    "$JCC_HOME/src/com/jcc/Main.java"
if [ $? -eq 0 ]; then
    echo "Build successful."
else
    echo "Build FAILED."
    exit 1
fi
