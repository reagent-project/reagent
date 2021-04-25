#!/bin/bash
set -ex
rm -rf target/cljsbuild/prod-test/
lein doo chrome-headless prod-test once
test -f target/cljsbuild/prod-test/main.js

gzip -k target/cljsbuild/prod-test/main.js
ls -lh target/cljsbuild/prod-test/
