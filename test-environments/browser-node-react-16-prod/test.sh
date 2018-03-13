#!/bin/bash
set -ex
lein with-profile dev,node-react-16-prod do clean, doo chrome-headless prod-test once
test -f target/cljsbuild/prod-test/main.js
../../node_modules/.bin/gzip-size target/cljsbuild/prod-test/main.js
