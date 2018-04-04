#!/bin/bash

set -ex

lein do clean, doo chrome-headless prod-test-npm once
test -f target/cljsbuild/prod-test-npm/main.js
node_modules/.bin/gzip-size target/cljsbuild/prod-test-npm/main.js
