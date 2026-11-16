#!/bin/bash
# Run clj-kondo on all Clojure sources.
#
# Each example is linted separately: several of them use the same namespace
# name (example.core), and linting them in one run reports false
# "Unresolved var" warnings.

set -uo pipefail

cd "$(dirname "$0")"

status=0

clj-kondo --lint src test demo prerender bb || status=1

for dir in examples/*/src; do
    clj-kondo --lint "$dir" || status=1
done

exit $status
