#!/bin/bash
set -ex
rm -rf target/cljsbuild/test/
COVERAGE=1 lein doo chrome-headless test once
test -f target/cljsbuild/test/out/cljsjs/react/development/react.inc.js
