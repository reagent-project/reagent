#!/bin/bash

echo "Failure expected: https://github.com/facebook/react/issues/12368"
echo

set -ex

lein do clean, doo chrome-headless prod-test-npm once || true
test -f target/cljsbuild/prod-test-npm/main.js
node_modules/.bin/gzip-size target/cljsbuild/prod-test-npm/main.js
