#!/bin/bash
set -ex
lein do clean, doo chrome-headless test once
test -f target/cljsbuild/test/out/cljsjs/react/development/react.inc.js
