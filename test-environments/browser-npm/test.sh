#!/bin/bash
set -ex
lein do clean, doo chrome-headless test-npm once
test -f target/cljsbuild/test-npm/out/node_modules/react/index.js
