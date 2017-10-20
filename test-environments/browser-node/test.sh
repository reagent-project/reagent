#!/bin/bash
set -ex
lein do clean, doo chrome-headless test once
test -f target/cljsbuild/test/out/node_modules/react/react.js
