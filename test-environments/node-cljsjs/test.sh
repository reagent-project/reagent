#!/bin/bash
set -ex
lein do clean, doo node node-test once
test -f target/cljsbuild/node-test/out/cljsjs/react/development/react.inc.js
