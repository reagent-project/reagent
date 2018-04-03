#!/bin/bash
set -ex
lein do clean, doo chrome-headless prod-test once
test -f target/cljsbuild/prod-test/main.js
../../node_modules/.bin/gzip-size target/cljsbuild/prod-test/main.js
