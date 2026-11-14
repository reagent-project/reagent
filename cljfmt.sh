#!/bin/bash
# Run cljfmt on all tracked Clojure files.
# examples/*/ contain ignored build caches, so don't let cljfmt scan directories.
#
# Usage: ./cljfmt.sh check|fix

set -euo pipefail

cd "$(dirname "$0")"

if [[ $# -ne 1 || ( $1 != check && $1 != fix ) ]]; then
    echo "Usage: $0 check|fix" >&2
    exit 1
fi

git ls-files -z '*.clj' '*.cljs' '*.cljc' | xargs -0 cljfmt "$1"
