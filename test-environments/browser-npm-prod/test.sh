#!/bin/bash

set -ex

rm -rf target/cljsbuild/prod-test-npm/
lein doo chrome-headless prod-test-npm once
test -f target/cljsbuild/prod-test-npm/main.js
npx gzip-size-cli target/cljsbuild/prod-test-npm/main.js
