#!/bin/bash

set -ex

rm -rf target/cljsbuild/prod-test-npm/
lein doo chrome-headless prod-test-npm once
test -f target/cljsbuild/prod-test-npm/main.js
node_modules/.bin/gzip-size target/cljsbuild/prod-test-npm/main.js
