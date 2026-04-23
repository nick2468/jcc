#!/bin/sh
JCC_HOME="$(cd "$(dirname "$0")" && pwd)"
exec java -Djcc.home="$JCC_HOME" -cp "$JCC_HOME/out" com.jcc.Main "$@"
