#!/bin/bash
set -ex
rm -rf target/cljsbuild/test-npm/
lein doo chrome-headless test-npm once
test -f target/cljsbuild/test-npm/out/node_modules/react/index.js
