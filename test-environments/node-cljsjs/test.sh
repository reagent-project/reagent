#!/bin/bash
set -ex
rm -rf target/cljsbuild/node-test/
lein with-profile +cljsjs doo node node-test once
test -f target/cljsbuild/node-test/out/cljsjs/react/development/react.inc.js
