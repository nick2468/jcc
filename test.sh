#!/bin/sh
JCC_HOME="$(cd "$(dirname "$0")" && pwd)"
PASS=0; FAIL=0

# run_test <name> <expected-output>
run_test() {
    name="$1"; expected="$2"
    out=$(mktemp)
    if "$JCC_HOME/jcc.sh" "$JCC_HOME/examples/$name.c" -o "$out" >/dev/null 2>&1; then
        actual=$("$out")
        if [ "$actual" = "$expected" ]; then
            echo "PASS  $name"
            PASS=$((PASS+1))
        else
            printf 'FAIL  %s\n     expected: %s\n     got:      %s\n' "$name" "$expected" "$actual"
            FAIL=$((FAIL+1))
        fi
    else
        echo "FAIL  $name  [compile error]"
        FAIL=$((FAIL+1))
    fi
    rm -f "$out"
}

run_test HelloWorld "Hello, World!"

echo ""
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
